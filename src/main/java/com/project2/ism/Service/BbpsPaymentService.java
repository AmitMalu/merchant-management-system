package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project2.ism.DTO.BillAvenueCredentials;
import com.project2.ism.DTO.BillPaymentRequest;
import com.project2.ism.DTO.TransactionStatusRequest;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
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
    private final com.project2.ism.Service.Monitoring.TransactionEventService transactionEventService;
    private final com.project2.ism.util.BillAvenueAgentDevice agentDevice;

    // When true (default) a bill payment is refused if no BBPS service charge is configured, so the
    // platform never pays a bill for free by mistake. Switch off only for a deliberate zero-charge launch.
    @org.springframework.beans.factory.annotation.Value("${bbps.charge-required:true}")
    private boolean chargeRequired = true;

    // How the CUSTOMER pays the agent, as Bill Avenue wants it. It is not our wallet: the wallet only
    // funds the payment. For the AGT (agent) channel Bill Avenue accepts only "Cash"
    // (spec v2.8.7, payment mode / channel table). Whatever the website sends is replaced by this.
    @org.springframework.beans.factory.annotation.Value("${bill.avenue.agent.payment-mode:Cash}")
    private String vendorPaymentMode = "Cash";

    // Each bill category has its own service charge: a payment mode with the code BBPS_<CATEGORY>
    // (BBPS_CREDIT_CARD, BBPS_ELECTRICITY, ...) with a charge slab. A category without its own mode is
    // REFUSED, so it can never be charged with another category's slab by accident.
    // Only when bbps.charge-default-fallback=true does such a category (or an unknown biller) use the
    // default mode below instead.
    @org.springframework.beans.factory.annotation.Value("${bbps.charge-default-fallback:false}")
    private boolean chargeDefaultFallback = false;

    // The fallback mode, used only when bbps.charge-default-fallback=true.
    @org.springframework.beans.factory.annotation.Value("${bbps.charge-mode-code:BBPS_CREDIT_CARD}")
    private String chargeModeCode = "BBPS_CREDIT_CARD";

    private static final String CATEGORY_MODE_PREFIX = "BBPS_";

    public BbpsPaymentService(PaymentVendorRepository paymentVendorRepository,
                               BillAvenueConfigRepository billAvenueConfigRepository,
                               BillAvenueApiClient billAvenueApiClient,
                               PaymentVendorCredentialsService credentialsService,
                               PaymentChargeService paymentChargeService,
                               MerchantWalletRepository merchantWalletRepository,
                               MerchantRepository merchantRepository,
                               MerchantTransDetRepository merchantTransDetRepository,
                               BbpsTransactionRepository bbpsTransactionRepository,
                               ObjectMapper objectMapper,
                               com.project2.ism.Service.Monitoring.TransactionEventService transactionEventService,
                               com.project2.ism.util.BillAvenueAgentDevice agentDevice) {
        this.agentDevice = agentDevice;
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
        this.transactionEventService = transactionEventService;
    }

    /**
     * What a bill payment would take from the merchant's wallet, before it is made:
     * bill amount + service charge (+ customer fee). Same charge rule as the payment itself, so the
     * number shown is the number debited. Nothing is changed.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> quote(Long merchantId, BigDecimal billAmount, String custConvFeeText) {
        return quote(merchantId, billAmount, custConvFeeText, null);
    }

    /** Same, for a biller: the biller's category decides which service charge applies. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> quote(Long merchantId, BigDecimal billAmount, String custConvFeeText,
                                               String billerId) {
        if (merchantId == null) {
            throw new IllegalArgumentException("Merchant is required");
        }
        if (billAmount == null || billAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Enter a valid amount");
        }
        BigDecimal custConvFee = parseCustConvFee(custConvFeeText);
        String category = categoryOfBiller(billerId);
        BigDecimal charges = calculateCharges(billAmount, merchantId, category);

        java.util.Map<String, Object> quote = new java.util.LinkedHashMap<>();
        quote.put("amount", billAmount);
        quote.put("custConvFee", custConvFee);
        quote.put("charges", charges);
        quote.put("totalDebit", billAmount.add(custConvFee).add(charges));
        quote.put("paymentMode", vendorPaymentMode);
        quote.put("chargeMode", chargeModeFor(category));
        return quote;
    }

    @Transactional
    public CommonResponse<Object> doBillPayment(BillPaymentRequest request) throws Exception {

        validateBillPaymentRequest(request);
        request.getPaymentMethod().setPaymentMode(vendorPaymentMode);

        Long merchantId = request.getMerchantId();
        BigDecimal billAmount = request.getAmountInfo().getAmount();
        requireRemitterName(request);
        requirePanForLargeCashPayment(request, billAmount);
        BigDecimal custConvFee = parseCustConvFee(request.getAmountInfo().getCustConvFee());

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

        // The payment must use the SAME request id as the bill fetch (Bill Avenue links them by it:
        // "No fetch data found for given ref id" otherwise). One payment per fetch: a request id that
        // was already used is refused here, before any money moves, which also stops a double click
        // or a replay from paying the same fetched bill twice.
        String requestId = (request.getRequestId() != null && !request.getRequestId().isBlank())
                ? request.getRequestId().trim()
                : BillAvenueIVGenerator.generate();
        if (bbpsTransactionRepository.findByRequestId(requestId).isPresent()) {
            throw new IllegalArgumentException(
                    "This bill was already submitted. Please fetch the bill again to pay it.");
        }

        // 3. Charges (BBPS commission). Worked out before any money moves, so a missing
        // configuration refuses the payment cleanly.
        BigDecimal charges = calculateCharges(billAmount, merchantId,
                billerConfig != null ? billerConfig.getServiceName() : null);

        BigDecimal totalDeduction = billAmount.add(custConvFee).add(charges);

        // 4. Lock & validate merchant wallet, deduct funds up-front.
        // Deliberately NOT inside the try/catch below: if this throws, no
        // money has moved yet, so letting it roll back the transaction and
        // propagate to the controller is correct.
        BigDecimal balanceAfterDebit = validateAndDeductMerchantBalance(merchantId, totalDeduction);

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

        // Transaction Monitoring: record initiation
        transactionEventService.recordEvent(TransactionSourceType.BBPS, bbpsTxn.getId(),
                "MERCHANT", merchantId, totalDeduction, TransactionEventStatus.PENDING,
                "billerId=" + request.getBillerId());

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

                transactionEventService.recordEvent(TransactionSourceType.BBPS, bbpsTxn.getId(),
                        "MERCHANT", merchantId, totalDeduction, TransactionEventStatus.SUCCESS,
                        "txnRefId=" + bbpsTxn.getTxnRefId());

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

            transactionEventService.recordEvent(TransactionSourceType.BBPS, bbpsTxn.getId(),
                    "MERCHANT", merchantId, totalDeduction, TransactionEventStatus.FAILED, errorMessage);
            transactionEventService.recordEvent(TransactionSourceType.BBPS_REFUND, bbpsTxn.getId(),
                    "MERCHANT", merchantId, totalDeduction, TransactionEventStatus.SUCCESS,
                    "refund for failed BBPS payment " + requestId);

            log.warn("BBPS bill payment FAILED | merchantId={} requestId={} responseCode={} message={}",
                    merchantId, requestId, responseCode, errorMessage);

            return CommonResponse.failedResponse(400, "Transaction Failed: " + errorMessage, vendorResponse);

        } catch (Exception ex) {
            log.error("BBPS bill payment exception | merchantId={} requestId={} error={}",
                    merchantId, requestId, ex.getMessage(), ex);

            bbpsTxn.setStatus(BbpsTransaction.BbpsStatus.FAILED);
            bbpsTxn.setResponseMessage(ex.getMessage());
            bbpsTxn.setCompletedAt(LocalDateTime.now());
            bbpsTransactionRepository.save(bbpsTxn);

            updateLedgerStatus(ledgerEntry, "FAILED", ex.getMessage(), null);
            refundMerchantWallet(merchantId, totalDeduction, "BBPS payment error - " + ex.getMessage());

            transactionEventService.recordEvent(TransactionSourceType.BBPS, bbpsTxn.getId(),
                    "MERCHANT", merchantId, totalDeduction, TransactionEventStatus.FAILED, ex.getMessage());
            transactionEventService.recordEvent(TransactionSourceType.BBPS_REFUND, bbpsTxn.getId(),
                    "MERCHANT", merchantId, totalDeduction, TransactionEventStatus.SUCCESS,
                    "refund for failed BBPS payment " + requestId);

            return CommonResponse.failedResponse(500, "Transaction failed: " + ex.getMessage(), null);
        }
    }

    /**
     * Status of one of the logged-in merchant's OWN bill payments, looked up by the Bill Avenue
     * transaction id (TRANS_REF_ID). Searching by mobile number or for someone else's transaction is
     * not allowed: the id must belong to a payment this merchant made.
     */
    @Transactional(readOnly = true)
    public Object fetchTransactionStatus(TransactionStatusRequest request, Long merchantId) throws Exception {

        if (request == null || request.getTrackingValue() == null || request.getTrackingValue().isBlank()) {
            throw new IllegalArgumentException("Enter the transaction ID");
        }
        if (request.getTrackingType() != null && !request.getTrackingType().isBlank()
                && !"TRANS_REF_ID".equalsIgnoreCase(request.getTrackingType())) {
            throw new IllegalArgumentException("Only search by transaction ID is supported");
        }
        if (merchantId == null) {
            throw new IllegalArgumentException("Merchant is required");
        }

        // The same answer for "no such transaction" and "somebody else's transaction", so nobody
        // can use this to find out which ids exist.
        bbpsTransactionRepository.findByTxnRefId(request.getTrackingValue().trim())
                .filter(t -> merchantId.equals(t.getMerchantId()))
                .orElseThrow(() -> new IllegalArgumentException("No transaction found for that ID"));

        request.setTrackingType("TRANS_REF_ID");
        request.setTrackingValue(request.getTrackingValue().trim());
        request.setFromDate(null);
        request.setToDate(null);

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

    private static final BigDecimal CASH_PAN_LIMIT = new BigDecimal("50000");
    private static final java.util.regex.Pattern PAN_FORMAT = java.util.regex.Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    /**
     * RBI rule passed on by Bill Avenue: a cash payment of Rs.50,000 or more must carry the customer's
     * PAN. Checked here, before any money moves, so the merchant is told straight away.
     */
    private static final java.util.regex.Pattern REMITTER_NAME_FORMAT =
            java.util.regex.Pattern.compile("^[A-Za-z][A-Za-z .'-]{1,49}$");

    /**
     * Bill Avenue spec (Remitter Info): the payer's name is mandatory for every payment
     * (it answers with E030 otherwise). Checked here, before any money moves.
     */
    private void requireRemitterName(BillPaymentRequest request) {
        String name = request.getCustomerInfo() != null ? request.getCustomerInfo().getRemitterName() : null;
        name = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (!REMITTER_NAME_FORMAT.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "The payer's name is required (letters only, 2 to 50 characters)");
        }
        request.getCustomerInfo().setRemitterName(name);
    }

    private void requirePanForLargeCashPayment(BillPaymentRequest request, BigDecimal billAmount) {
        if (billAmount.compareTo(CASH_PAN_LIMIT) < 0 || !"Cash".equalsIgnoreCase(vendorPaymentMode)) {
            return;
        }
        String pan = request.getCustomerInfo() != null ? request.getCustomerInfo().getCustomerPan() : null;
        pan = pan == null ? "" : pan.trim().toUpperCase();
        if (!PAN_FORMAT.matcher(pan).matches()) {
            throw new IllegalArgumentException(
                    "The customer's PAN number is required for cash payments of Rs.50,000 or more");
        }
        request.getCustomerInfo().setCustomerPan(pan);
    }

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

    private void collectErrors(JsonNode node, java.util.List<String> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(n -> collectErrors(n, out));
        } else if (node.isObject()) {
            String message = node.path("errorMessage").asText("");
            if (!message.isBlank()) {
                String code = node.path("errorCode").asText("");
                out.add(code.isBlank() ? message : "[" + code + "] " + message);
            }
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

    /**
     * The reason Bill Avenue gave. It is not always shaped the same: errorInfo can be
     * {"error":[{...}]} or [{"error":{...}}], and some answers carry vErrorRootVO.
     */
    private String extractErrorMessage(JsonNode vendorResponse) {
        java.util.List<String> reasons = new java.util.ArrayList<>();

        JsonNode errorInfo = vendorResponse.path("errorInfo");
        collectErrors(errorInfo.path("error"), reasons);
        if (errorInfo.isArray()) {
            for (JsonNode item : errorInfo) {
                collectErrors(item.path("error"), reasons);
            }
        }
        collectErrors(vendorResponse.path("vErrorRootVO").path("error"), reasons);

        if (!reasons.isEmpty()) {
            return String.join(" | ", reasons);
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
        deviceInfo.setIp(agentDevice.ip());
        deviceInfo.setInitChannel(agentDevice.channel());
        deviceInfo.setMac(agentDevice.mac());
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

        // Remitter info, in the form of Bill Avenue's own UAT samples for the AGT channel with Cash:
        //   "paymentRefId": "<our reference>"                       (top level of the request)
        //   "paymentInfo": {"info": [{"infoName": "Payment Account Info", "infoValue": "Cash"}]}
        // Set here, never taken from the website.
        request.setPaymentRefId(requestId.length() > 27 ? requestId.substring(0, 27) : requestId);
        String accountInfo = "Cash".equalsIgnoreCase(vendorPaymentMode) ? "Cash" : vendorPaymentMode;
        BillPaymentRequest.PaymentInfo paymentInfo = new BillPaymentRequest.PaymentInfo();
        paymentInfo.setInfo(java.util.List.of(remitterInfo("Payment Account Info", accountInfo)));
        request.setPaymentInfo(paymentInfo);

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

    private BillPaymentRequest.PaymentInfo.Info remitterInfo(String name, String value) {
        BillPaymentRequest.PaymentInfo.Info info = new BillPaymentRequest.PaymentInfo.Info();
        info.setInfoName(name);
        info.setInfoValue(value);
        return info;
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

    /** The convenience fee the customer pays: empty means none; it must be a number, not negative. */
    private BigDecimal parseCustConvFee(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal fee;
        try {
            fee = new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("custConvFee must be a number");
        }
        if (fee.signum() < 0) {
            throw new IllegalArgumentException("custConvFee cannot be negative");
        }
        return fee;
    }

    private BigDecimal parseRupeeString(String rupeeString) {
        if (rupeeString == null || rupeeString.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(rupeeString);
    }

    /**
     * BBPS service charge, from the same charge engine the payout flow uses (payment mode
     * BBPS_<CATEGORY>, for example BBPS_CREDIT_CARD or BBPS_ELECTRICITY).
     * If none is configured for this merchant / amount, the payment is refused (bbps.charge-required,
     * on by default) instead of silently charging nothing. Nothing has been debited at this point.
     */
    private BigDecimal calculateCharges(BigDecimal amount, Long merchantId, String category) {
        String modeCode = chargeModeFor(category);
        if (modeCode == null) {
            log.error("No BBPS charge mode for category '{}' (expected {}<CATEGORY>) and the default fallback is off - payment refused",
                    category, CATEGORY_MODE_PREFIX);
            throw new IllegalStateException("Service charges for "
                    + (category != null && !category.isBlank() ? category : "this") + " bill payment are not configured. "
                    + "Please contact the administrator.");
        }
        try {
            return paymentChargeService.calculateCharges(amount, modeCode, merchantId);
        } catch (Exception ex) {
            if (chargeRequired) {
                log.error("No BBPS charge configuration (merchantId={} amount={} mode={}) - payment refused: {}",
                        merchantId, amount, modeCode, ex.getMessage());
                throw new IllegalStateException(
                        "Service charges for bill payment are not configured. Please contact the administrator.");
            }
            log.warn("No BBPS charge configuration found (merchantId={}) - using zero commission: {}",
                    merchantId, ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** The bill category (service name) of a biller from the saved biller list, or null if unknown. */
    private String categoryOfBiller(String billerId) {
        if (billerId == null || billerId.isBlank()) {
            return null;
        }
        return billAvenueConfigRepository.findByProviderId(billerId.trim())
                .map(BillAvenueConfig::getServiceName).orElse(null);
    }

    /**
     * Which payment mode's charge slab applies: BBPS_<CATEGORY> when that mode exists (for example
     * BBPS_ELECTRICITY for the Electricity category). Without one, null (the payment is refused) unless
     * bbps.charge-default-fallback=true, which uses the default mode (bbps.charge-mode-code).
     */
    String chargeModeFor(String category) {
        if (category == null || category.isBlank()) {
            return chargeDefaultFallback ? chargeModeCode : null;
        }
        String key = category.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        String specific = CATEGORY_MODE_PREFIX + key;
        if (!key.isEmpty() && paymentChargeService.paymentModeExists(specific)) {
            return specific;
        }
        if (!chargeDefaultFallback) {
            return null;
        }
        log.warn("No payment mode {} for category '{}' - using the default charge mode {}",
                specific, category, chargeModeCode);
        return chargeModeCode;
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
