package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project2.ism.DTO.BillAvenueCredentials;
import com.project2.ism.DTO.BillPaymentRequest;
import com.project2.ism.DTO.TransactionStatusRequest;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Helper.BillAvenueIVGenerator;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.Bbps.BillAvenueConfig;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.MerchantWallet;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.BbpsTransactionRepository;
import com.project2.ism.Repository.BillAvenueConfigRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.Repository.MerchantWalletRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.response.CommonResponse;
import com.project2.ism.util.BillAvenueApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Handles real money movement for BBPS bill payments — mirrors PayoutService's
 * role for the Payout vendor flow: validate & lock the merchant wallet,
 * deduct funds up front, call the vendor, record ledger + transaction state,
 * and refund on failure.
 *
 * Like PayoutService#initiatePayout, this method never lets an exception
 * escape once the wallet has been debited — everything after the debit is
 * caught and turned into a normal (failed) response, so the debit, the
 * refund, and the FAILED transaction/ledger records all commit together
 * instead of being rolled back by the ambient @Transactional context.
 *
 * @author SHUBHAM KHOPADE
 */
@Service
public class BbpsPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BbpsPaymentService.class);
    private static final String BBPS_PAYMENT_MODE_CODE = "BBPS";

    private final PaymentVendorRepository paymentVendorRepository;
    private final BillAvenueConfigRepository billAvenueConfigRepository;
    private final BillAvenueApiClient billAvenueApiClient;
    private final PaymentVendorCredentialsService credentialsService;
    private final PaymentChargeService paymentChargeService;
    private final MerchantWalletRepository merchantWalletRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantTransDetRepository merchantTransDetRepository;
    private final BbpsTransactionRepository bbpsTransactionRepository;
    private final ObjectMapper objectMapper;

    public BbpsPaymentService(PaymentVendorRepository paymentVendorRepository,
                               BillAvenueConfigRepository billAvenueConfigRepository,
                               BillAvenueApiClient billAvenueApiClient,
                               PaymentVendorCredentialsService credentialsService,
                               PaymentChargeService paymentChargeService,
                               MerchantWalletRepository merchantWalletRepository,
                               MerchantRepository merchantRepository,
                               MerchantTransDetRepository merchantTransDetRepository,
                               BbpsTransactionRepository bbpsTransactionRepository,
                               ObjectMapper objectMapper) {
        this.paymentVendorRepository = paymentVendorRepository;
        this.billAvenueConfigRepository = billAvenueConfigRepository;
        this.billAvenueApiClient = billAvenueApiClient;
        this.credentialsService = credentialsService;
        this.paymentChargeService = paymentChargeService;
        this.merchantWalletRepository = merchantWalletRepository;
        this.merchantRepository = merchantRepository;
        this.merchantTransDetRepository = merchantTransDetRepository;
        this.bbpsTransactionRepository = bbpsTransactionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CommonResponse<Object> doBillPayment(BillPaymentRequest request) throws Exception {

        validateBillPaymentRequest(request);

        Long merchantId = request.getMerchantId();
        BigDecimal billAmount = request.getAmountInfo().getAmount();
        BigDecimal custConvFee = request.getAmountInfo().getCustConvFee() != null
                ? new BigDecimal(request.getAmountInfo().getCustConvFee())
                : BigDecimal.ZERO;

        log.info("Starting BBPS bill payment | merchantId={} billerId={} amount={}",
                merchantId, request.getBillerId(), billAmount);

        // 1. Resolve active Bill Avenue vendor
        PaymentVendor vendor = paymentVendorRepository
                .findByVendorNameAndStatus("Bill Avenue", true)
                .orElseThrow(() -> new RuntimeException("Active Bill Avenue vendor not found"));

        // 2. Look up cached biller config for our own bookkeeping (non-fatal if missing)
        BillAvenueConfig billerConfig = billAvenueConfigRepository
                .findByProviderId(request.getBillerId())
                .orElse(null);

        // 3. Charges (BBPS commission) — optional; default to zero if not yet configured
        BigDecimal charges = calculateChargesSafely(billAmount, merchantId);

        BigDecimal totalDeduction = billAmount.add(custConvFee).add(charges);

        // 4. Lock & validate merchant wallet, deduct funds up-front.
        // Deliberately NOT inside the try/catch below: if this throws, no
        // money has moved yet, so letting it roll back the transaction and
        // propagate to the controller is correct.
        BigDecimal balanceAfterDebit = validateAndDeductMerchantBalance(merchantId, totalDeduction);

        String requestId = (request.getRequestId() != null && !request.getRequestId().isBlank())
                ? request.getRequestId()
                : BillAvenueIVGenerator.generate();

        String consumerNumber = extractConsumerNumber(request);

        // 5. Create the BBPS transaction record (PENDING)
        BbpsTransaction bbpsTxn = new BbpsTransaction();
        bbpsTxn.setMerchantId(merchantId);
        bbpsTxn.setVendorId(vendor.getId());
        bbpsTxn.setRequestId(requestId);
        bbpsTxn.setBillerId(request.getBillerId());
        bbpsTxn.setBillerName(billerConfig != null ? billerConfig.getProviderName() : null);
        bbpsTxn.setCategory(billerConfig != null ? billerConfig.getServiceName() : null);
        bbpsTxn.setConsumerNumber(consumerNumber);
        bbpsTxn.setCustomerMobile(request.getCustomerInfo() != null ? request.getCustomerInfo().getCustomerMobile() : null);
        bbpsTxn.setAmount(billAmount);
        bbpsTxn.setCustConvFee(custConvFee);
        bbpsTxn.setCharges(charges);
        bbpsTxn.setTotalDeducted(totalDeduction);
        bbpsTxn.setPaymentMode(request.getPaymentMethod() != null ? request.getPaymentMethod().getPaymentMode() : null);
        bbpsTxn.setStatus(BbpsTransaction.BbpsStatus.PENDING);
        bbpsTxn = bbpsTransactionRepository.save(bbpsTxn);

        // 6. Ledger DEBIT entry (PENDING)
        MerchantTransactionDetails ledgerEntry = recordDebitLedger(merchantId, totalDeduction, balanceAfterDebit, bbpsTxn);
        bbpsTxn.setLedgerMerchantTxnId(ledgerEntry.getTransactionId());
        bbpsTransactionRepository.save(bbpsTxn);

        // 7. Call the vendor — everything from here on is caught, never
        // rethrown, so the debit/refund/status writes always commit.
        try {
            JsonNode vendorResponse = callBillAvenuePayment(request, vendor.getId(), requestId);

            String responseCode = vendorResponse.path("responseCode").asText("");
            boolean success = "000".equals(responseCode);

            bbpsTxn.setResponseCode(responseCode);
            bbpsTxn.setTxnRefId(vendorResponse.path("txnRefId").asText(null));
            bbpsTxn.setApprovalRefNumber(vendorResponse.path("approvalRefNumber").asText(null));
            bbpsTxn.setCompletedAt(LocalDateTime.now());

            if (success) {
                bbpsTxn.setStatus(BbpsTransaction.BbpsStatus.SUCCESS);
                bbpsTxn.setResponseMessage(vendorResponse.path("responseReason").asText("Successful"));
                bbpsTransactionRepository.save(bbpsTxn);

                updateLedgerStatus(ledgerEntry, "SUCCESS", null, bbpsTxn.getTxnRefId());

                log.info("BBPS bill payment SUCCESS | merchantId={} requestId={} txnRefId={}",
                        merchantId, requestId, bbpsTxn.getTxnRefId());

                return CommonResponse.success(vendorResponse, "Transaction Successful");
            }

            String errorMessage = extractErrorMessage(vendorResponse);
            bbpsTxn.setStatus(BbpsTransaction.BbpsStatus.FAILED);
            bbpsTxn.setResponseMessage(errorMessage);
            bbpsTransactionRepository.save(bbpsTxn);

            updateLedgerStatus(ledgerEntry, "FAILED", errorMessage, bbpsTxn.getTxnRefId());
            refundMerchantWallet(merchantId, totalDeduction, "BBPS payment failed - " + errorMessage);

            log.warn("BBPS bill payment FAILED | merchantId={} requestId={} responseCode={} message={}",
                    merchantId, requestId, responseCode, errorMessage);

            return CommonResponse.failedResponse(400, "Transaction Failed", vendorResponse);

        } catch (Exception ex) {
            log.error("BBPS bill payment exception | merchantId={} requestId={} error={}",
                    merchantId, requestId, ex.getMessage(), ex);

            bbpsTxn.setStatus(BbpsTransaction.BbpsStatus.FAILED);
            bbpsTxn.setResponseMessage(ex.getMessage());
            bbpsTxn.setCompletedAt(LocalDateTime.now());
            bbpsTransactionRepository.save(bbpsTxn);

            updateLedgerStatus(ledgerEntry, "FAILED", ex.getMessage(), null);
            refundMerchantWallet(merchantId, totalDeduction, "BBPS payment error - " + ex.getMessage());

            return CommonResponse.failedResponse(500, "Transaction failed: " + ex.getMessage(), null);
        }
    }

    @Transactional(readOnly = true)
    public Object fetchTransactionStatus(TransactionStatusRequest request) throws Exception {

        if (request == null || request.getTrackingType() == null || request.getTrackingType().isBlank()
                || request.getTrackingValue() == null || request.getTrackingValue().isBlank()) {
            throw new IllegalArgumentException("trackingType and trackingValue are required");
        }

        PaymentVendor vendor = paymentVendorRepository
                .findByVendorNameAndStatus("Bill Avenue", true)
                .orElseThrow(() -> new RuntimeException("Active Bill Avenue vendor not found"));

        String requestId = (request.getRequestId() != null && !request.getRequestId().isBlank())
                ? request.getRequestId()
                : BillAvenueIVGenerator.generate();

        String requestJson = objectMapper.writeValueAsString(request);

        log.info("Fetching BBPS transaction status | vendorId={} requestId={} trackingType={} trackingValue={}",
                vendor.getId(), requestId, request.getTrackingType(), request.getTrackingValue());

        String decryptedResponse = billAvenueApiClient.call(
                vendor.getId(),
                BillAvenueApiType.TRANSACTION_STATUS,
                requestId,
                requestJson
        );

        return objectMapper.readTree(decryptedResponse);
    }

    // ==================== VALIDATION & HELPERS ====================

    private void validateBillPaymentRequest(BillPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.getMerchantId() == null) {
            throw new IllegalArgumentException("merchantId is required");
        }
        if (request.getBillerId() == null || request.getBillerId().isBlank()) {
            throw new IllegalArgumentException("billerId is required");
        }
        if (request.getAmountInfo() == null || request.getAmountInfo().getAmount() == null
                || request.getAmountInfo().getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("A valid amountInfo.amount is required");
        }
        if (request.getPaymentMethod() == null || request.getPaymentMethod().getPaymentMode() == null
                || request.getPaymentMethod().getPaymentMode().isBlank()) {
            throw new IllegalArgumentException("paymentMethod.paymentMode is required");
        }
    }

    private String extractConsumerNumber(BillPaymentRequest request) {
        if (request.getInputParams() != null
                && request.getInputParams().getInput() != null
                && !request.getInputParams().getInput().isEmpty()) {
            return request.getInputParams().getInput().get(0).getParamValue();
        }
        return null;
    }

    private String extractErrorMessage(JsonNode vendorResponse) {
        JsonNode errors = vendorResponse.path("errorInfo").path("error");
        if (errors.isArray() && errors.size() > 0) {
            JsonNode first = errors.get(0);
            return "[" + first.path("errorCode").asText("") + "] " + first.path("errorMessage").asText("");
        }
        return vendorResponse.path("responseReason").asText(
                "Biller error (code " + vendorResponse.path("responseCode").asText() + ")");
    }

    /**
     * Fills the server-side fields (agentId, agentDeviceInfo, blank customer
     * fields) then encrypts + calls Bill Avenue — same pattern as
     * BillPayConfigService#fetchBill.
     */
    private JsonNode callBillAvenuePayment(BillPaymentRequest request, Long vendorId, String requestId) throws Exception {

        BillAvenueCredentials credentials = credentialsService.getBillAvenueCredentials(vendorId);

        request.setAgentId(credentials.secretKey());

        BillPaymentRequest.AgentDeviceInfo deviceInfo = request.getAgentDeviceInfo();
        if (deviceInfo == null) {
            deviceInfo = new BillPaymentRequest.AgentDeviceInfo();
        }
        deviceInfo.setIp("192.168.2.73");
        deviceInfo.setInitChannel("INT");
        deviceInfo.setMac("01-23-45-67-89-ab");
        request.setAgentDeviceInfo(deviceInfo);

        BillPaymentRequest.CustomerInfo customerInfo = request.getCustomerInfo();
        if (customerInfo == null) {
            customerInfo = new BillPaymentRequest.CustomerInfo();
        }
        customerInfo.setCustomerEmail(customerInfo.getCustomerEmail() != null ? customerInfo.getCustomerEmail() : "");
        customerInfo.setCustomerAdhaar(customerInfo.getCustomerAdhaar() != null ? customerInfo.getCustomerAdhaar() : "");
        customerInfo.setCustomerPan(customerInfo.getCustomerPan() != null ? customerInfo.getCustomerPan() : "");
        request.setCustomerInfo(customerInfo);

        if (request.getBillerAdhoc() == null) {
            request.setBillerAdhoc("false");
        }

        // Bill Avenue requires amounts in paise ("Any amount mentioned in the
        // API request/response are in paise" — spec section 3.0). Everywhere
        // else in this codebase (wallet, ledger, BbpsTransaction) works in
        // rupees, so the rupee->paise conversion happens only here, at the
        // vendor-JSON boundary — the DTO instance itself is left untouched.
        ObjectNode vendorRequestNode = (ObjectNode) objectMapper.valueToTree(request);
        ObjectNode amountInfoNode = (ObjectNode) vendorRequestNode.get("amountInfo");
        amountInfoNode.put("amount", toPaise(request.getAmountInfo().getAmount()));
        amountInfoNode.put("custConvFee", toPaise(parseRupeeString(request.getAmountInfo().getCustConvFee())));

        String requestJson = objectMapper.writeValueAsString(vendorRequestNode);

        String decryptedResponse = billAvenueApiClient.call(
                vendorId,
                BillAvenueApiType.BILL_PAYMENT,
                requestId,
                requestJson
        );

        return objectMapper.readTree(decryptedResponse);
    }

    /** Rupees -> whole-paise string, e.g. 923.00 -> "92300". */
    private String toPaise(BigDecimal rupees) {
        if (rupees == null) {
            rupees = BigDecimal.ZERO;
        }
        return rupees.multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    private BigDecimal parseRupeeString(String rupeeString) {
        if (rupeeString == null || rupeeString.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(rupeeString);
    }

    /**
     * Best-effort BBPS commission lookup, reusing the same charge engine the
     * Payout flow uses (PaymentMode "BBPS"). No admin-configured charge slab
     * for BBPS yet means zero commission rather than a hard failure — this is
     * a brand-new payment mode and shouldn't block payments while charges are
     * still being set up.
     */
    private BigDecimal calculateChargesSafely(BigDecimal amount, Long merchantId) {
        try {
            return paymentChargeService.calculateCharges(amount, BBPS_PAYMENT_MODE_CODE, merchantId);
        } catch (Exception ex) {
            log.warn("No BBPS charge configuration found (merchantId={}) — defaulting to zero commission: {}",
                    merchantId, ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    // ==================== WALLET & LEDGER ====================

    private BigDecimal validateAndDeductMerchantBalance(Long merchantId, BigDecimal totalDeduction) {
        MerchantWallet wallet = merchantWalletRepository.findByMerchantIdForUpdate(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant wallet not found: " + merchantId));

        BigDecimal available = nvl(wallet.getAvailableBalance());
        if (available.compareTo(totalDeduction) < 0) {
            throw new IllegalStateException("Insufficient balance. Available: " + available +
                    ", Required: " + totalDeduction);
        }

        BigDecimal newBalance = available.subtract(totalDeduction);
        wallet.setAvailableBalance(newBalance);
        wallet.setLastUpdatedAmount(totalDeduction.negate());
        wallet.setLastUpdatedAt(LocalDateTime.now());
        merchantWalletRepository.save(wallet);

        log.debug("Merchant {} wallet deducted for BBPS: {} -> {}", merchantId, available, newBalance);
        return newBalance;
    }

    private MerchantTransactionDetails recordDebitLedger(Long merchantId, BigDecimal totalDeduction,
                                                           BigDecimal balanceAfterDebit, BbpsTransaction bbpsTxn) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        MerchantTransactionDetails mtd = new MerchantTransactionDetails();
        mtd.setMerchant(merchant);
        mtd.setActionOnBalance("DEBIT");
        mtd.setAmount(totalDeduction.negate());
        mtd.setCharge(bbpsTxn.getCharges());
        mtd.setNetAmount(bbpsTxn.getAmount().negate());
        mtd.setBalBeforeTran(balanceAfterDebit.add(totalDeduction));
        mtd.setBalAfterTran(balanceAfterDebit);
        mtd.setFinalBalance(balanceAfterDebit);
        mtd.setTransactionDate(LocalDateTime.now());
        mtd.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        mtd.setTranStatus("PENDING");
        mtd.setTransactionType("DEBIT");
        mtd.setService("BBPS");
        mtd.setRemarks("BBPS bill payment - " + bbpsTxn.getBillerId() +
                (bbpsTxn.getConsumerNumber() != null ? " - " + bbpsTxn.getConsumerNumber() : ""));

        return merchantTransDetRepository.save(mtd);
    }

    private void updateLedgerStatus(MerchantTransactionDetails ledgerEntry, String status,
                                     String failureRemarks, String vendorTxnId) {
        merchantTransDetRepository.findById(ledgerEntry.getTransactionId()).ifPresent(mtd -> {
            mtd.setTranStatus(status);
            if (vendorTxnId != null) {
                mtd.setVendorTransactionId(vendorTxnId);
            }
            if (failureRemarks != null) {
                mtd.setFailureRemarks(failureRemarks);
            }
            merchantTransDetRepository.save(mtd);
        });
    }

    private void refundMerchantWallet(Long merchantId, BigDecimal refundAmount, String remark) {
        MerchantWallet wallet = merchantWalletRepository.findByMerchantIdForUpdate(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant wallet not found: " + merchantId));

        BigDecimal newBalance = nvl(wallet.getAvailableBalance()).add(refundAmount);
        wallet.setAvailableBalance(newBalance);
        wallet.setLastUpdatedAmount(refundAmount);
        wallet.setLastUpdatedAt(LocalDateTime.now());
        merchantWalletRepository.save(wallet);

        Merchant merchant = merchantRepository.findById(merchantId).orElseThrow();
        MerchantTransactionDetails refundEntry = new MerchantTransactionDetails();
        refundEntry.setMerchant(merchant);
        refundEntry.setActionOnBalance("CREDIT");
        refundEntry.setAmount(refundAmount);
        refundEntry.setBalBeforeTran(newBalance.subtract(refundAmount));
        refundEntry.setBalAfterTran(newBalance);
        refundEntry.setFinalBalance(newBalance);
        refundEntry.setTransactionDate(LocalDateTime.now());
        refundEntry.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        refundEntry.setTranStatus("SUCCESS");
        refundEntry.setTransactionType("CREDIT");
        refundEntry.setService("BBPS_REFUND");
        refundEntry.setRemarks(remark);

        merchantTransDetRepository.save(refundEntry);

        log.info("Refunded merchant {} wallet for failed BBPS payment: amount={}", merchantId, refundAmount);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
