package com.project2.ism.Service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.PayoutDTO.*;
import com.project2.ism.Model.*;
import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Model.Payment.VendorBank;
import com.project2.ism.Model.Payment.VendorState;
import com.project2.ism.Model.Payout.PayoutBanks;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Service for handling payout operations
 */
@Service
public class PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutService.class);

    @Value("${payout.default.vendor.id}")
    private Long defaultPayoutVendorId; // ViMoPay - TODO: Make dynamic when multiple vendors available

    private final PayoutTransactionRepository payoutTxnRepo;
    private final MerchantWalletRepository merchantWalletRepo;
    private final FranchiseWalletRepository franchiseWalletRepo;
    private final MerchantTransDetRepository merchantTxnRepo;
    private final FranchiseTransDetRepository franchiseTxnRepo;
    private final MerchantRepository merchantRepo;
    private final FranchiseRepository franchiseRepo;
    private final VimoPayClientService vimoPayClient;
    private final PaymentChargeService paymentChargeService;
    private final PayoutBankRepository payoutBanksRepo;
    private final VendorStateRepository vendorStateRepo;
    private final VendorBankRepository vendorBankRepo;
    private final MerchantTransDetRepository merchantTransDetRepo;

    private final PaymentVendorResponseLogRepository paymentVendorResponseLogRepository;
    private final ObjectMapper objectMapper;


    public PayoutService(PayoutTransactionRepository payoutTxnRepo,
                         MerchantWalletRepository merchantWalletRepo,
                         FranchiseWalletRepository franchiseWalletRepo,
                         MerchantTransDetRepository merchantTxnRepo,
                         FranchiseTransDetRepository franchiseTxnRepo,
                         MerchantRepository merchantRepo,
                         FranchiseRepository franchiseRepo,
                         VimoPayClientService vimoPayClient,
                         PaymentChargeService paymentChargeService,
                         PayoutBankRepository payoutBanksRepo, VendorStateRepository vendorStateRepo, VendorBankRepository vendorBankRepo, PaymentVendorResponseLogRepository paymentVendorResponseLogRepository, ObjectMapper objectMapper,
                         MerchantTransDetRepository merchantTransDetRepo) {
        this.payoutTxnRepo = payoutTxnRepo;
        this.merchantWalletRepo = merchantWalletRepo;
        this.franchiseWalletRepo = franchiseWalletRepo;
        this.merchantTxnRepo = merchantTxnRepo;
        this.franchiseTxnRepo = franchiseTxnRepo;
        this.merchantRepo = merchantRepo;
        this.franchiseRepo = franchiseRepo;
        this.vimoPayClient = vimoPayClient;
        this.paymentChargeService = paymentChargeService;
        this.payoutBanksRepo = payoutBanksRepo;
        this.vendorStateRepo = vendorStateRepo;
        this.vendorBankRepo = vendorBankRepo;
        this.paymentVendorResponseLogRepository = paymentVendorResponseLogRepository;
        this.objectMapper = objectMapper;
        this.merchantTransDetRepo = merchantTransDetRepo;
    }


    @Async("payoutExecutor")
    @Transactional
    public void handleEncryptedCallbackAsync(Map<String, Object> body) {
        try {
            handleEncryptedCallback(body);
        } catch (Exception ex) {
            log.error("Callback processing failed: {}", ex.getMessage(), ex);
            // optionally save internal log to DB
        }
    }


    /**
     * Initiate payout - validates balance, calculates charges, deducts from wallet,
     * records in ledger, sends to vendor
     */
    @Transactional
    public PayoutResult initiatePayout(PayoutRequest request, Long vendorId) {
        log.info("Initiating payout for initiator={} type={} amount={}",
                request.getInitiatorId(), request.getInitiatorType(),
                request.getAmount());

        try {
            // 1. Validate unique merchant ref
            //validateMerchantRef(request.getMerchantRefId());

            // 2. Calculate charges based on amount and payment mode
            BigDecimal charges = paymentChargeService.calculateCharges(
                    request.getAmount(), request.getPaymentMode());
            BigDecimal totalDeduction = request.getAmount().add(charges);

            log.debug("Calculated charges={} totalDeduction={}", charges, totalDeduction);

            // 3. Validate and lock wallet
            BigDecimal remainingBalance;
            if ("MERCHANT".equals(request.getInitiatorType())) {
                remainingBalance = validateAndDeductMerchantBalance(
                        request.getInitiatorId(), totalDeduction);
            } else if ("FRANCHISE".equals(request.getInitiatorType())) {
                remainingBalance = validateAndDeductFranchiseBalance(
                        request.getInitiatorId(), totalDeduction);
            } else {
                throw new IllegalArgumentException("Invalid initiator type: " + request.getInitiatorType());
            }

            // 4. Create payout transaction record (status = PENDING)
            PayoutTransaction payoutTxn = createPayoutTransaction(request, charges, vendorId);

            // 5. Record in ledger (DEBIT entry)
            recordInLedger(payoutTxn, remainingBalance);
            request.setMerchantRefId(payoutTxn.getMerchantRefId());
            // 6. Send to vendor
            PayoutResult vendorResult = vimoPayClient.submitPayout(vendorId, request, charges);

            // 7. Update payout transaction with vendor response
            updatePayoutWithVendorResponse(payoutTxn, vendorResult);

            log.info("Payout initiated successfully: ref={} vendorTxnId={} status={}",
                    request.getMerchantRefId(), vendorResult.getTxnId(), vendorResult.getStatus());

            // if immediate failure returned by vendor, refund now
            if ("FAILED".equalsIgnoreCase(vendorResult.getStatus())) {
                // ensure payoutTxn is up to date
                refundToWallet(payoutTxn);
            }
            vendorResult.setCharges(charges);
            vendorResult.setRemainingBalance(remainingBalance);
            return vendorResult;

        } catch (Exception e) {
            log.error("Payout initiation failed for ref={}: {}",
                    request.getMerchantRefId(), e.getMessage(), e);
            return PayoutResult.failed(request.getMerchantRefId(), e.getMessage());
        }
    }

    /**
     * NEW: Simplified payout using saved bank account
     * User just provides: payoutBankId, amount, mode
     */
    @Transactional
    public PayoutResult initiateSimplePayout(SimplePayoutRequest simpleRequest, Long vendorId) {
        log.info("Initiating simple payout: bankId={} amount={} mode={}",
                simpleRequest.getPayoutBankId(), simpleRequest.getAmount(),
                simpleRequest.getPaymentMode());//, simpleRequest.getMerchantRefId());

        try {
            // 1. Get verified bank details
            PayoutBanks bank = payoutBanksRepo.findByIdAndVerifiedTrue(simpleRequest.getPayoutBankId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Bank account not found or not verified: " + simpleRequest.getPayoutBankId()));


            // 2. Get state code and bank code from vendor masters
            String stateCode = getStateCode(bank.getStateName());
            String bankCode = getBankCode(bank.getBankName());

            // 3. Default payment purpose if not provided
            String paymentPurpose = simpleRequest.getPaymentPurpose() != null
                    ? simpleRequest.getPaymentPurpose()
                    : "004"; // Default purpose code

            // 4. Build full PayoutRequest
            PayoutRequest fullRequest = new PayoutRequest();
            fullRequest.setInitiatorType(bank.getCustomerType());
            fullRequest.setInitiatorId(bank.getCustomerId());
            fullRequest.setAmount(simpleRequest.getAmount());
            fullRequest.setPaymentMode(simpleRequest.getPaymentMode());
            fullRequest.setPaymentPurpose(paymentPurpose);
            fullRequest.setBeneficiaryBank(bankCode);
            fullRequest.setBeneficiaryAccountNumber(bank.getAccountNumber());
            fullRequest.setBeneficiaryIFSC(bank.getIfscCode());
            fullRequest.setBeneficiaryMobileNumber(
                    resolveBeneficiaryMobile(bank.getCustomerType(), bank.getCustomerId())
            );

            fullRequest.setBeneficiaryLocation(stateCode);
            fullRequest.setBeneficiaryName(bank.getHolderName());
//            fullRequest.setMerchantRefId(simpleRequest.getMerchantRefId());
//            fullRequest.setLat(simpleRequest.getLat());
//            fullRequest.setLongitude(simpleRequest.getLongitude());
            fullRequest.setLat("19.0760");       // Mumbai lat
            fullRequest.setLongitude("72.8777"); // Mumbai long

            fullRequest.setUdf1(simpleRequest.getUdf1());
            fullRequest.setUdf2(simpleRequest.getUdf2());
            fullRequest.setUdf3(simpleRequest.getUdf3());

            // 5. Call standard payout flow
            return initiatePayout(fullRequest, vendorId);

        } catch (Exception e) {
            log.error("Simple payout initiation failed: {}", e.getMessage(), e);
            return PayoutResult.failed(null, e.getMessage());   // no merchantRefId yet
        }

    }

    // Helper methods to get codes from vendor data
    private String getStateCode(String stateName) {
        return vendorStateRepo.findByStateName(stateName)
                .map(VendorState::getStateCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid state: " + stateName));
    }

    private String getBankCode(String bankName) {
        return vendorBankRepo.findByBankName(bankName)
                .map(VendorBank::getBankCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid bank: " + bankName));
    }



    private String resolveBeneficiaryMobile(String customerType, Long customerId) {

        if ("MERCHANT".equalsIgnoreCase(customerType)) {
            Merchant merchant = merchantRepo.findById(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + customerId));
            return merchant.getContactPerson().getPhoneNumber();    // Change this to your merchant mobile field
        }

        if ("FRANCHISE".equalsIgnoreCase(customerType)) {
            Franchise franchise = franchiseRepo.findById(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Franchise not found: " + customerId));
            return franchise.getContactPerson().getPhoneNumber();     // Change to your franchise mobile field
        }

        throw new IllegalArgumentException("Invalid customer type: " + customerType);
    }

    @Transactional
    public void handleEncryptedCallback(Map<String, Object> encryptedBody) {

        final Long vendorId = defaultPayoutVendorId; // ViMoPay (default)

        log.info(" Handling encrypted vendor callback | vendorId={}", vendorId);
        log.debug("Encrypted callback keys: {}", encryptedBody.keySet());

        PayoutCallback callback;
        try {
            log.info("➡ Decrypting & parsing vendor callback | vendorId={}", vendorId);

            // This method already logs encrypted/plain payload internally
            callback = vimoPayClient.handleEncryptedCallback(vendorId, encryptedBody);

            log.info(" Vendor callback decrypted & parsed | vendorId={} | callbackId={}",
                    vendorId,
                    callback != null ? callback.getMerchantRefId() : null
            );

        } catch (Exception e) {

            // Critical: decryption/parsing failed
            log.error("Failed to parse/decrypt vendor callback | vendorId={}", vendorId, e);

            persistUnknownCallback(
                    vendorId,
                    "CALLBACK_DECRYPT_ERROR",
                    encryptedBody,
                    null,
                    e.getMessage()
            );

            log.warn("⚠ Raw encrypted callback persisted | vendorId={} | reason=CALLBACK_DECRYPT_ERROR", vendorId);

            return; // swallow exception → vendor still receives 200
        }

        try {
            log.info("➡ Processing parsed vendor callback | vendorId={} | callbackId={}",
                    vendorId,
                    callback.getMerchantRefId());

            handleCallback(callback);

            log.info(" Vendor callback processed successfully | vendorId={} | callbackId={}",
                    vendorId,
                    callback.getMerchantRefId());

        } catch (Exception e) {
            // Optional: depends on your retry/alert strategy
            log.error(" Error while processing parsed callback | vendorId={} | callbackId={}",
                    vendorId,
                    callback.getMerchantRefId(),
                    e
            );
            // do NOT rethrow
        }
    }



    /**
     * Handle callback from vendor with transaction status update
     */
    @Transactional
    public void handleCallback(PayoutCallback callback) {
        log.info("Received payout callback: merchantRef={} status={} txnId={}",
                callback.getMerchantRefId(), callback.getTxnStatus(), callback.getTxnId());

        try {
            PayoutTransaction payoutTxn = payoutTxnRepo.findByMerchantRefId(callback.getMerchantRefId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Payout transaction not found: " + callback.getMerchantRefId()));

            // Update transaction status
            updatePayoutStatus(payoutTxn, callback);

            // Update ledger status
            updateLedgerStatus(payoutTxn, callback);

            log.info("Callback processed successfully for ref={}", callback.getMerchantRefId());

        } catch (Exception e) {
            log.error("Failed to process callback for ref={}: {}",
                    callback.getMerchantRefId(), e.getMessage(), e);
            throw new RuntimeException("Callback processing failed", e);
        }
    }

    // ==================== VALIDATION METHODS ====================

    private void validateMerchantRef(String merchantRefId) {
        if (payoutTxnRepo.existsByMerchantRefId(merchantRefId)) {
            throw new IllegalArgumentException("Duplicate merchant reference: " + merchantRefId);
        }
    }

    private BigDecimal validateAndDeductMerchantBalance(Long merchantId, BigDecimal totalDeduction) {
        MerchantWallet wallet = merchantWalletRepo.findByMerchantIdForUpdate(merchantId)
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
        merchantWalletRepo.save(wallet);

        log.debug("Merchant {} wallet deducted: {} -> {}", merchantId, available, newBalance);
        return newBalance;
    }

    private BigDecimal validateAndDeductFranchiseBalance(Long franchiseId, BigDecimal totalDeduction) {
        FranchiseWallet wallet = franchiseWalletRepo.findByFranchiseIdForUpdate(franchiseId)
                .orElseThrow(() -> new IllegalArgumentException("Franchise wallet not found: " + franchiseId));

        BigDecimal available = nvl(wallet.getAvailableBalance());
        if (available.compareTo(totalDeduction) < 0) {
            throw new IllegalStateException("Insufficient balance. Available: " + available +
                    ", Required: " + totalDeduction);
        }

        BigDecimal newBalance = available.subtract(totalDeduction);
        wallet.setAvailableBalance(newBalance);
        wallet.setLastUpdatedAmount(totalDeduction.negate());
        wallet.setLastUpdatedAt(LocalDateTime.now());
        franchiseWalletRepo.save(wallet);

        log.debug("Franchise {} wallet deducted: {} -> {}", franchiseId, available, newBalance);
        return newBalance;
    }

    // ==================== TRANSACTION MANAGEMENT ====================

    private PayoutTransaction createPayoutTransaction(PayoutRequest request, BigDecimal charges, Long vendorId) {
        PayoutTransaction txn = new PayoutTransaction();
        //txn.setMerchantRefId(request.getMerchantRefId()); for now commenting trying to implement in backend to create this
        txn.setInitiatorType(request.getInitiatorType());
        txn.setInitiatorId(request.getInitiatorId());
        txn.setAmount(request.getAmount());
        txn.setCharges(charges);
        txn.setTotalDeducted(request.getAmount().add(charges));
        txn.setPaymentMode(request.getPaymentMode());
        txn.setPaymentPurpose(request.getPaymentPurpose());
        txn.setBeneficiaryName(request.getBeneficiaryName());
        txn.setBeneficiaryAccountNumber(request.getBeneficiaryAccountNumber());
        txn.setBeneficiaryIFSC(request.getBeneficiaryIFSC());
        txn.setBeneficiaryBank(request.getBeneficiaryBank());
        txn.setBeneficiaryMobile(request.getBeneficiaryMobileNumber());
        txn.setBeneficiaryLocation(request.getBeneficiaryLocation());
        txn.setLatitude(request.getLat());
        txn.setLongitude(request.getLongitude());
        txn.setUdf1(request.getUdf1());
        txn.setUdf2(request.getUdf2());
        txn.setUdf3(request.getUdf3());
        txn.setStatus(PayoutTransaction.PayoutStatus.PENDING);
        txn.setVendorId(vendorId);

        return payoutTxnRepo.save(txn);
    }

    private void updatePayoutWithVendorResponse(PayoutTransaction payoutTxn, PayoutResult vendorResult) {

        if (vendorResult.getTxnId() != null) {
            payoutTxn.setVendorTxnId(vendorResult.getTxnId());
        }

        payoutTxn.setResponseMessage(vendorResult.getMessage());
        payoutTxn.setRrn(vendorResult.getRrn());

        switch (vendorResult.getStatus()) {
            case "OK":
                payoutTxn.setStatus(PayoutTransaction.PayoutStatus.SUCCESS);
                payoutTxn.setCompletedAt(LocalDateTime.now());
                payoutTxn.setVendorTxnId(vendorResult.getTxnId());
                payoutTxn.setVendorCharges(vendorResult.getVendorCharges());
                break;

            case "PENDING":
                payoutTxn.setStatus(PayoutTransaction.PayoutStatus.PENDING);
                payoutTxn.setVendorTxnId(vendorResult.getTxnId());
                payoutTxn.setVendorCharges(vendorResult.getVendorCharges());
                break;

            case "FAILED":
                payoutTxn.setStatus(PayoutTransaction.PayoutStatus.FAILED);
                payoutTxn.setCompletedAt(LocalDateTime.now());
                break;
        }

        payoutTxnRepo.save(payoutTxn);
        // NEW: update ledger entry also
        updateLedgerWithVendorResponse(payoutTxn, vendorResult);
    }

    /**
     * Update ledger status when vendor responds immediately (not via callback)
     */
    private void updateLedgerWithVendorResponse(PayoutTransaction payoutTxn, PayoutResult vendorResult) {
        String finalStatus = switch (vendorResult.getStatus()) {
            case "OK" -> "SUCCESS";
            case "FAILED" -> "FAILED";
            case "PENDING" -> "PENDING";
            default -> "PENDING";
        };

        // Update merchant ledger if exists
        if (payoutTxn.getLedgerMerchantTxnId() != null) {
            merchantTxnRepo.findById(payoutTxn.getLedgerMerchantTxnId()).ifPresent(mtd -> {
                mtd.setTranStatus(finalStatus);
                if (vendorResult.getTxnId() != null) {
                    mtd.setVendorTransactionId(vendorResult.getTxnId());
                }
                if (vendorResult.getRrn() != null) {
                    mtd.setBankRefId(vendorResult.getRrn()); // RRN is more important than txnId
                }
                if ("FAILED".equals(finalStatus)) {
                    mtd.setFailureRemarks(vendorResult.getMessage());
                }
                merchantTxnRepo.save(mtd);
                log.debug("Updated merchant ledger {} to status {}", mtd.getTransactionId(), finalStatus);
            });
        }

        // Update franchise ledger if exists
        if (payoutTxn.getLedgerFranchiseTxnId() != null) {
            franchiseTxnRepo.findById(payoutTxn.getLedgerFranchiseTxnId()).ifPresent(ftd -> {
                ftd.setTranStatus(finalStatus);
                if (vendorResult.getTxnId() != null) {
                    ftd.setVendorTransactionId(vendorResult.getTxnId());
                }
                if (vendorResult.getRrn() != null) {
                    ftd.setBankRefId(vendorResult.getRrn()); // RRN is more important than txnId
                }
                if ("FAILED".equals(finalStatus)) {
                    ftd.setFailureRemarks(vendorResult.getMessage());
                }
                franchiseTxnRepo.save(ftd);
                log.debug("Updated franchise ledger {} to status {}", ftd.getTransactionId(), finalStatus);
            });
        }
    }

    private void updatePayoutStatus(PayoutTransaction payoutTxn, PayoutCallback callback) {
        payoutTxn.setVendorTxnId(callback.getTxnId());
        payoutTxn.setStatusCode(callback.getTxnStatusCode());
        payoutTxn.setRrn(callback.getRrn());
        payoutTxn.setResponseMessage(callback.getResponseMessage());

        if ("000".equals(callback.getTxnStatusCode()) || "Success".equalsIgnoreCase(callback.getTxnStatus())) {


            payoutTxn.setStatus(PayoutTransaction.PayoutStatus.SUCCESS);
            payoutTxn.setCompletedAt(LocalDateTime.now());
        } else if ("001".equals(callback.getTxnStatusCode()) || "Failed".equalsIgnoreCase(callback.getTxnStatus())) {
            // If already failed, DO NOT REFUND AGAIN
            if (payoutTxn.getStatus() == PayoutTransaction.PayoutStatus.FAILED) {
                log.warn("Duplicate FAILED callback ignored for ref={}", payoutTxn.getMerchantRefId());
                return; // STOP here
            }
            payoutTxn.setStatus(PayoutTransaction.PayoutStatus.FAILED);
            payoutTxn.setCompletedAt(LocalDateTime.now());

            // Refund the amount back to wallet
            refundToWallet(payoutTxn);
        }
        // "002" or "Pending" keeps status as PENDING

        payoutTxnRepo.save(payoutTxn);
    }

    // ==================== LEDGER MANAGEMENT ====================

    private void recordInLedger(PayoutTransaction payoutTxn, BigDecimal remainingBalance) {
        if ("MERCHANT".equals(payoutTxn.getInitiatorType())) {
            recordMerchantLedger(payoutTxn, remainingBalance);
        } else {
            recordFranchiseLedger(payoutTxn, remainingBalance);
        }
    }

    private void recordMerchantLedger(PayoutTransaction payoutTxn, BigDecimal remainingBalance) {
        Merchant merchant = merchantRepo.findById(payoutTxn.getInitiatorId())
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));

        MerchantTransactionDetails mtd = new MerchantTransactionDetails();
        mtd.setMerchant(merchant);
        mtd.setActionOnBalance("DEBIT");
        mtd.setAmount(payoutTxn.getTotalDeducted().negate()); // negative for debit
        mtd.setCharge(payoutTxn.getCharges());
        mtd.setNetAmount(payoutTxn.getAmount().negate());
        mtd.setBalBeforeTran(remainingBalance.add(payoutTxn.getTotalDeducted()));
        mtd.setBalAfterTran(remainingBalance);
        mtd.setFinalBalance(remainingBalance);
        mtd.setTransactionDate(LocalDateTime.now());
        mtd.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        mtd.setTranStatus("PENDING");
        mtd.setTransactionType("DEBIT");
        mtd.setService("PAYOUT");
        //mtd.setVendorTransactionId(payoutTxn.getMerchantRefId());
        mtd.setRemarks("Payout to " + payoutTxn.getBeneficiaryName() +
                " - " + payoutTxn.getBeneficiaryAccountNumber());
        mtd.setBankRefId(payoutTxn.getVendorTxnId());

        mtd.setGrossCharge(payoutTxn.getCharges());
        mtd = merchantTxnRepo.save(mtd);
        payoutTxn.setLedgerMerchantTxnId(mtd.getTransactionId());
        // Now generate merchantRefId
        String merchantRefId = "MER-" + mtd.getTransactionId();
        payoutTxn.setMerchantRefId(merchantRefId);
        payoutTxnRepo.save(payoutTxn);

        log.debug("Recorded merchant ledger entry: txnId={}", mtd.getTransactionId());
    }

    private void recordFranchiseLedger(PayoutTransaction payoutTxn, BigDecimal remainingBalance) {
        Franchise franchise = franchiseRepo.findById(payoutTxn.getInitiatorId())
                .orElseThrow(() -> new IllegalArgumentException("Franchise not found"));

        FranchiseTransactionDetails ftd = new FranchiseTransactionDetails();
        ftd.setFranchise(franchise);
        ftd.setActionOnBalance("DEBIT");
        ftd.setAmount(payoutTxn.getTotalDeducted().negate());
        ftd.setNetAmount(payoutTxn.getAmount().negate());
        ftd.setBalBeforeTran(remainingBalance.add(payoutTxn.getTotalDeducted()));
        ftd.setBalAfterTran(remainingBalance);
        ftd.setFinalBalance(remainingBalance);
        ftd.setTransactionDate(LocalDateTime.now());
        ftd.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        ftd.setTranStatus("PENDING");
        ftd.setTransactionType("DEBIT");
        ftd.setService("PAYOUT");
        //ftd.setVendorTransactionId(payoutTxn.getMerchantRefId());
        ftd.setRemarks("Payout to " + payoutTxn.getBeneficiaryName() +
                " - " + payoutTxn.getBeneficiaryAccountNumber());
        ftd.setBankRefId(payoutTxn.getVendorTxnId());
        ftd.setCharge(payoutTxn.getCharges());
        ftd = franchiseTxnRepo.save(ftd);
        payoutTxn.setLedgerFranchiseTxnId(ftd.getTransactionId());
        // Generate merchantRefId from ledger id
        String merchantRefId = "FRA-" + ftd.getTransactionId();
        payoutTxn.setMerchantRefId(merchantRefId);
        payoutTxnRepo.save(payoutTxn);

        log.debug("Recorded franchise ledger entry: txnId={}", ftd.getTransactionId());
    }

    private void updateLedgerStatus(PayoutTransaction payoutTxn, PayoutCallback callback) {
        String finalStatus;
        if ("000".equals(callback.getTxnStatusCode()) || "Success".equalsIgnoreCase(callback.getTxnStatus())) {
            finalStatus = "SUCCESS";
        } else if ("001".equals(callback.getTxnStatusCode()) || "Failed".equalsIgnoreCase(callback.getTxnStatus())) {
            finalStatus = "FAILED";
        } else {
            finalStatus = "PENDING";
        }

        if (payoutTxn.getLedgerMerchantTxnId() != null) {
            merchantTxnRepo.findById(payoutTxn.getLedgerMerchantTxnId()).ifPresent(mtd -> {
                mtd.setTranStatus(finalStatus);
                mtd.setBankRefId(callback.getRrn());
                mtd.setFailureRemarks(callback.getResponseMessage());
                mtd.setVendorTransactionId(payoutTxn.getVendorTxnId());
                merchantTxnRepo.save(mtd);
            });
        }

        if (payoutTxn.getLedgerFranchiseTxnId() != null) {
            franchiseTxnRepo.findById(payoutTxn.getLedgerFranchiseTxnId()).ifPresent(ftd -> {
                ftd.setTranStatus(finalStatus);
                ftd.setBankRefId(callback.getRrn());
                ftd.setFailureRemarks(callback.getResponseMessage());
                ftd.setVendorTransactionId(payoutTxn.getVendorTxnId());
                franchiseTxnRepo.save(ftd);
            });
        }
    }

    private void refundToWallet(PayoutTransaction payoutTxn) {
        log.info("Refunding failed payout: ref={} amount={}",
                payoutTxn.getMerchantRefId(), payoutTxn.getTotalDeducted());

        if ("MERCHANT".equals(payoutTxn.getInitiatorType())) {
            refundMerchantWallet(payoutTxn);
        } else {
            refundFranchiseWallet(payoutTxn);
        }
    }

    private void refundMerchantWallet(PayoutTransaction payoutTxn) {
        MerchantWallet wallet = merchantWalletRepo.findByMerchantIdForUpdate(payoutTxn.getInitiatorId())
                .orElseThrow(() -> new IllegalArgumentException("Merchant wallet not found"));

        BigDecimal newBalance = nvl(wallet.getAvailableBalance()).add(payoutTxn.getTotalDeducted());
        wallet.setAvailableBalance(newBalance);
        wallet.setLastUpdatedAmount(payoutTxn.getTotalDeducted());
        wallet.setLastUpdatedAt(LocalDateTime.now());
        merchantWalletRepo.save(wallet);

        // Create refund ledger entry
        Merchant merchant = merchantRepo.findById(payoutTxn.getInitiatorId()).orElseThrow();
        MerchantTransactionDetails refundEntry = new MerchantTransactionDetails();
        refundEntry.setMerchant(merchant);
        refundEntry.setActionOnBalance("CREDIT");
        refundEntry.setAmount(payoutTxn.getTotalDeducted());
        refundEntry.setBalBeforeTran(newBalance.subtract(payoutTxn.getTotalDeducted()));
        refundEntry.setBalAfterTran(newBalance);
        refundEntry.setFinalBalance(newBalance);
        refundEntry.setTransactionDate(LocalDateTime.now());
        refundEntry.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        refundEntry.setTranStatus("SUCCESS");
        refundEntry.setTransactionType("CREDIT");
        refundEntry.setService("PAYOUT_REFUND");
        refundEntry.setVendorTransactionId(payoutTxn.getVendorTxnId());
        refundEntry.setRemarks("Refund for failed payout - " + payoutTxn.getResponseMessage());
        merchantTxnRepo.save(refundEntry);

        log.debug("Refunded merchant {} wallet: amount={}", payoutTxn.getInitiatorId(), payoutTxn.getTotalDeducted());
    }

    private void refundFranchiseWallet(PayoutTransaction payoutTxn) {
        FranchiseWallet wallet = franchiseWalletRepo.findByFranchiseIdForUpdate(payoutTxn.getInitiatorId())
                .orElseThrow(() -> new IllegalArgumentException("Franchise wallet not found"));

        BigDecimal newBalance = nvl(wallet.getAvailableBalance()).add(payoutTxn.getTotalDeducted());
        wallet.setAvailableBalance(newBalance);
        wallet.setLastUpdatedAmount(payoutTxn.getTotalDeducted());
        wallet.setLastUpdatedAt(LocalDateTime.now());
        franchiseWalletRepo.save(wallet);

        // Create refund ledger entry
        Franchise franchise = franchiseRepo.findById(payoutTxn.getInitiatorId()).orElseThrow();
        FranchiseTransactionDetails refundEntry = new FranchiseTransactionDetails();
        refundEntry.setFranchise(franchise);
        refundEntry.setActionOnBalance("CREDIT");
        refundEntry.setAmount(payoutTxn.getTotalDeducted());
        refundEntry.setBalBeforeTran(newBalance.subtract(payoutTxn.getTotalDeducted()));
        refundEntry.setBalAfterTran(newBalance);
        refundEntry.setFinalBalance(newBalance);
        refundEntry.setTransactionDate(LocalDateTime.now());
        refundEntry.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        refundEntry.setTranStatus("SUCCESS");
        refundEntry.setTransactionType("CREDIT");
        refundEntry.setService("PAYOUT_REFUND");
        refundEntry.setVendorTransactionId(payoutTxn.getVendorTxnId());
        refundEntry.setRemarks("Refund for failed payout - " + payoutTxn.getResponseMessage());
        franchiseTxnRepo.save(refundEntry);

        log.debug("Refunded franchise {} wallet: amount={}", payoutTxn.getInitiatorId(), payoutTxn.getTotalDeducted());
    }

    // =======
    private void persistUnknownCallback(Long vendorId, String apiName, Map<String, Object> rawBody, PayoutCallback callback, String note) {
        try {
            PaymentVendorResponseLog logEntry = new PaymentVendorResponseLog();
            logEntry.setVendorId(vendorId);
            logEntry.setApiName(apiName);
            // raw request JSON
            logEntry.setRequestPayload(objectMapper.writeValueAsString(rawBody));
            // encryptedRequest not applicable (we already have only raw body)
            logEntry.setEncryptedRequest(null);
            logEntry.setEncryptedResponse(null);
            // store the parsed/decrypted callback (useful for investigations)
            logEntry.setDecryptedResponse(callback != null ? objectMapper.writeValueAsString(callback) : null);

            // statusCode: store 0 or a custom code; we will use 422 for unprocessable/unmatched
            logEntry.setStatusCode(422);

            // add a short note in responseMessage? your model doesn't have that — so we rely on decryptedResponse.
            logEntry.setCreatedOn(LocalDateTime.now());

            paymentVendorResponseLogRepository.save(logEntry);

            log.warn("Persisted unmatched vendor callback (apiName={} vendorId={}): merchantRef={} note={}",
                    apiName, vendorId, callback != null ? callback.getMerchantRefId() : "<null>", note);

        } catch (Exception e) {
            log.error("Failed to persist unknown callback for vendor={}, merchantRef={}: {}",
                    vendorId, callback != null ? callback.getMerchantRefId() : "<null>", e.getMessage(), e);
        }
    }

    public PayoutTransactionReportResponse getTransactionReport(
            LocalDate startDate,
            LocalDate endDate,
            String service,
            int page,
            int size
    ) {

        // 🔹 Date handling
        if (startDate == null && endDate == null) {
            endDate = LocalDate.now();
            startDate = endDate.minusDays(7);
        } else {
            if (startDate == null) {
                startDate = endDate.minusDays(7);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }
        }

        // 🔹 Service handling
        List<String> services;
        if (service == null || "BOTH".equalsIgnoreCase(service)) {
            services = List.of("PAYOUT", "PAYOUT_REFUND");
        } else {
            services = List.of(service);
        }

        // DateTime conversion (CORRECT & SAFE)
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        Pageable pageable = PageRequest.of(page, size);

        // Fetch data
        Page<PayoutTransactionReportDTO> transactions =
                merchantTransDetRepo.fetchTransactions(from, to, services, pageable);

        // Fetch counts
        PayoutServiceCountDTO counts =
                merchantTransDetRepo.fetchServiceCounts(from, to, services);

        // Build response
        PayoutTransactionReportResponse response = new PayoutTransactionReportResponse();
        response.setTotalCount(counts.getTotalCount());
        response.setPayoutCount(counts.getPayoutCount());
        response.setPayoutRefundCount(counts.getPayoutRefundCount());
        response.setTransactions(transactions);

        return response;
    }


// ==================== UTILITY METHODS ====================

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
