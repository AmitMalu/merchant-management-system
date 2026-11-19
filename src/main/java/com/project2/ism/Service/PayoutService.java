package com.project2.ism.Service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.PayoutDTO.*;
import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Model.*;
import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Model.Payment.VendorBank;
import com.project2.ism.Model.Payment.VendorState;
import com.project2.ism.Model.Payout.PaymentAttempt;
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

    /** ViMoPay refuses payouts under Rs 100 and at or above Rs 1 lakh (its API document). Checked before any debit. */
    @Value("${payout.min-amount:100}")
    private BigDecimal payoutMinAmount = new BigDecimal("100");

    @Value("${payout.max-amount:99999}")
    private BigDecimal payoutMaxAmount = new BigDecimal("99999");

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
    private final com.project2.ism.Service.Monitoring.TransactionEventService transactionEventService;
    private final com.project2.ism.Service.Monitoring.AlertService alertService;
    private final FeatureAccessService featureAccess;


    // Routing trial run: records which vendor routing WOULD choose, next to the vendor really used. It never
    // throws and never changes the payment. Optional, so a missing bean can never stop the application.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PaymentRoutingTrialService routingTrial;

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
                         MerchantTransDetRepository merchantTransDetRepo,
                         com.project2.ism.Service.Monitoring.TransactionEventService transactionEventService,
                         com.project2.ism.Service.Monitoring.AlertService alertService,
                         FeatureAccessService featureAccess) {
        this.featureAccess = featureAccess;
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
        this.transactionEventService = transactionEventService;
        this.alertService = alertService;
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
            // The Payout switch (Merchant Settings) must be on for this account, even if the menu is
            // visible. Checked before anything else, so nothing is deducted when it is off.
            featureAccess.requirePayoutEnabled(new CustomerRef(request.getInitiatorType(), request.getInitiatorId()));

            // Bank verification returns holder names with a title prefix
            // (e.g. "Mr. SUBHENDU  MONDAL"); the vendor fails payouts carrying
            // it, so clean the name once here — covers the vendor payload and
            // the stored payout_transactions row alike.
            request.setBeneficiaryName(sanitizeBeneficiaryName(request.getBeneficiaryName()));

            // Current-account guard: a business-looking holder name means the
            // account may be a current account (charges / locked funds). Refuse
            // before any wallet money moves. Also covers beneficiaries that were
            // saved before this check existed.
            String blockedKeyword = BeneficiaryNameValidator.findBlockedKeyword(request.getBeneficiaryName());
            if (blockedKeyword != null) {
                log.warn("Payout blocked, possible current account | initiator={} type={} beneficiary={} matchedKeyword={}",
                        request.getInitiatorId(), request.getInitiatorType(),
                        request.getBeneficiaryName(), blockedKeyword);
                throw new IllegalArgumentException(
                        BeneficiaryNameValidator.blockedMessage(request.getBeneficiaryName()));
            }

            // 1. Validate unique merchant ref
            //validateMerchantRef(request.getMerchantRefId());

            //2. New Validation
            validatePayoutSettings(request);

            // 3. Calculate charges based on amount and payment mode
            BigDecimal charges = paymentChargeService.calculateCharges(
                    request.getAmount(), request.getPaymentMode(), request.getInitiatorId());
            BigDecimal totalDeduction = request.getAmount().add(charges);

            log.debug("Calculated charges={} totalDeduction={}", charges, totalDeduction);

            // 4. Validate and lock wallet
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

            // 5. Create payout transaction record (status = PENDING)
            PayoutTransaction payoutTxn = createPayoutTransaction(request, charges, vendorId);

            // 6. Record in ledger (DEBIT entry)
            recordInLedger(payoutTxn, remainingBalance);
            request.setMerchantRefId(payoutTxn.getMerchantRefId());

            // Trial run only: note which vendor routing would choose. Does not change this payout.
            if (routingTrial != null) {
                routingTrial.recordPayout(request.getInitiatorType(), request.getInitiatorId(), request.getAmount(),
                        vendorId, payoutTxn.getMerchantRefId());
            }

            // Transaction Monitoring: record initiation so velocity rules can
            // count attempts even before the vendor responds.
            transactionEventService.recordEvent(TransactionSourceType.PAYOUT, payoutTxn.getId(),
                    request.getInitiatorType(), request.getInitiatorId(), totalDeduction,
                    TransactionEventStatus.PENDING, "paymentMode=" + request.getPaymentMode());

            // 7. Send to vendor
            PayoutResult vendorResult = vimoPayClient.submitPayout(vendorId, request, charges);

            // 8. Update payout transaction with vendor response
            updatePayoutWithVendorResponse(payoutTxn, vendorResult);

            // Transaction Monitoring: record the outcome
            transactionEventService.recordEvent(TransactionSourceType.PAYOUT, payoutTxn.getId(),
                    request.getInitiatorType(), request.getInitiatorId(), totalDeduction,
                    mapPayoutStatus(vendorResult.getStatus()), "vendorTxnId=" + vendorResult.getTxnId());

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
            PayoutTransaction payoutTxn = payoutTxnRepo.findByMerchantRefIdForUpdate(callback.getMerchantRefId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Payout transaction not found: " + callback.getMerchantRefId()));

            // A payout that already reached a final state must never be flipped
            // by a later callback: SUCCESS -> FAILED would refund the wallet for
            // money the bank already received (a forged or erroneous "failed"
            // message becomes free money), and FAILED -> SUCCESS would leave the
            // wallet refunded AND the bank paid. Identical repeats still flow
            // through as before; only a *conflicting* one is stopped, loudly,
            // for a human to reconcile against the vendor statement.
            boolean callbackSaysFailed = "001".equals(callback.getTxnStatusCode())
                    || "Failed".equalsIgnoreCase(callback.getTxnStatus());
            boolean callbackSaysSuccess = "000".equals(callback.getTxnStatusCode())
                    || "Success".equalsIgnoreCase(callback.getTxnStatus());
            PayoutTransaction.PayoutStatus current = payoutTxn.getStatus();
            if ((current == PayoutTransaction.PayoutStatus.SUCCESS && callbackSaysFailed)
                    || (current == PayoutTransaction.PayoutStatus.FAILED && callbackSaysSuccess)) {
                log.error("RECONCILE: conflicting vendor callback IGNORED. ref={} currentStatus={} " +
                                "callbackStatus={} callbackCode={} vendorTxnId={} — verify against the vendor statement",
                        callback.getMerchantRefId(), current, callback.getTxnStatus(),
                        callback.getTxnStatusCode(), callback.getTxnId());
                return;
            }

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

    // ==================== PAYOUT FLOW V2 (Phase 3) ====================
    //
    // The same rules as initiatePayout, in a safer order: the debit is COMMITTED first, the vendor is called with
    // no transaction and no wallet lock, and the answer is applied in a second short transaction. It is used only
    // when the payout engine switch says so (see PayoutEngine). The flow above is untouched.

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private java.util.List<PayoutVendorAdapter> payoutAdapters;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PaymentAttemptRepository attemptRepo;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.transaction.PlatformTransactionManager txManager;

    /** Picks the vendor from the account's routing, only when "Use routing for payouts" is on. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PayoutRoutingPlanner routingPlanner;

    private org.springframework.transaction.support.TransactionTemplate v2Template;

    private org.springframework.transaction.support.TransactionTemplate v2Tx() {
        if (v2Template == null) {
            v2Template = new org.springframework.transaction.support.TransactionTemplate(txManager);
        }
        return v2Template;
    }

    private PayoutVendorAdapter adapterFor(Long vendorId) {
        if (payoutAdapters == null) {
            return null;
        }
        for (PayoutVendorAdapter a : payoutAdapters) {
            if (a.supports(vendorId)) {
                return a;
            }
        }
        return null;
    }

    /** Is everything the new flow needs in place for this vendor? If not, the old flow is used. */
    public boolean v2Available(Long vendorId) {
        return attemptRepo != null && txManager != null && adapterFor(vendorId) != null;
    }

    /** The account a saved bank belongs to, or null (the old flow then reports the problem as it always did). */
    @Transactional(readOnly = true)
    public CustomerRef savedBankOwner(Long payoutBankId) {
        if (payoutBankId == null) {
            return null;
        }
        return payoutBanksRepo.findByIdAndVerifiedTrue(payoutBankId)
                .map(b -> new CustomerRef(b.getCustomerType(), b.getCustomerId()))
                .orElse(null);
    }

    /** The full payout request for a saved bank (the same conversion initiateSimplePayout does). */
    @Transactional(readOnly = true)
    public PayoutRequest toPayoutRequest(SimplePayoutRequest simpleRequest) {
        PayoutBanks bank = payoutBanksRepo.findByIdAndVerifiedTrue(simpleRequest.getPayoutBankId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bank account not found or not verified: " + simpleRequest.getPayoutBankId()));

        String stateCode = getStateCode(bank.getStateName());
        String bankCode = getBankCode(bank.getBankName());
        String paymentPurpose = simpleRequest.getPaymentPurpose() != null
                ? simpleRequest.getPaymentPurpose()
                : "004"; // Default purpose code

        PayoutRequest fullRequest = new PayoutRequest();
        fullRequest.setInitiatorType(bank.getCustomerType());
        fullRequest.setInitiatorId(bank.getCustomerId());
        fullRequest.setAmount(simpleRequest.getAmount());
        fullRequest.setPaymentMode(simpleRequest.getPaymentMode());
        fullRequest.setPaymentPurpose(paymentPurpose);
        fullRequest.setBeneficiaryBank(bankCode);
        fullRequest.setBeneficiaryBankName(bank.getBankName());
        fullRequest.setBeneficiaryStateName(bank.getStateName());
        fullRequest.setBeneficiaryAccountNumber(bank.getAccountNumber());
        fullRequest.setBeneficiaryIFSC(bank.getIfscCode());
        fullRequest.setBeneficiaryMobileNumber(
                resolveBeneficiaryMobile(bank.getCustomerType(), bank.getCustomerId()));
        fullRequest.setBeneficiaryLocation(stateCode);
        fullRequest.setBeneficiaryName(bank.getHolderName());
        fullRequest.setLat("19.0760");       // Mumbai lat
        fullRequest.setLongitude("72.8777"); // Mumbai long
        fullRequest.setUdf1(simpleRequest.getUdf1());
        fullRequest.setUdf2(simpleRequest.getUdf2());
        fullRequest.setUdf3(simpleRequest.getUdf3());
        return fullRequest;
    }

    /** vendorId: the vendor this payout goes to (routing may have chosen it); tries/waitSeconds: from the routing. */
    private record PreparedV2(Long payoutId, Long attemptId, String merchantRefId, BigDecimal charges,
                              BigDecimal totalDeduction, BigDecimal remainingBalance,
                              Long vendorId, int tries, int waitSeconds) {

        PreparedV2 withAttempt(Long newAttemptId) {
            return new PreparedV2(payoutId, newAttemptId, merchantRefId, charges, totalDeduction, remainingBalance,
                    vendorId, tries, waitSeconds);
        }
    }

    /** The longest pause between two tries, whatever the routing says. */
    private static final int MAX_RETRY_WAIT_SECONDS = 10;

    /**
     * Not @Transactional on purpose: it runs its own short transactions. Call it through the PayoutEngine.
     */
    public PayoutResult initiatePayoutV2(PayoutRequest request, Long vendorId) {
        log.info("Initiating payout (V2) for initiator={} type={} amount={}",
                request.getInitiatorId(), request.getInitiatorType(), request.getAmount());

        PayoutVendorAdapter adapter = adapterFor(vendorId);
        if (adapter == null || attemptRepo == null || txManager == null) {
            return PayoutResult.failed(null, "Payout is not available right now. Please try again later.");
        }

        // ---- Step 1: check, charge, debit, create the payout (pending) and its attempt; COMMIT ----
        final PreparedV2 prepared;
        try {
            prepared = v2Tx().execute(status -> prepareV2(request, vendorId));
        } catch (Exception e) {
            // rolled back: nothing was debited
            log.error("Payout (V2) could not start for ref={}: {}", request.getMerchantRefId(), e.getMessage(), e);
            return PayoutResult.failed(request.getMerchantRefId(), e.getMessage());
        }

        // Trial run only: note which vendor routing would choose. Does not change this payout.
        if (routingTrial != null) {
            routingTrial.recordPayout(request.getInitiatorType(), request.getInitiatorId(), request.getAmount(),
                    prepared.vendorId(), prepared.merchantRefId());
        }

        // ---- Step 2: call the vendor, outside any transaction and without any wallet lock ----
        // A try the vendor clearly did not take (NOT_PROCESSED) is repeated on the same vendor, with a short wait,
        // up to the routing's retry count, always with the same reference. Any other answer is final here.
        PayoutVendorAdapter useAdapter = adapterFor(prepared.vendorId());
        PreparedV2 current = prepared;
        PayoutVendorAdapter.AttemptResult answer;
        int attemptNo = 1;
        while (true) {
            try {
                answer = useAdapter.submit(prepared.vendorId(), request, prepared.charges());
            } catch (Exception e) {
                log.error("Payout (V2): the vendor call broke, outcome unknown | ref={} | error={}",
                        prepared.merchantRefId(), e.getMessage(), e);
                answer = PayoutVendorAdapter.AttemptResult.unknown(e.getMessage());
            }
            if (answer.outcome() != PayoutVendorAdapter.Outcome.NOT_PROCESSED || attemptNo >= prepared.tries()) {
                break;
            }
            try {
                final PreparedV2 closing = current;
                final PayoutVendorAdapter.AttemptResult notTaken = answer;
                final int nextNo = attemptNo + 1;
                log.warn("Payout (V2): try {} not taken by the vendor, trying again | ref={} | message={}",
                        attemptNo, prepared.merchantRefId(), notTaken.message());
                v2Tx().execute(status -> closeTry(closing, notTaken));
                sleepBetweenTries(prepared.waitSeconds());
                Long nextAttemptId = v2Tx().execute(status -> openTry(closing, nextNo));
                current = current.withAttempt(nextAttemptId);
                attemptNo = nextNo;
            } catch (Exception e) {
                // Could not record the next try: stop here. The last answer is applied below, so the payout
                // ends as a refunded failure (the vendor did not take any try).
                log.error("Payout (V2): could not start the next try | ref={} | error={}",
                        prepared.merchantRefId(), e.getMessage(), e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }

        // ---- Step 3: apply the answer in a new short transaction ----
        final PayoutVendorAdapter.AttemptResult finalAnswer = answer;
        final PreparedV2 last = current;
        try {
            return v2Tx().execute(status -> finishV2(last, finalAnswer));
        } catch (Exception e) {
            // The debit is already committed and the payout is PENDING, so nothing is lost; the stuck-pending
            // alert and the admin screens show it. Log what is needed to check it by hand.
            log.error("PAYOUT (V2) NEEDS MANUAL CHECK | ref={} | outcome={} | vendorTxnId={} | error={}",
                    prepared.merchantRefId(), finalAnswer.outcome(), finalAnswer.vendorTxnId(), e.getMessage(), e);
            return processingResult(prepared, request.getAmount());
        }
    }

    /** A try the vendor did not take: record it as failed (the payout itself stays pending for the next try). */
    private Void closeTry(PreparedV2 p, PayoutVendorAdapter.AttemptResult answer) {
        PaymentAttempt attempt = attemptRepo.findById(p.attemptId()).orElse(null);
        if (attempt != null) {
            attempt.setStatus(PaymentAttempt.FAILED);
            attempt.setResponseCode(answer.responseCode());
            attempt.setMessage(answer.message());
            attempt.setCompletedAt(LocalDateTime.now());
            attemptRepo.save(attempt);
        }
        return null;
    }

    /** The next try on the same vendor, with the same reference. */
    private Long openTry(PreparedV2 p, int attemptNo) {
        PaymentAttempt next = new PaymentAttempt();
        next.setSourceType(PaymentAttempt.SOURCE_PAYOUT);
        next.setSourceId(p.payoutId());
        next.setAttemptNo(attemptNo);
        next.setVendorId(p.vendorId());
        next.setVendorReference(p.merchantRefId());
        next.setStatus(PaymentAttempt.CREATED);
        return attemptRepo.save(next).getId();
    }

    private static void sleepBetweenTries(int seconds) throws InterruptedException {
        int s = Math.min(Math.max(seconds, 0), MAX_RETRY_WAIT_SECONDS);
        if (s > 0) {
            Thread.sleep(s * 1000L);
        }
    }

    private PreparedV2 prepareV2(PayoutRequest request, Long vendorId) {
        // The Payout switch (Merchant Settings) must be on, checked before anything moves.
        featureAccess.requirePayoutEnabled(new CustomerRef(request.getInitiatorType(), request.getInitiatorId()));

        request.setBeneficiaryName(sanitizeBeneficiaryName(request.getBeneficiaryName()));

        String blockedKeyword = BeneficiaryNameValidator.findBlockedKeyword(request.getBeneficiaryName());
        if (blockedKeyword != null) {
            log.warn("Payout blocked, possible current account | initiator={} type={} beneficiary={} matchedKeyword={}",
                    request.getInitiatorId(), request.getInitiatorType(),
                    request.getBeneficiaryName(), blockedKeyword);
            throw new IllegalArgumentException(
                    BeneficiaryNameValidator.blockedMessage(request.getBeneficiaryName()));
        }

        validatePayoutSettings(request);

        // Which vendor serves this payout. Without "Use routing for payouts" (the default) it is the fixed
        // vendor, one try, exactly as before. With it, the account's routing decides, and the vendor's limits
        // are checked here, before any money moves (a refusal costs the merchant nothing).
        Long useVendorId = vendorId;
        int tries = 1;
        int waitSeconds = 0;
        if (routingPlanner != null) {
            PayoutRoutingPlanner.Choice choice = routingPlanner.choose(request.getInitiatorType(),
                    request.getInitiatorId(), request.getAmount(), vendorId, id -> adapterFor(id) != null);
            useVendorId = choice.vendorId();
            tries = choice.tries();
            waitSeconds = choice.waitSeconds();
        }

        BigDecimal charges = paymentChargeService.calculateCharges(
                request.getAmount(), request.getPaymentMode(), request.getInitiatorId());
        BigDecimal totalDeduction = request.getAmount().add(charges);

        BigDecimal remainingBalance;
        if ("MERCHANT".equals(request.getInitiatorType())) {
            remainingBalance = validateAndDeductMerchantBalance(request.getInitiatorId(), totalDeduction);
        } else if ("FRANCHISE".equals(request.getInitiatorType())) {
            remainingBalance = validateAndDeductFranchiseBalance(request.getInitiatorId(), totalDeduction);
        } else {
            throw new IllegalArgumentException("Invalid initiator type: " + request.getInitiatorType());
        }

        PayoutTransaction payoutTxn = createPayoutTransaction(request, charges, useVendorId);
        recordInLedger(payoutTxn, remainingBalance);
        request.setMerchantRefId(payoutTxn.getMerchantRefId());

        transactionEventService.recordEvent(TransactionSourceType.PAYOUT, payoutTxn.getId(),
                request.getInitiatorType(), request.getInitiatorId(), totalDeduction,
                TransactionEventStatus.PENDING, "paymentMode=" + request.getPaymentMode());

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setSourceType(PaymentAttempt.SOURCE_PAYOUT);
        attempt.setSourceId(payoutTxn.getId());
        attempt.setAttemptNo(1);
        attempt.setVendorId(useVendorId);
        attempt.setVendorReference(payoutTxn.getMerchantRefId());
        attempt.setStatus(PaymentAttempt.CREATED);
        attempt = attemptRepo.save(attempt);

        return new PreparedV2(payoutTxn.getId(), attempt.getId(), payoutTxn.getMerchantRefId(), charges,
                totalDeduction, remainingBalance, useVendorId, tries, waitSeconds);
    }

    private PayoutResult finishV2(PreparedV2 p, PayoutVendorAdapter.AttemptResult answer) {
        // Locked read: a vendor callback for this payout waits for us, and we wait for it.
        PayoutTransaction txn = payoutTxnRepo.findByIdForUpdate(p.payoutId())
                .orElseThrow(() -> new IllegalStateException("Payout not found: " + p.payoutId()));

        PaymentAttempt attempt = attemptRepo.findById(p.attemptId()).orElse(null);
        if (attempt != null) {
            attempt.setStatus(attemptStatus(answer.outcome()));
            attempt.setVendorTxnId(answer.vendorTxnId());
            attempt.setResponseCode(answer.responseCode());
            attempt.setMessage(answer.message());
            if (answer.outcome() == PayoutVendorAdapter.Outcome.SUCCESS
                    || answer.outcome() == PayoutVendorAdapter.Outcome.FAILED
                    || answer.outcome() == PayoutVendorAdapter.Outcome.NOT_PROCESSED) {
                attempt.setCompletedAt(LocalDateTime.now());
            }
            attemptRepo.save(attempt);
        }

        // Nobody knows if the vendor got it: keep the debit and the pending payout (like the old flow does when
        // the vendor call breaks); the stuck-pending alert and the admin screens show it.
        if (answer.outcome() == PayoutVendorAdapter.Outcome.UNKNOWN) {
            log.error("PAYOUT (V2) OUTCOME UNKNOWN, left pending for a manual check | ref={} | message={}",
                    p.merchantRefId(), answer.message());
            return processingResult(p, txn.getAmount());
        }

        // A callback may already have settled this payout; the immediate answer must never overwrite it.
        if (txn.getStatus() == PayoutTransaction.PayoutStatus.SUCCESS
                || txn.getStatus() == PayoutTransaction.PayoutStatus.FAILED) {
            log.warn("Payout (V2): already {} before the immediate answer ({}) was applied, answer ignored | ref={}",
                    txn.getStatus(), answer.outcome(), p.merchantRefId());
            // the try must agree with the payout's final state, not with the answer that was ignored
            if (attempt != null) {
                attempt.setStatus(txn.getStatus() == PayoutTransaction.PayoutStatus.SUCCESS
                        ? PaymentAttempt.SUCCESS : PaymentAttempt.FAILED);
                attempt.setCompletedAt(LocalDateTime.now());
                attemptRepo.save(attempt);
            }
            PayoutResult settled = new PayoutResult();
            settled.setStatus(txn.getStatus() == PayoutTransaction.PayoutStatus.SUCCESS ? "OK" : "FAILED");
            settled.setMessage(txn.getResponseMessage());
            settled.setTxnId(txn.getVendorTxnId());
            settled.setMerchantRefId(p.merchantRefId());
            settled.setAmount(txn.getAmount());
            settled.setCharges(p.charges());
            settled.setTotalDeducted(p.totalDeduction());
            settled.setRemainingBalance(p.remainingBalance());
            return settled;
        }

        PayoutResult vendorResult = new PayoutResult();
        vendorResult.setStatus(switch (answer.outcome()) {
            case SUCCESS -> "OK";
            case PENDING -> "PENDING";
            default -> "FAILED";
        });
        vendorResult.setMessage(answer.message());
        vendorResult.setTxnId(answer.vendorTxnId());
        vendorResult.setRrn(answer.rrn());
        vendorResult.setVendorCharges(answer.vendorCharges());
        vendorResult.setMerchantRefId(p.merchantRefId());
        vendorResult.setAmount(txn.getAmount());
        vendorResult.setTotalDeducted(p.totalDeduction());

        updatePayoutWithVendorResponse(txn, vendorResult);

        transactionEventService.recordEvent(TransactionSourceType.PAYOUT, txn.getId(),
                txn.getInitiatorType(), txn.getInitiatorId(), p.totalDeduction(),
                mapPayoutStatus(vendorResult.getStatus()), "vendorTxnId=" + answer.vendorTxnId());

        log.info("Payout (V2) answered: ref={} vendorTxnId={} status={}",
                p.merchantRefId(), answer.vendorTxnId(), vendorResult.getStatus());

        // a clear failure: refund now (the refund routine refunds only once)
        if ("FAILED".equals(vendorResult.getStatus())) {
            refundToWallet(txn);
        }

        vendorResult.setCharges(p.charges());
        vendorResult.setRemainingBalance(p.remainingBalance());
        return vendorResult;
    }

    /**
     * A callback (or a late answer) settled the payout, so a try that is still open (PENDING / CREATED / UNKNOWN)
     * is closed with the same result. Runs only after the callback's own transaction has committed, in a
     * transaction of its own, and never throws: a problem here can never undo the payout or its refund.
     */
    private void closeAttemptsAfterCommit(PayoutTransaction txn) {
        if (attemptRepo == null || txManager == null || txn == null || txn.getId() == null) {
            return;
        }
        PayoutTransaction.PayoutStatus s = txn.getStatus();
        if (s != PayoutTransaction.PayoutStatus.SUCCESS && s != PayoutTransaction.PayoutStatus.FAILED) {
            return;
        }
        final Long payoutId = txn.getId();
        final String attemptStatus = s == PayoutTransaction.PayoutStatus.SUCCESS
                ? PaymentAttempt.SUCCESS : PaymentAttempt.FAILED;
        final String message = txn.getResponseMessage();
        final String code = txn.getStatusCode();
        final String vendorTxnId = txn.getVendorTxnId();
        Runnable work = () -> closeAttempts(payoutId, attemptStatus, message, code, vendorTxnId);

        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            work.run();
                        }
                    });
        } else {
            work.run();
        }
    }

    void closeAttempts(Long payoutId, String attemptStatus, String message, String code, String vendorTxnId) {
        try {
            org.springframework.transaction.support.TransactionTemplate template =
                    new org.springframework.transaction.support.TransactionTemplate(txManager);
            template.setPropagationBehavior(
                    org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.execute(status -> {
                for (PaymentAttempt a : attemptRepo.findBySourceTypeAndSourceIdOrderByAttemptNoAsc(
                        PaymentAttempt.SOURCE_PAYOUT, payoutId)) {
                    boolean open = PaymentAttempt.PENDING.equals(a.getStatus())
                            || PaymentAttempt.CREATED.equals(a.getStatus())
                            || PaymentAttempt.UNKNOWN.equals(a.getStatus());
                    if (!open) {
                        continue;
                    }
                    a.setStatus(attemptStatus);
                    a.setCompletedAt(LocalDateTime.now());
                    if (message != null && !message.isBlank()) {
                        a.setMessage(message);
                    }
                    if (code != null) {
                        a.setResponseCode(code);
                    }
                    if (a.getVendorTxnId() == null && vendorTxnId != null) {
                        a.setVendorTxnId(vendorTxnId);
                    }
                    attemptRepo.save(a);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Payout attempts could not be closed for payout {} (the payout itself is fine): {}",
                    payoutId, e.toString());
        }
    }

    private PayoutResult processingResult(PreparedV2 p, BigDecimal amount) {
        PayoutResult r = PayoutResult.pending(null, p.merchantRefId(), amount, p.charges(), null, p.remainingBalance());
        r.setMessage("Your payout is being processed. Its status will be updated shortly.");
        return r;
    }

    private static String attemptStatus(PayoutVendorAdapter.Outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> PaymentAttempt.SUCCESS;
            case PENDING -> PaymentAttempt.PENDING;
            case FAILED, NOT_PROCESSED -> PaymentAttempt.FAILED;
            case UNKNOWN -> PaymentAttempt.UNKNOWN;
        };
    }

    // ==================== VALIDATION METHODS ====================

    // Transaction Monitoring: maps the vendor's status vocabulary onto the
    // monitoring event vocabulary.
    private TransactionEventStatus mapPayoutStatus(String vendorStatus) {
        if ("OK".equalsIgnoreCase(vendorStatus)) return TransactionEventStatus.SUCCESS;
        if ("FAILED".equalsIgnoreCase(vendorStatus)) return TransactionEventStatus.FAILED;
        return TransactionEventStatus.PENDING;
    }

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

    // Leading honorific(s) only, and only when followed by "." or whitespace —
    // so names that merely start with the same letters (Mrinal, Drishti,
    // Missy) are untouched. Deliberately limited to unambiguous titles:
    // "Sri"/"Kumari"/"Master" are also real given names/surnames and are left alone.
    private static final java.util.regex.Pattern NAME_TITLE_PREFIX = java.util.regex.Pattern.compile(
            "^\\s*(?:(?:mr|mrs|ms|miss|mx|dr|prof|shri|smt)(?:\\.\\s*|\\s+))+",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    static String sanitizeBeneficiaryName(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = NAME_TITLE_PREFIX.matcher(name).replaceFirst("")
                .replaceAll("\\s+", " ")
                .trim();
        // Never send an empty name just because the whole value was a title.
        return cleaned.isEmpty() ? name.trim() : cleaned;
    }

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
        txn.setBeneficiaryBankName(request.getBeneficiaryBankName());
        txn.setBeneficiaryStateName(request.getBeneficiaryStateName());
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

        // The new flow records each try on the vendor; close the open one now that the payout is final.
        closeAttemptsAfterCommit(payoutTxn);

        // Transaction Monitoring: record the callback-driven status change
        // (async path — the payout came back PENDING synchronously earlier
        // and is only now resolving to SUCCESS/FAILED).
        transactionEventService.recordEvent(TransactionSourceType.PAYOUT, payoutTxn.getId(),
                payoutTxn.getInitiatorType(), payoutTxn.getInitiatorId(), payoutTxn.getTotalDeducted(),
                mapPayoutStatus(payoutTxn.getStatus() == PayoutTransaction.PayoutStatus.SUCCESS ? "OK"
                        : payoutTxn.getStatus() == PayoutTransaction.PayoutStatus.FAILED ? "FAILED" : "PENDING"),
                "callback vendorTxnId=" + callback.getTxnId());

        // A late callback that finally resolves a payout means it's no longer
        // "stuck" — auto-close any open Stuck-Pending alert about it. Only
        // fires when the status actually became final (not another PENDING).
        if (payoutTxn.getStatus() == PayoutTransaction.PayoutStatus.SUCCESS
                || payoutTxn.getStatus() == PayoutTransaction.PayoutStatus.FAILED) {
            alertService.autoResolveStuckPending(TransactionSourceType.PAYOUT, payoutTxn.getId(),
                    "Auto-resolved: late vendor callback resolved this payout to "
                            + payoutTxn.getStatus() + " (vendorTxnId=" + callback.getTxnId() + ")");
        }
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
        String merchantRefId = "MER" + mtd.getTransactionId();
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
        String merchantRefId = "FRA" + ftd.getTransactionId();
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

    public void refundPayoutForManualFailure(PayoutTransaction payoutTransaction) {

        log.info(
                "Manual refund started. payoutId={}, merchantRefId={}",
                payoutTransaction.getId(),
                payoutTransaction.getMerchantRefId()
        );

        refundToWallet(payoutTransaction);

        log.info(
                "Manual refund completed. payoutId={}, merchantRefId={}",
                payoutTransaction.getId(),
                payoutTransaction.getMerchantRefId()
        );
    }

    private void refundToWallet(PayoutTransaction payoutTxn) {
        // Refund only once: if this payout already has its refund ledger row, never credit the wallet again.
        boolean alreadyRefunded = "MERCHANT".equals(payoutTxn.getInitiatorType())
                ? payoutTxn.getLedgerRefundMerchantTxnId() != null
                : payoutTxn.getLedgerRefundFranchiseTxnId() != null;
        if (alreadyRefunded) {
            log.warn("Refund skipped, this payout was already refunded: ref={}", payoutTxn.getMerchantRefId());
            return;
        }

        log.info("Refunding failed payout: ref={} amount={}",
                payoutTxn.getMerchantRefId(), payoutTxn.getTotalDeducted());

        if ("MERCHANT".equals(payoutTxn.getInitiatorType())) {
            refundMerchantWallet(payoutTxn);
        } else {
            refundFranchiseWallet(payoutTxn);
        }

        transactionEventService.recordEvent(TransactionSourceType.PAYOUT_REFUND, payoutTxn.getId(),
                payoutTxn.getInitiatorType(), payoutTxn.getInitiatorId(), payoutTxn.getTotalDeducted(),
                TransactionEventStatus.SUCCESS, "refund for failed payout " + payoutTxn.getMerchantRefId());
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
        //refundEntry.setVendorTransactionId(payoutTxn.getVendorTxnId());
        refundEntry.setRemarks(
                "Refund for failed payout - " + payoutTxn.getResponseMessage() +
                        " | Beneficiary A/C: " + payoutTxn.getBeneficiaryAccountNumber());

        MerchantTransactionDetails savedRefund = merchantTxnRepo.save(refundEntry);

        // keep which payout this refund belongs to (the report can then tell its vendor)
        payoutTxn.setLedgerRefundMerchantTxnId(savedRefund.getTransactionId());
        payoutTxnRepo.save(payoutTxn);

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
        FranchiseTransactionDetails savedRefund = franchiseTxnRepo.save(refundEntry);

        payoutTxn.setLedgerRefundFranchiseTxnId(savedRefund.getTransactionId());
        payoutTxnRepo.save(payoutTxn);

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
            Long merchantId,
            int page,
            int size
    ) {
        return getTransactionReport(startDate, endDate, service, merchantId, null, page, size);
    }

    /** Same, narrowed to one payout vendor (vendorId; null = all vendors). */
    public PayoutTransactionReportResponse getTransactionReport(
            LocalDate startDate,
            LocalDate endDate,
            String service,
            Long merchantId,
            Long vendorId,
            int page,
            int size
    ) {
        log.info(
                "Fetching payout transaction report | startDate={}, endDate={}, " +
                        "service={}, merchantId={}, vendorId={}, page={}, size={}",
                startDate, endDate, service, merchantId, vendorId, page, size);

        // Date handling
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

        // Service handling. "FAILED" isn't a service type (PAYOUT/PAYOUT_REFUND
        // are) — it's a status filter layered on top, so a failed payout and a
        // failed refund attempt both show up when it's selected.
        List<String> services;
        String tranStatus = null;
        if (service == null || "BOTH".equalsIgnoreCase(service)) {
            services = List.of("PAYOUT", "PAYOUT_REFUND");
        } else if ("FAILED".equalsIgnoreCase(service)) {
            services = List.of("PAYOUT", "PAYOUT_REFUND");
            tranStatus = "FAILED";
        } else if ("PENDING".equalsIgnoreCase(service)) {
            // payouts still waiting for the bank's answer
            services = List.of("PAYOUT", "PAYOUT_REFUND");
            tranStatus = "PENDING";
        } else {
            services = List.of(service);
        }

        // DateTime conversion (CORRECT & SAFE)
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay();

        Pageable pageable = PageRequest.of(page, size);

        // Fetch data
        Page<PayoutTransactionReportDTO> transactions =
                merchantTransDetRepo.fetchTransactions(from, to, services, tranStatus, merchantId, vendorId, pageable);

        // Fetch counts
        PayoutServiceCountDTO counts =
                merchantTransDetRepo.fetchServiceCounts(from, to, services, merchantId, vendorId);

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

    /** Refuses an amount the vendor would refuse anyway, so nothing is debited and refunded for nothing. */
    void validateAmountLimits(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Payout amount is required.");
        }
        if (amount.compareTo(payoutMinAmount) < 0 || amount.compareTo(payoutMaxAmount) > 0) {
            throw new IllegalArgumentException("Payout amount must be between Rs "
                    + payoutMinAmount.stripTrailingZeros().toPlainString() + " and Rs "
                    + payoutMaxAmount.stripTrailingZeros().toPlainString() + ".");
        }
    }

    private void validatePayoutSettings(
            PayoutRequest request) {

        // Before any wallet is read or touched.
        validateAmountLimits(request.getAmount());

        if ("MERCHANT".equalsIgnoreCase(request.getInitiatorType())) {

            Merchant merchant = merchantRepo
                    .findById(request.getInitiatorId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Merchant not found."));

            MerchantWallet wallet = merchantWalletRepo
                    .findByMerchantId(
                            request.getInitiatorId())
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Merchant wallet not found."));

            // Check payout enabled
            if (!Boolean.TRUE.equals(
                    merchant.getPayout())) {

                log.warn(
                        "Payout disabled for merchantId={}",
                        merchant.getId());

                throw new RuntimeException(
                        "Payout service is disabled for this merchant.");
            }

            BigDecimal available =
                    wallet.getAvailableBalance();

            BigDecimal lien =
                    wallet.getCutOfAmount();

            BigDecimal remaining =
                    available.subtract(request.getAmount());

            if (remaining.compareTo(lien) < 0) {

                log.warn(
                        "Lien validation failed. MerchantId={}, Available={}, Lien={}, Requested={}",
                        merchant.getId(),
                        available,
                        lien,
                        request.getAmount());

                throw new RuntimeException(
                        "Insufficient withdrawable balance. Lien amount must be maintained.");
            }

        } else if ("FRANCHISE".equalsIgnoreCase(request.getInitiatorType())) {

            // Franchise logic if required

        } else {

            throw new RuntimeException(
                    "Invalid initiator type.");
        }
    }
}
