package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.*;
import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Exception.CreditCardBillException;
import com.project2.ism.Model.CreditCard.CreditCardApiLog;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction.Status;
import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.FranchiseWallet;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.MerchantWallet;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.CreditCardBillTransactionRepository;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import com.project2.ism.Repository.FranchiseWalletRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.Repository.MerchantWalletRepository;
import com.project2.ism.Service.Monitoring.TransactionEventService;
import com.project2.ism.Service.SuivrePayClient.CallContext;
import com.project2.ism.Service.SuivrePayClient.VendorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Credit card bill payment through SuivrePay, for merchants and franchises.
 *
 * Flow: list billers -> fetch bill (stored here as FETCHED) -> pay.
 *
 * Who may use it: the account's "credit card bill payment" switch (set by admin) must be on,
 * otherwise billers / fetch bill / pay are refused, even if the menu is visible.
 *
 * Paying is done in three separate steps on purpose:
 *   1. (transaction)  lock the bill row + wallet, deduct, write ledger, row = PENDING
 *   2. (no transaction) call SuivrePay
 *   3. (transaction)  SUCCESS -> mark done | definite failure -> refund | unknown -> keep PENDING
 * The debit is committed BEFORE the vendor call, so a crash or restart can never
 * leave a paid bill with no record; and the wallet row is not locked while we
 * wait for the vendor.
 *
 * "Unknown" (timeout, 5xx, unreadable answer) is never auto-refunded: the bill
 * may really have been paid, and refunding it would pay the customer twice.
 * Those stay PENDING for the customer to raise a complaint and for admin review.
 *
 * A merchant pays from the merchant wallet / merchant ledger, a franchise from the franchise
 * wallet / franchise ledger. Merchant and franchise ids overlap, so everything is keyed by
 * (type, id) - see CustomerRef.
 */
@Service
public class CreditCardBillService {

    private static final Logger log = LoggerFactory.getLogger(CreditCardBillService.class);

    private static final String PARAM_CARD_LAST4 = "Last 4 digits of Primary Credit Card Number";
    private static final String PARAM_MOBILE = "Registered Mobile Number";

    private static final BigDecimal PAN_MANDATORY_FROM = new BigDecimal("50000");
    private static final long BILL_VALID_MINUTES = 30;

    private static final Pattern LAST4 = Pattern.compile("^\\d{4}$");
    private static final Pattern MOBILE = Pattern.compile("^\\d{10}$");
    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    // HTTP statuses where SuivrePay clearly refused the request (nothing was paid).
    private static final Set<Integer> DEFINITE_FAILURE_STATUSES = Set.of(400, 401, 402, 403, 404, 409, 422);

    // SuivrePay's own wording for a failed payment that it has already refunded.
    private static final Pattern PAYMENT_FAILED_REFUNDED =
            Pattern.compile("payment\\s+failed.*refund", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final String GENERIC_UNAVAILABLE =
            "Credit card bill payment is temporarily unavailable. Please try again later.";
    private static final String PENDING_MESSAGE =
            "Your payment is being confirmed. Please check Recent Payments in a few minutes. "
                    + "If the status does not update, raise a complaint for this payment.";

    // Bill APIs: relative to the Base URL in Payment Vendors Credentials
    // (https://suivrepay.com/apipanel/billavenue), no ".php".
    @Value("${suivrepay.path.billers:/fetch_creditCard_billers}")
    private String billersPath;
    @Value("${suivrepay.path.fetch-bill:/fetch_cc_bill}")
    private String fetchBillPath;
    @Value("${suivrepay.path.pay-bill:/pay_cc_bill}")
    private String payBillPath;
    // Complaint APIs live in a different SuivrePay folder, so they are full URLs.
    @Value("${suivrepay.path.raise-complaint:https://suivrepay.com/apipanel/apayout/raise_complaint.php}")
    private String raiseComplaintPath;
    @Value("${suivrepay.path.complaint-status:https://suivrepay.com/apipanel/apayout/raised_complaint_status.php}")
    private String complaintStatusPath;
    // Credit card bills run on the BBPS network; SuivrePay's complaint form has no separate
    // "Credit Card" service, so these complaints are filed under BBPS.
    @Value("${suivrepay.complaint.service-name:BBPS}")
    private String complaintServiceName;

    @Value("${suivrepay.duplicate-window-seconds:120}")
    private long duplicateWindowSeconds;

    @Value("${suivrepay.fetch-reuse-seconds:60}")
    private long fetchReuseSeconds;

    // Our service charge comes from Payment Charges (admin screen). The charge is set on a payment
    // MODE; this is the list of mode codes that mean "credit card bill payment". The first code that
    // exists is used. Add the code here (comma separated) if the mode was created with another name.
    @Value("${suivrepay.charge-mode-codes:CCBP,CREDIT_CARD,CREDIT_CARD_BILL,CREDIT_CARD_BILL_PAYMENT,CC_BILL,CCBILL,CREDIT CARD,CREDITCARD}")
    private String chargeModeCodes;

    // true (default): if no charge is configured, the payment is refused instead of silently
    // charging nothing. false: fall back to a zero charge.
    @Value("${suivrepay.charge-required:true}")
    private boolean chargeRequired;

    private final SuivrePayClient client;
    private final CreditCardBillTransactionRepository repository;
    private final MerchantWalletRepository merchantWalletRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantTransDetRepository merchantTransDetRepository;
    private final PaymentChargeService paymentChargeService;
    private final TransactionEventService transactionEventService;
    private final TransactionTemplate tx;
    private final FranchiseWalletRepository franchiseWalletRepository;
    private final FranchiseRepository franchiseRepository;
    private final FranchiseTransDetRepository franchiseTransDetRepository;
    private final FeatureAccessService featureAccess;

    public CreditCardBillService(SuivrePayClient client,
                                 CreditCardBillTransactionRepository repository,
                                 MerchantWalletRepository merchantWalletRepository,
                                 MerchantRepository merchantRepository,
                                 MerchantTransDetRepository merchantTransDetRepository,
                                 PaymentChargeService paymentChargeService,
                                 TransactionEventService transactionEventService,
                                 PlatformTransactionManager transactionManager,
                                 FranchiseWalletRepository franchiseWalletRepository,
                                 FranchiseRepository franchiseRepository,
                                 FranchiseTransDetRepository franchiseTransDetRepository,
                                 FeatureAccessService featureAccess) {
        this.client = client;
        this.repository = repository;
        this.merchantWalletRepository = merchantWalletRepository;
        this.merchantRepository = merchantRepository;
        this.merchantTransDetRepository = merchantTransDetRepository;
        this.paymentChargeService = paymentChargeService;
        this.transactionEventService = transactionEventService;
        this.tx = new TransactionTemplate(transactionManager);
        this.franchiseWalletRepository = franchiseWalletRepository;
        this.franchiseRepository = franchiseRepository;
        this.franchiseTransDetRepository = franchiseTransDetRepository;
        this.featureAccess = featureAccess;
    }

    // =====================================================================
    // 1. Billers
    // =====================================================================

    public List<Biller> getBillers(CustomerRef who) {
        featureAccess.requireCreditCardEnabled(who);

        log.info("Credit card billers requested | {}", who.label());
        VendorResponse response = client.post(billersPath, null,
                CallContext.of(CreditCardApiLog.FETCH_BILLERS, who, null, null, null, null));

        if (!isSuccess(response)) {
            throw vendorProblem(response, "billers");
        }

        List<Biller> billers = new ArrayList<>();
        JsonNode data = response.body().path("data");
        if (data.isArray()) {
            for (JsonNode b : data) {
                String id = b.path("biller_id").asText("");
                if (!id.isBlank()) {
                    billers.add(new Biller(id, b.path("biller_name").asText(id)));
                }
            }
        }
        log.info("Credit card billers loaded | count={}", billers.size());
        return billers;
    }

    // =====================================================================
    // 2. Fetch bill
    // =====================================================================

    public FetchedBill fetchBill(CustomerRef who, FetchBillRequest request) {
        featureAccess.requireCreditCardEnabled(who);

        if (request == null || isBlank(request.billerId())) {
            throw new IllegalArgumentException("Please select a biller.");
        }
        String billerId = request.billerId().trim();
        if (billerId.length() > 30) {
            throw new IllegalArgumentException("Invalid biller.");
        }
        String last4 = request.cardLast4() == null ? "" : request.cardLast4().trim();
        if (!LAST4.matcher(last4).matches()) {
            throw new IllegalArgumentException("Enter the last 4 digits of the credit card.");
        }
        String mobile = request.mobileNumber() == null ? "" : request.mobileNumber().trim();
        if (!MOBILE.matcher(mobile).matches()) {
            throw new IllegalArgumentException("Enter the 10-digit registered mobile number.");
        }

        log.info("Credit card fetch-bill started | {} | billerId={} | card=****{} | mobile={}",
                who.label(), billerId, last4, maskMobile(mobile));

        // Same card fetched again within a minute (double click, "let me check once more"):
        // show the bill we already have instead of asking the biller again. Billers throttle
        // repeated lookups ("Too many request for that Biller"), so this keeps them calm.
        List<CreditCardBillTransaction> recent = repository.findRecentFetched(
                who.type(), who.id(), billerId, last4, mobile, Status.FETCHED,
                LocalDateTime.now().minusSeconds(fetchReuseSeconds), PageRequest.of(0, 1));
        if (!recent.isEmpty()) {
            CreditCardBillTransaction existing = recent.get(0);
            log.info("Credit card fetch-bill reused a bill fetched moments ago | {} | requestId={}",
                    who.label(), existing.getRequestId());
            return toFetchedBill(existing, calculateCharges(existing.getBillAmount(), who));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("MID", client.mid());
        body.put("biller_id", billerId);
        body.put("mobile", mobile); // SuivrePay's sample request also sends the mobile at top level
        body.put("input_params", List.of(
                Map.of("name", PARAM_CARD_LAST4, "value", last4),
                Map.of("name", PARAM_MOBILE, "value", mobile)));

        VendorResponse response = client.post(fetchBillPath, body,
                CallContext.of(CreditCardApiLog.FETCH_BILL, who, billerId, last4, null, null));

        if (!isSuccess(response)) {
            throw vendorProblem(response, "fetch-bill");
        }

        JsonNode data = response.body().path("data");
        String requestId = data.path("request_id").asText("");
        BigDecimal amount = parseAmount(data.path("amount"));

        if (requestId.isBlank() || amount == null) {
            log.error("SuivrePay fetch-bill returned an unusable response | {}", who.label());
            throw new CreditCardBillException(HttpStatus.BAD_GATEWAY,
                    "Could not read the bill details. Please try again.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CreditCardBillException(HttpStatus.BAD_REQUEST,
                    "There is no outstanding amount to pay on this card.");
        }

        CreditCardBillTransaction row = new CreditCardBillTransaction();
        row.setCustomerType(who.type());
        row.setMerchantId(who.id());
        row.setRequestId(requestId);
        row.setBillerId(billerId);
        row.setBillerName(data.path("biller_name").asText(null));
        row.setCardLast4(last4);
        row.setCustomerMobile(mobile);
        row.setCustomerName(data.path("customer_name").asText(null));
        row.setBillNumber(data.path("bill_number").asText(null));
        row.setBillDate(data.path("bill_date").asText(null));
        row.setDueDate(data.path("due_date").asText(null));
        row.setBillAmount(amount);
        row.setStatus(Status.FETCHED);
        row.setFetchedAt(LocalDateTime.now());

        try {
            repository.save(row);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Duplicate request_id on fetch-bill | {}", who.label());
            throw new CreditCardBillException(HttpStatus.CONFLICT,
                    "This bill was already fetched. Please fetch it again.");
        }

        BigDecimal charges = calculateCharges(amount, who);

        log.info("Credit card fetch-bill done | {} | requestId={} | billerId={} | amount={} | charges={}",
                who.label(), requestId, billerId, amount, charges);

        return toFetchedBill(row, charges);
    }

    private FetchedBill toFetchedBill(CreditCardBillTransaction row, BigDecimal charges) {
        BigDecimal amount = row.getBillAmount();
        return new FetchedBill(
                row.getRequestId(),
                row.getBillerId(),
                row.getBillerName(),
                row.getCustomerName(),
                row.getBillNumber(),
                row.getBillDate(),
                row.getDueDate(),
                amount,
                charges,
                amount.add(charges),
                amount.compareTo(PAN_MANDATORY_FROM) >= 0);
    }

    // =====================================================================
    // 3. Pay bill
    // =====================================================================

    private record Prepared(Long id, String requestId, BigDecimal amount, String pan,
                            BigDecimal charges, BigDecimal totalDeducted,
                            String cardLast4, String mobile, String billerId) {
    }

    private enum Outcome {SUCCESS, FAILED, UNKNOWN}

    public PaymentResult payBill(CustomerRef who, PayBillRequest request) {
        // Before anything else: the switch must be on. Nothing is debited if it is off.
        featureAccess.requireCreditCardEnabled(who);

        if (request == null || isBlank(request.requestId())) {
            throw new IllegalArgumentException("Please fetch the bill first.");
        }
        String requestId = request.requestId().trim();

        String pan = request.customerPan() == null ? null : request.customerPan().trim().toUpperCase();
        if (pan != null && pan.isEmpty()) {
            pan = null;
        }
        if (pan != null && !PAN.matcher(pan).matches()) {
            throw new IllegalArgumentException("Enter a valid PAN (for example ABCDE1234F).");
        }

        log.info("Credit card pay requested | {} | requestId={} | panGiven={}", who.label(), requestId, pan != null);

        // Fail before any money moves if SuivrePay is not configured.
        client.ensureConfigured();
        String mid = client.mid();

        // ---- Step 1: debit + mark PENDING (committed before we call the vendor) ----
        final String panFinal = pan;
        Prepared prepared = tx.execute(status -> prepareDebit(who, requestId, panFinal));

        // ---- Step 2: call SuivrePay, outside any transaction ----
        VendorResponse response = null;
        Outcome outcome;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("MID", mid);
            body.put("amount", prepared.amount());
            body.put("request_id", prepared.requestId());
            if (prepared.pan() != null) {
                body.put("customer_pan", prepared.pan());
            }
            // SuivrePay reads the stored bill by request_id; its sample request also repeats the
            // card details, which it only uses as a fallback. Sending them costs nothing.
            if (!isBlank(prepared.cardLast4()) && !isBlank(prepared.mobile())) {
                body.put("input_params", List.of(
                        Map.of("name", PARAM_CARD_LAST4, "value", prepared.cardLast4()),
                        Map.of("name", PARAM_MOBILE, "value", prepared.mobile())));
            }
            response = client.post(payBillPath, body,
                    CallContext.of(CreditCardApiLog.PAY_BILL, who, prepared.billerId(), prepared.cardLast4(),
                            prepared.requestId(), prepared.amount()));
            outcome = classify(response);
            log.info("Credit card pay: SuivrePay answered | {} | requestId={} | httpStatus={} | vendorStatus={} | vendorMessage={} | decision={}",
                    who.label(), requestId, response.httpStatus(),
                    response.body() != null ? response.body().path("status").asText("") : "(not JSON)",
                    response.body() != null ? response.body().path("message").asText("") : "",
                    outcome);
        } catch (SuivrePayClient.VendorUnreachableException e) {
            log.error("Credit card pay: no answer from SuivrePay, outcome unknown | {} | requestId={}",
                    who.label(), requestId);
            outcome = Outcome.UNKNOWN;
        } catch (Exception e) {
            log.error("Credit card pay: unexpected error after debit, outcome unknown | {} | requestId={} | error={}",
                    who.label(), requestId, e.getMessage(), e);
            outcome = Outcome.UNKNOWN;
        }

        // ---- Step 3: record the result ----
        final VendorResponse vendorResponse = response;
        final Outcome finalOutcome = outcome;
        try {
            return tx.execute(status -> finalizePayment(who, prepared, vendorResponse, finalOutcome));
        } catch (Exception e) {
            // The debit is already committed and the row is PENDING, so nothing is lost;
            // log everything needed to reconcile by hand.
            log.error("CREDIT CARD PAYMENT NEEDS MANUAL CHECK | {} | requestId={} | outcome={} | httpStatus={} | raw={} | error={}",
                    who.label(), requestId, finalOutcome,
                    vendorResponse != null ? vendorResponse.httpStatus() : null,
                    vendorResponse != null ? vendorResponse.raw() : null,
                    e.getMessage(), e);
            return new PaymentResult(prepared.id(), requestId, Status.PENDING.name(), PENDING_MESSAGE,
                    prepared.amount(), prepared.charges(), prepared.totalDeducted(), null, null);
        }
    }

    private Prepared prepareDebit(CustomerRef who, String requestId, String pan) {

        CreditCardBillTransaction t = repository.findByRequestIdForUpdate(requestId, who.type(), who.id())
                .orElseThrow(() -> new IllegalArgumentException("Bill not found. Please fetch the bill again."));

        if (t.getStatus() != Status.FETCHED) {
            throw new IllegalArgumentException("This bill has already been processed.");
        }

        if (t.getFetchedAt() == null
                || t.getFetchedAt().plusMinutes(BILL_VALID_MINUTES).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This bill has expired. Please fetch the bill again.");
        }

        BigDecimal amount = t.getBillAmount() == null ? null : t.getBillAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid bill amount. Please fetch the bill again.");
        }

        if (amount.compareTo(PAN_MANDATORY_FROM) >= 0 && pan == null) {
            throw new IllegalArgumentException("PAN is mandatory for bill payments of Rs. 50,000 or more.");
        }

        // Same payment twice (double click, or fetching again and paying again): take the
        // wallet lock FIRST so two simultaneous requests for this account are handled one
        // after the other, then refuse if an identical payment was made a moment ago.
        lockWallet(who);
        rejectIfRecentDuplicate(who, t, amount);

        BigDecimal charges = calculateCharges(amount, who);
        BigDecimal total = amount.add(charges);

        BigDecimal balanceAfterDebit = deductBalance(who, total);

        t.setCharges(charges);
        t.setTotalDeducted(total);
        t.setCustomerPan(pan);
        t.setStatus(Status.PENDING);
        t.setPaidAt(LocalDateTime.now());
        repository.save(t);

        Long ledgerId = recordDebitLedger(who, total, balanceAfterDebit, t);
        t.setLedgerMerchantTxnId(ledgerId);
        repository.save(t);

        safeEvent(TransactionSourceType.CREDIT_CARD, t.getId(), who, total,
                TransactionEventStatus.PENDING, "billerId=" + t.getBillerId());

        log.info("Credit card payment started | {} | requestId={} | amount={} | charges={}",
                who.label(), requestId, amount, charges);

        return new Prepared(t.getId(), requestId, amount, pan, charges, total,
                t.getCardLast4(), t.getCustomerMobile(), t.getBillerId());
    }

    /**
     * Blocks paying the same amount to the same card (same biller, last 4 digits and mobile)
     * again within the duplicate window. Failed (refunded) payments do not count, so a
     * genuine retry after a failure is never blocked.
     */
    private void rejectIfRecentDuplicate(CustomerRef who, CreditCardBillTransaction t, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();

        List<CreditCardBillTransaction> recent = repository.findRecentSimilarPayments(
                who.type(), who.id(), t.getBillerId(), t.getCardLast4(), t.getCustomerMobile(), amount,
                List.of(Status.PENDING, Status.SUCCESS), now.minusSeconds(duplicateWindowSeconds), t.getId());

        if (recent.isEmpty()) {
            return;
        }

        LocalDateTime earliestRetry = recent.get(0).getPaidAt().plusSeconds(duplicateWindowSeconds);
        long waitSeconds = Math.max(1, java.time.Duration.between(now, earliestRetry).getSeconds());

        log.warn("Duplicate credit card payment blocked | {} | billerId={} | card=****{} | amount={} | waitSeconds={}",
                who.label(), t.getBillerId(), t.getCardLast4(), amount, waitSeconds);

        throw new IllegalArgumentException("A payment of Rs. " + amount.toPlainString()
                + " to this card was just made. To avoid paying twice, please wait " + waitSeconds
                + " seconds before paying the same amount again, and check Recent Payments.");
    }

    private Outcome classify(VendorResponse r) {
        if (r == null) {
            return Outcome.UNKNOWN;
        }
        int http = r.httpStatus();
        JsonNode body = r.body();
        String status = body == null ? "" : body.path("status").asText("");

        // The docs list "Payment failed. Rs.X refunded. <reason>" (HTTP 500 or response_code 001):
        // SuivrePay itself says the payment failed AND the money went back, so it is a definite
        // failure even though the HTTP status is 5xx.
        if (body != null && PAYMENT_FAILED_REFUNDED.matcher(body.path("message").asText("")).find()) {
            return Outcome.FAILED;
        }

        if (http >= 200 && http < 300 && body != null) {
            if ("success".equalsIgnoreCase(status)) {
                String code = body.path("response_code").asText("000");
                return "000".equals(code) ? Outcome.SUCCESS : Outcome.UNKNOWN;
            }
            if ("failed".equalsIgnoreCase(status) || "failure".equalsIgnoreCase(status)
                    || "error".equalsIgnoreCase(status)) {
                return Outcome.FAILED;
            }
            return Outcome.UNKNOWN;
        }

        if (DEFINITE_FAILURE_STATUSES.contains(http) && body != null) {
            return Outcome.FAILED;
        }

        return Outcome.UNKNOWN;
    }

    private PaymentResult finalizePayment(CustomerRef who, Prepared p, VendorResponse r, Outcome outcome) {

        CreditCardBillTransaction t = repository.findByIdForUpdate(p.id())
                .orElseThrow(() -> new IllegalStateException("Credit card transaction missing: " + p.id()));

        if (t.getStatus() != Status.PENDING) {
            log.warn("Credit card payment already finalised | id={} | status={}", t.getId(), t.getStatus());
            return toResult(t);
        }

        if (r != null) {
            t.setRawPayResponse(r.raw());
            if (r.body() != null) {
                t.setResponseCode(truncate(r.body().path("response_code").asText(null), 20));
            }
        }

        switch (outcome) {
            case SUCCESS -> {
                JsonNode data = r.body().path("data");
                t.setVendorTxnId(truncate(data.path("txn_id").asText(null), 100));
                t.setWalletTxnId(truncate(data.path("wallet_txn_id").asText(null), 100));
                t.setApiRefNumber(truncate(data.path("api_ref_number").asText(null), 100));
                t.setStatus(Status.SUCCESS);
                t.setResponseMessage("Credit card bill payment successful");
                t.setCompletedAt(LocalDateTime.now());
                repository.save(t);

                updateLedger(who, t.getLedgerMerchantTxnId(), "SUCCESS", null,
                        t.getWalletTxnId() != null ? t.getWalletTxnId() : t.getVendorTxnId());

                safeEvent(TransactionSourceType.CREDIT_CARD, t.getId(), who, t.getTotalDeducted(),
                        TransactionEventStatus.SUCCESS, "walletTxnId=" + t.getWalletTxnId());

                log.info("Credit card payment SUCCESS | {} | requestId={} | walletTxnId={}",
                        who.label(), t.getRequestId(), t.getWalletTxnId());
            }
            case FAILED -> {
                String vendorMessage = r.body() != null ? r.body().path("message").asText("") : "";
                String userMessage = userMessage(r.httpStatus(), vendorMessage);

                t.setStatus(Status.FAILED);
                t.setResponseMessage(truncate(userMessage, 500));
                t.setCompletedAt(LocalDateTime.now());
                repository.save(t);

                updateLedger(who, t.getLedgerMerchantTxnId(), "FAILED", userMessage, null);
                refundWallet(who, t.getTotalDeducted(), "Credit card bill payment failed - " + userMessage);

                safeEvent(TransactionSourceType.CREDIT_CARD, t.getId(), who, t.getTotalDeducted(),
                        TransactionEventStatus.FAILED, userMessage);
                safeEvent(TransactionSourceType.CREDIT_CARD_REFUND, t.getId(), who, t.getTotalDeducted(),
                        TransactionEventStatus.SUCCESS, "refund for failed credit card payment " + t.getRequestId());

                log.warn("Credit card payment FAILED and refunded | {} | requestId={} | httpStatus={} | vendorMessage={}",
                        who.label(), t.getRequestId(), r.httpStatus(), vendorMessage);
            }
            default -> {
                // UNKNOWN: keep the money deducted and the row PENDING.
                t.setResponseMessage(PENDING_MESSAGE);
                repository.save(t);

                safeEvent(TransactionSourceType.CREDIT_CARD, t.getId(), who, t.getTotalDeducted(),
                        TransactionEventStatus.PENDING, "outcome unknown, awaiting confirmation");

                log.error("Credit card payment OUTCOME UNKNOWN, left PENDING | {} | requestId={} | httpStatus={}",
                        who.label(), t.getRequestId(), r != null ? r.httpStatus() : null);
            }
        }

        return toResult(t);
    }

    // =====================================================================
    // 4. History + complaints (not blocked by the switch: past payments stay visible)
    // =====================================================================

    public List<TransactionView> getHistory(CustomerRef who) {
        return repository.findHistory(who.type(), who.id(), Status.FETCHED, PageRequest.of(0, 100))
                .stream()
                .map(this::toView)
                .toList();
    }

    public ComplaintStatus raiseComplaint(CustomerRef who, Long id, ComplaintRequest request) {

        String description = request == null || request.description() == null ? "" : request.description().trim();
        if (description.length() < 10 || description.length() > 500) {
            throw new IllegalArgumentException("Please describe the issue in 10 to 500 characters.");
        }

        CreditCardBillTransaction t = repository.findOwned(id, who.type(), who.id())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found."));

        if (t.getStatus() == Status.FETCHED) {
            throw new IllegalArgumentException("A complaint can only be raised for a payment you have made.");
        }
        if (t.getComplaintTicketId() != null) {
            throw new IllegalArgumentException("A complaint is already raised for this payment. Ticket: "
                    + t.getComplaintTicketId());
        }

        String reference = !isBlank(t.getWalletTxnId()) ? t.getWalletTxnId() : t.getRequestId();
        if (!(reference.startsWith("SP") || reference.startsWith("GP"))) {
            throw new IllegalArgumentException("A complaint cannot be raised for this payment. Please contact support.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("complaint_type", "Services");
        body.put("service_name", complaintServiceName);
        body.put("tranx_id", reference);
        // The SuivrePay account is shared by all our merchants and franchises, so tag the complaint with ours.
        body.put("description", "UtsabPay " + who.type().toLowerCase() + " #" + who.id() + ": " + description);

        VendorResponse response = client.post(raiseComplaintPath, body,
                CallContext.of(CreditCardApiLog.RAISE_COMPLAINT, who, t.getBillerId(), t.getCardLast4(),
                        t.getRequestId(), t.getBillAmount()));

        if (!isSuccess(response)) {
            throw vendorProblem(response, "raise-complaint");
        }

        String ticketId = response.body().path("data").path("ticket_id").asText("");
        if (ticketId.isBlank()) {
            throw new CreditCardBillException(HttpStatus.BAD_GATEWAY,
                    "The complaint could not be confirmed. Please try again.");
        }

        t.setComplaintTicketId(truncate(ticketId, 100));
        repository.save(t);

        log.info("Credit card complaint raised | {} | txnId={} | ticketId={}", who.label(), id, ticketId);

        return new ComplaintStatus(ticketId, "Services", complaintServiceName, description, "Open", null);
    }

    public ComplaintStatus getComplaintStatus(CustomerRef who, Long id) {

        CreditCardBillTransaction t = repository.findOwned(id, who.type(), who.id())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found."));

        if (isBlank(t.getComplaintTicketId())) {
            throw new IllegalArgumentException("No complaint has been raised for this payment.");
        }

        VendorResponse response = client.post(complaintStatusPath, Map.of("ticket_id", t.getComplaintTicketId()),
                CallContext.of(CreditCardApiLog.COMPLAINT_STATUS, who, t.getBillerId(), t.getCardLast4(),
                        t.getRequestId(), t.getBillAmount()));

        if (!isSuccess(response)) {
            throw vendorProblem(response, "complaint-status");
        }

        JsonNode d = response.body().path("data");
        return new ComplaintStatus(
                d.path("ticket_id").asText(t.getComplaintTicketId()),
                d.path("type").asText(null),
                d.path("service_name").asText(null),
                d.path("description").asText(null),
                d.path("status").asText(null),
                d.path("created_at").asText(null));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private boolean isSuccess(VendorResponse r) {
        return r != null && r.httpStatus() >= 200 && r.httpStatus() < 300 && r.body() != null
                && "success".equalsIgnoreCase(r.body().path("status").asText(""));
    }

    /** Turns a vendor refusal into a customer-safe exception (internal vendor/account details stay in the log). */
    private CreditCardBillException vendorProblem(VendorResponse r, String api) {
        int http = r == null ? 0 : r.httpStatus();
        String vendorMessage = r != null && r.body() != null ? r.body().path("message").asText("") : "";

        log.warn("SuivrePay refused request | api={} | httpStatus={} | message={}", api, http, vendorMessage);

        String message = userMessage(http, vendorMessage);
        HttpStatus status = (http >= 200 && http < 500 && !GENERIC_UNAVAILABLE.equals(message))
                ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return new CreditCardBillException(status, message);
    }

    /**
     * Auth problems, our own SuivrePay wallet running low, and server errors are
     * not the customer's business, so they get a generic message. Problems with
     * what the customer typed (wrong card digits, no bill, already paid) keep
     * the vendor's own wording.
     */
    private String userMessage(int httpStatus, String vendorMessage) {
        // "Payment failed. Rs.X refunded. <reason>": show only the biller's reason. The amount in
        // that text includes SuivrePay's own fee, which is not the customer's business.
        if (!isBlank(vendorMessage) && PAYMENT_FAILED_REFUNDED.matcher(vendorMessage).find()) {
            String reason = vendorMessage.replaceFirst("(?is)^.*?refunded\\.?", "").trim();
            if (reason.isEmpty() || reason.toLowerCase().contains("api connection error")) {
                return "The payment could not be completed. No amount has been deducted from your wallet.";
            }
            return "The payment could not be completed: " + reason
                    + " No amount has been deducted from your wallet.";
        }
        // BillAvenue / the card issuer throttles repeated lookups for the same biller (ERR033).
        if (!isBlank(vendorMessage) && vendorMessage.toLowerCase().contains("too many request")) {
            return "Too many requests for this biller right now. Please wait a few minutes and try again.";
        }
        if (httpStatus == 401 || httpStatus == 402 || httpStatus == 403 || httpStatus >= 500 || httpStatus == 0) {
            return GENERIC_UNAVAILABLE;
        }
        // "BFR001 - Incorrect / invalid Customer account ..." -> drop the internal code prefix
        return isBlank(vendorMessage) ? GENERIC_UNAVAILABLE
                : vendorMessage.trim().replaceFirst("^[A-Z]{2,5}\\d{2,4}\\s*-\\s*", "");
    }

    private BigDecimal parseAmount(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText().trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Our service charge for this bill, from the existing Payment Charges setup (same engine and
     * rules as payout): a merchant uses its merchant / franchise-merchant / global charge, a
     * franchise uses its own franchise / global charge. The slab for the bill amount decides the
     * percentage or fixed charge.
     *
     * If no charge is configured the payment is refused (chargeRequired = true), so a payment can
     * never silently go through for free because of a missing or misnamed configuration.
     */
    private BigDecimal calculateCharges(BigDecimal amount, CustomerRef who) {
        String problem = null;

        for (String code : chargeModeCodes.split(",")) {
            String modeCode = code.trim();
            if (modeCode.isEmpty()) {
                continue;
            }
            try {
                BigDecimal charges = who.isFranchise()
                        ? paymentChargeService.calculateFranchiseCharges(amount, modeCode, who.id())
                        : paymentChargeService.calculateCharges(amount, modeCode, who.id());
                return charges == null ? BigDecimal.ZERO : charges;
            } catch (IllegalArgumentException ex) {
                if (ex.getMessage() != null && ex.getMessage().startsWith("Invalid payment mode")) {
                    continue; // no payment mode with this code: try the next one
                }
                problem = ex.getMessage();
                break;
            } catch (Exception ex) {
                // the mode exists but nothing is configured for this account / amount
                problem = ex.getMessage();
                break;
            }
        }

        String reason = problem != null ? problem
                : "no payment mode found with any of these codes: " + chargeModeCodes;
        if (chargeRequired) {
            log.error("Credit card charge NOT configured, payment refused | {} | amount={} | reason={}",
                    who.label(), amount, reason);
            throw new CreditCardBillException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Service charges for credit card bill payment are not configured. "
                            + "Please contact the administrator.");
        }
        log.warn("No credit card charge configuration ({}), using zero charge: {}", who.label(), reason);
        return BigDecimal.ZERO;
    }

    private void safeEvent(TransactionSourceType type, Long sourceId, CustomerRef who, BigDecimal amount,
                           TransactionEventStatus status, String note) {
        try {
            transactionEventService.recordEvent(type, sourceId, who.type(), who.id(), amount, status, note);
        } catch (Exception e) {
            log.warn("Could not record monitoring event for credit card payment: {}", e.getMessage());
        }
    }

    private PaymentResult toResult(CreditCardBillTransaction t) {
        return new PaymentResult(t.getId(), t.getRequestId(), t.getStatus().name(), t.getResponseMessage(),
                t.getBillAmount(), t.getCharges(), t.getTotalDeducted(), t.getWalletTxnId(), t.getVendorTxnId());
    }

    private TransactionView toView(CreditCardBillTransaction t) {
        return new TransactionView(t.getId(), t.getRequestId(), t.getBillerName(), t.getCardLast4(),
                t.getCustomerName(), t.getBillAmount(), t.getCharges(), t.getTotalDeducted(),
                t.getStatus().name(), t.getResponseMessage(), t.getWalletTxnId(), t.getComplaintTicketId(),
                t.getCreatedAt(), t.getCompletedAt());
    }

    private static String maskMobile(String m) {
        return m == null || m.length() < 6 ? "****" : m.substring(0, 2) + "*".repeat(m.length() - 4) + m.substring(m.length() - 2);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    // =====================================================================
    // Wallet & ledger: merchant wallet + merchant ledger, or franchise wallet + franchise
    // ledger (same bookkeeping as the BBPS / payout flows)
    // =====================================================================

    private void lockWallet(CustomerRef who) {
        if (who.isFranchise()) {
            franchiseWalletRepository.findByFranchiseIdForUpdate(who.id())
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));
        } else {
            merchantWalletRepository.findByMerchantIdForUpdate(who.id())
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));
        }
    }

    private BigDecimal deductBalance(CustomerRef who, BigDecimal total) {
        if (who.isFranchise()) {
            FranchiseWallet wallet = franchiseWalletRepository.findByFranchiseIdForUpdate(who.id())
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));
            BigDecimal available = nvl(wallet.getAvailableBalance());
            requireEnough(available, total);
            BigDecimal newBalance = available.subtract(total);
            wallet.setAvailableBalance(newBalance);
            wallet.setLastUpdatedAmount(total.negate());
            wallet.setLastUpdatedAt(LocalDateTime.now());
            franchiseWalletRepository.save(wallet);
            return newBalance;
        }

        MerchantWallet wallet = merchantWalletRepository.findByMerchantIdForUpdate(who.id())
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));
        BigDecimal available = nvl(wallet.getAvailableBalance());
        requireEnough(available, total);
        BigDecimal newBalance = available.subtract(total);
        wallet.setAvailableBalance(newBalance);
        wallet.setLastUpdatedAmount(total.negate());
        wallet.setLastUpdatedAt(LocalDateTime.now());
        merchantWalletRepository.save(wallet);
        return newBalance;
    }

    private static void requireEnough(BigDecimal available, BigDecimal total) {
        if (available.compareTo(total) < 0) {
            throw new IllegalStateException("Insufficient wallet balance. Available: " + available
                    + ", Required: " + total);
        }
    }

    /** Writes the DEBIT row in the right ledger and returns its transaction id. */
    private Long recordDebitLedger(CustomerRef who, BigDecimal total, BigDecimal balanceAfterDebit,
                                   CreditCardBillTransaction t) {
        String remarks = "Credit card bill payment - " + (t.getBillerName() != null ? t.getBillerName() : t.getBillerId())
                + " - card ending " + t.getCardLast4();

        if (who.isFranchise()) {
            Franchise franchise = franchiseRepository.findById(who.id())
                    .orElseThrow(() -> new IllegalArgumentException("Franchise not found."));
            FranchiseTransactionDetails ftd = new FranchiseTransactionDetails();
            ftd.setFranchise(franchise);
            fillDebit(ftd, total, balanceAfterDebit, t, remarks);
            return franchiseTransDetRepository.save(ftd).getTransactionId();
        }

        Merchant merchant = merchantRepository.findById(who.id())
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found."));
        MerchantTransactionDetails mtd = new MerchantTransactionDetails();
        mtd.setMerchant(merchant);
        fillDebit(mtd, total, balanceAfterDebit, t, remarks);
        mtd.setGrossCharge(t.getCharges()); // same two charge columns as the payout ledger row
        return merchantTransDetRepository.save(mtd).getTransactionId();
    }

    private void fillDebit(com.project2.ism.Model.TransactionDetailsBase row, BigDecimal total,
                           BigDecimal balanceAfterDebit, CreditCardBillTransaction t, String remarks) {
        row.setActionOnBalance("DEBIT");
        row.setAmount(total.negate());
        row.setCharge(t.getCharges());
        row.setNetAmount(t.getBillAmount().negate());
        row.setBalBeforeTran(balanceAfterDebit.add(total));
        row.setBalAfterTran(balanceAfterDebit);
        row.setFinalBalance(balanceAfterDebit);
        row.setTransactionDate(LocalDateTime.now());
        row.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        row.setTranStatus("PENDING");
        row.setTransactionType("DEBIT");
        row.setService("CREDIT_CARD");
        row.setRemarks(remarks);
    }

    private void updateLedger(CustomerRef who, Long ledgerId, String status, String failureRemarks, String vendorTxnId) {
        if (ledgerId == null) {
            return;
        }
        if (who.isFranchise()) {
            franchiseTransDetRepository.findById(ledgerId).ifPresent(row -> {
                applyLedgerUpdate(row, status, failureRemarks, vendorTxnId);
                franchiseTransDetRepository.save(row);
            });
        } else {
            merchantTransDetRepository.findById(ledgerId).ifPresent(row -> {
                applyLedgerUpdate(row, status, failureRemarks, vendorTxnId);
                merchantTransDetRepository.save(row);
            });
        }
    }

    private static void applyLedgerUpdate(com.project2.ism.Model.TransactionDetailsBase row, String status,
                                          String failureRemarks, String vendorTxnId) {
        row.setTranStatus(status);
        if (vendorTxnId != null) {
            row.setVendorTransactionId(vendorTxnId);
        }
        if (failureRemarks != null) {
            row.setFailureRemarks(failureRemarks);
        }
    }

    private void refundWallet(CustomerRef who, BigDecimal refundAmount, String remark) {
        if (who.isFranchise()) {
            FranchiseWallet wallet = franchiseWalletRepository.findByFranchiseIdForUpdate(who.id())
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));
            BigDecimal newBalance = nvl(wallet.getAvailableBalance()).add(refundAmount);
            wallet.setAvailableBalance(newBalance);
            wallet.setLastUpdatedAmount(refundAmount);
            wallet.setLastUpdatedAt(LocalDateTime.now());
            franchiseWalletRepository.save(wallet);

            Franchise franchise = franchiseRepository.findById(who.id()).orElseThrow();
            FranchiseTransactionDetails refund = new FranchiseTransactionDetails();
            refund.setFranchise(franchise);
            fillRefund(refund, refundAmount, newBalance, remark);
            franchiseTransDetRepository.save(refund);
        } else {
            MerchantWallet wallet = merchantWalletRepository.findByMerchantIdForUpdate(who.id())
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));
            BigDecimal newBalance = nvl(wallet.getAvailableBalance()).add(refundAmount);
            wallet.setAvailableBalance(newBalance);
            wallet.setLastUpdatedAmount(refundAmount);
            wallet.setLastUpdatedAt(LocalDateTime.now());
            merchantWalletRepository.save(wallet);

            Merchant merchant = merchantRepository.findById(who.id()).orElseThrow();
            MerchantTransactionDetails refund = new MerchantTransactionDetails();
            refund.setMerchant(merchant);
            fillRefund(refund, refundAmount, newBalance, remark);
            merchantTransDetRepository.save(refund);
        }

        log.info("Refunded {} for failed credit card payment: amount={}", who.label(), refundAmount);
    }

    private void fillRefund(com.project2.ism.Model.TransactionDetailsBase row, BigDecimal refundAmount,
                            BigDecimal newBalance, String remark) {
        row.setActionOnBalance("CREDIT");
        row.setAmount(refundAmount);
        row.setBalBeforeTran(newBalance.subtract(refundAmount));
        row.setBalAfterTran(newBalance);
        row.setFinalBalance(newBalance);
        row.setTransactionDate(LocalDateTime.now());
        row.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        row.setTranStatus("SUCCESS");
        row.setTransactionType("CREDIT");
        row.setService("CREDIT_CARD_REFUND");
        row.setRemarks(remark);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
