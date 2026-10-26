package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.*;
import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Exception.CreditCardBillException;
import com.project2.ism.Model.CreditCard.CreditCardApiLog;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction.Status;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.MerchantWallet;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.CreditCardBillTransactionRepository;
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
 * Credit card bill payment through SuivrePay.
 *
 * Flow: list billers -> fetch bill (stored here as FETCHED) -> pay.
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
 * may really have been paid, and refunding it would pay the merchant twice.
 * Those stay PENDING for the merchant to raise a complaint and for admin review.
 */
@Service
public class CreditCardBillService {

    private static final Logger log = LoggerFactory.getLogger(CreditCardBillService.class);

    private static final String PARAM_CARD_LAST4 = "Last 4 digits of Primary Credit Card Number";
    private static final String PARAM_MOBILE = "Registered Mobile Number";

    private static final String PAYMENT_MODE_CODE = "CREDIT_CARD";
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

    private final SuivrePayClient client;
    private final CreditCardBillTransactionRepository repository;
    private final MerchantWalletRepository merchantWalletRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantTransDetRepository merchantTransDetRepository;
    private final PaymentChargeService paymentChargeService;
    private final TransactionEventService transactionEventService;
    private final TransactionTemplate tx;

    public CreditCardBillService(SuivrePayClient client,
                                 CreditCardBillTransactionRepository repository,
                                 MerchantWalletRepository merchantWalletRepository,
                                 MerchantRepository merchantRepository,
                                 MerchantTransDetRepository merchantTransDetRepository,
                                 PaymentChargeService paymentChargeService,
                                 TransactionEventService transactionEventService,
                                 PlatformTransactionManager transactionManager) {
        this.client = client;
        this.repository = repository;
        this.merchantWalletRepository = merchantWalletRepository;
        this.merchantRepository = merchantRepository;
        this.merchantTransDetRepository = merchantTransDetRepository;
        this.paymentChargeService = paymentChargeService;
        this.transactionEventService = transactionEventService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // =====================================================================
    // 1. Billers
    // =====================================================================

    public List<Biller> getBillers(Long merchantId) {
        log.info("Credit card billers requested | merchantId={}", merchantId);
        VendorResponse response = client.post(billersPath, null,
                new CallContext(CreditCardApiLog.FETCH_BILLERS, merchantId, null, null, null, null));

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

    public FetchedBill fetchBill(Long merchantId, FetchBillRequest request) {

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

        log.info("Credit card fetch-bill started | merchantId={} | billerId={} | card=****{} | mobile={}",
                merchantId, billerId, last4, maskMobile(mobile));

        // Same card fetched again within a minute (double click, "let me check once more"):
        // show the bill we already have instead of asking the biller again. Billers throttle
        // repeated lookups ("Too many request for that Biller"), so this keeps them calm.
        java.util.Optional<CreditCardBillTransaction> recent = repository
                .findFirstByMerchantIdAndBillerIdAndCardLast4AndCustomerMobileAndStatusAndFetchedAtAfterOrderByFetchedAtDesc(
                        merchantId, billerId, last4, mobile, Status.FETCHED,
                        LocalDateTime.now().minusSeconds(fetchReuseSeconds));
        if (recent.isPresent()) {
            CreditCardBillTransaction existing = recent.get();
            log.info("Credit card fetch-bill reused a bill fetched moments ago | merchantId={} | requestId={}",
                    merchantId, existing.getRequestId());
            return toFetchedBill(existing, calculateChargesSafely(existing.getBillAmount(), merchantId));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("MID", client.mid());
        body.put("biller_id", billerId);
        body.put("mobile", mobile); // SuivrePay's sample request also sends the mobile at top level
        body.put("input_params", List.of(
                Map.of("name", PARAM_CARD_LAST4, "value", last4),
                Map.of("name", PARAM_MOBILE, "value", mobile)));

        VendorResponse response = client.post(fetchBillPath, body,
                new CallContext(CreditCardApiLog.FETCH_BILL, merchantId, billerId, last4, null, null));

        if (!isSuccess(response)) {
            throw vendorProblem(response, "fetch-bill");
        }

        JsonNode data = response.body().path("data");
        String requestId = data.path("request_id").asText("");
        BigDecimal amount = parseAmount(data.path("amount"));

        if (requestId.isBlank() || amount == null) {
            log.error("SuivrePay fetch-bill returned an unusable response | merchantId={}", merchantId);
            throw new CreditCardBillException(HttpStatus.BAD_GATEWAY,
                    "Could not read the bill details. Please try again.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CreditCardBillException(HttpStatus.BAD_REQUEST,
                    "There is no outstanding amount to pay on this card.");
        }

        CreditCardBillTransaction row = new CreditCardBillTransaction();
        row.setMerchantId(merchantId);
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
            log.warn("Duplicate request_id on fetch-bill | merchantId={}", merchantId);
            throw new CreditCardBillException(HttpStatus.CONFLICT,
                    "This bill was already fetched. Please fetch it again.");
        }

        BigDecimal charges = calculateChargesSafely(amount, merchantId);

        log.info("Credit card fetch-bill done | merchantId={} | requestId={} | billerId={} | amount={} | charges={}",
                merchantId, requestId, billerId, amount, charges);

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

    public PaymentResult payBill(Long merchantId, PayBillRequest request) {

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

        log.info("Credit card pay requested | merchantId={} | requestId={} | panGiven={}", merchantId, requestId, pan != null);

        // Fail before any money moves if SuivrePay is not configured.
        client.ensureConfigured();
        String mid = client.mid();

        // ---- Step 1: debit + mark PENDING (committed before we call the vendor) ----
        final String panFinal = pan;
        Prepared prepared = tx.execute(status -> prepareDebit(merchantId, requestId, panFinal));

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
                    new CallContext(CreditCardApiLog.PAY_BILL, merchantId, prepared.billerId(), prepared.cardLast4(),
                            prepared.requestId(), prepared.amount()));
            outcome = classify(response);
            log.info("Credit card pay: SuivrePay answered | merchantId={} | requestId={} | httpStatus={} | vendorStatus={} | vendorMessage={} | decision={}",
                    merchantId, requestId, response.httpStatus(),
                    response.body() != null ? response.body().path("status").asText("") : "(not JSON)",
                    response.body() != null ? response.body().path("message").asText("") : "",
                    outcome);
        } catch (SuivrePayClient.VendorUnreachableException e) {
            log.error("Credit card pay: no answer from SuivrePay, outcome unknown | merchantId={} | requestId={}",
                    merchantId, requestId);
            outcome = Outcome.UNKNOWN;
        } catch (Exception e) {
            log.error("Credit card pay: unexpected error after debit, outcome unknown | merchantId={} | requestId={} | error={}",
                    merchantId, requestId, e.getMessage(), e);
            outcome = Outcome.UNKNOWN;
        }

        // ---- Step 3: record the result ----
        final VendorResponse vendorResponse = response;
        final Outcome finalOutcome = outcome;
        try {
            return tx.execute(status -> finalizePayment(merchantId, prepared, vendorResponse, finalOutcome));
        } catch (Exception e) {
            // The debit is already committed and the row is PENDING, so nothing is lost;
            // log everything needed to reconcile by hand.
            log.error("CREDIT CARD PAYMENT NEEDS MANUAL CHECK | merchantId={} | requestId={} | outcome={} | httpStatus={} | raw={} | error={}",
                    merchantId, requestId, finalOutcome,
                    vendorResponse != null ? vendorResponse.httpStatus() : null,
                    vendorResponse != null ? vendorResponse.raw() : null,
                    e.getMessage(), e);
            return new PaymentResult(prepared.id(), requestId, Status.PENDING.name(), PENDING_MESSAGE,
                    prepared.amount(), prepared.charges(), prepared.totalDeducted(), null, null);
        }
    }

    private Prepared prepareDebit(Long merchantId, String requestId, String pan) {

        CreditCardBillTransaction t = repository.findByRequestIdAndMerchantIdForUpdate(requestId, merchantId)
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
        // wallet lock FIRST so two simultaneous requests for this merchant are handled one
        // after the other, then refuse if an identical payment was made a moment ago.
        merchantWalletRepository.findByMerchantIdForUpdate(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));
        rejectIfRecentDuplicate(merchantId, t, amount);

        BigDecimal charges = calculateChargesSafely(amount, merchantId);
        BigDecimal total = amount.add(charges);

        BigDecimal balanceAfterDebit = deductMerchantBalance(merchantId, total);

        t.setCharges(charges);
        t.setTotalDeducted(total);
        t.setCustomerPan(pan);
        t.setStatus(Status.PENDING);
        t.setPaidAt(LocalDateTime.now());
        repository.save(t);

        MerchantTransactionDetails ledger = recordDebitLedger(merchantId, total, balanceAfterDebit, t);
        t.setLedgerMerchantTxnId(ledger.getTransactionId());
        repository.save(t);

        safeEvent(TransactionSourceType.CREDIT_CARD, t.getId(), merchantId, total,
                TransactionEventStatus.PENDING, "billerId=" + t.getBillerId());

        log.info("Credit card payment started | merchantId={} | requestId={} | amount={} | charges={}",
                merchantId, requestId, amount, charges);

        return new Prepared(t.getId(), requestId, amount, pan, charges, total,
                t.getCardLast4(), t.getCustomerMobile(), t.getBillerId());
    }

    /**
     * Blocks paying the same amount to the same card (same biller, last 4 digits and mobile)
     * again within the duplicate window. Failed (refunded) payments do not count, so a
     * genuine retry after a failure is never blocked.
     */
    private void rejectIfRecentDuplicate(Long merchantId, CreditCardBillTransaction t, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();

        List<CreditCardBillTransaction> recent = repository.findRecentSimilarPayments(
                merchantId, t.getBillerId(), t.getCardLast4(), t.getCustomerMobile(), amount,
                List.of(Status.PENDING, Status.SUCCESS), now.minusSeconds(duplicateWindowSeconds), t.getId());

        if (recent.isEmpty()) {
            return;
        }

        LocalDateTime earliestRetry = recent.get(0).getPaidAt().plusSeconds(duplicateWindowSeconds);
        long waitSeconds = Math.max(1, java.time.Duration.between(now, earliestRetry).getSeconds());

        log.warn("Duplicate credit card payment blocked | merchantId={} | billerId={} | card=****{} | amount={} | waitSeconds={}",
                merchantId, t.getBillerId(), t.getCardLast4(), amount, waitSeconds);

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

    private PaymentResult finalizePayment(Long merchantId, Prepared p, VendorResponse r, Outcome outcome) {

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

                updateLedger(t.getLedgerMerchantTxnId(), "SUCCESS", null,
                        t.getWalletTxnId() != null ? t.getWalletTxnId() : t.getVendorTxnId());

                safeEvent(TransactionSourceType.CREDIT_CARD, t.getId(), merchantId, t.getTotalDeducted(),
                        TransactionEventStatus.SUCCESS, "walletTxnId=" + t.getWalletTxnId());

                log.info("Credit card payment SUCCESS | merchantId={} | requestId={} | walletTxnId={}",
                        merchantId, t.getRequestId(), t.getWalletTxnId());
            }
            case FAILED -> {
                String vendorMessage = r.body() != null ? r.body().path("message").asText("") : "";
                String userMessage = userMessage(r.httpStatus(), vendorMessage);

                t.setStatus(Status.FAILED);
                t.setResponseMessage(truncate(userMessage, 500));
                t.setCompletedAt(LocalDateTime.now());
                repository.save(t);

                updateLedger(t.getLedgerMerchantTxnId(), "FAILED", userMessage, null);
                refundMerchantWallet(merchantId, t.getTotalDeducted(),
                        "Credit card bill payment failed - " + userMessage);

                safeEvent(TransactionSourceType.CREDIT_CARD, t.getId(), merchantId, t.getTotalDeducted(),
                        TransactionEventStatus.FAILED, userMessage);
                safeEvent(TransactionSourceType.CREDIT_CARD_REFUND, t.getId(), merchantId, t.getTotalDeducted(),
                        TransactionEventStatus.SUCCESS, "refund for failed credit card payment " + t.getRequestId());

                log.warn("Credit card payment FAILED and refunded | merchantId={} | requestId={} | httpStatus={} | vendorMessage={}",
                        merchantId, t.getRequestId(), r.httpStatus(), vendorMessage);
            }
            default -> {
                // UNKNOWN: keep the money deducted and the row PENDING.
                t.setResponseMessage(PENDING_MESSAGE);
                repository.save(t);

                safeEvent(TransactionSourceType.CREDIT_CARD, t.getId(), merchantId, t.getTotalDeducted(),
                        TransactionEventStatus.PENDING, "outcome unknown, awaiting confirmation");

                log.error("Credit card payment OUTCOME UNKNOWN, left PENDING | merchantId={} | requestId={} | httpStatus={}",
                        merchantId, t.getRequestId(), r != null ? r.httpStatus() : null);
            }
        }

        return toResult(t);
    }

    // =====================================================================
    // 4. History + complaints
    // =====================================================================

    public List<TransactionView> getHistory(Long merchantId) {
        return repository.findHistory(merchantId, Status.FETCHED, PageRequest.of(0, 100))
                .stream()
                .map(this::toView)
                .toList();
    }

    public ComplaintStatus raiseComplaint(Long merchantId, Long id, ComplaintRequest request) {

        String description = request == null || request.description() == null ? "" : request.description().trim();
        if (description.length() < 10 || description.length() > 500) {
            throw new IllegalArgumentException("Please describe the issue in 10 to 500 characters.");
        }

        CreditCardBillTransaction t = repository.findByIdAndMerchantId(id, merchantId)
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
        // The SuivrePay account is shared by all our merchants, so tag the complaint with ours.
        body.put("description", "UtsabPay merchant #" + merchantId + ": " + description);

        VendorResponse response = client.post(raiseComplaintPath, body,
                new CallContext(CreditCardApiLog.RAISE_COMPLAINT, merchantId, t.getBillerId(), t.getCardLast4(),
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

        log.info("Credit card complaint raised | merchantId={} | txnId={} | ticketId={}", merchantId, id, ticketId);

        return new ComplaintStatus(ticketId, "Services", complaintServiceName, description, "Open", null);
    }

    public ComplaintStatus getComplaintStatus(Long merchantId, Long id) {

        CreditCardBillTransaction t = repository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found."));

        if (isBlank(t.getComplaintTicketId())) {
            throw new IllegalArgumentException("No complaint has been raised for this payment.");
        }

        VendorResponse response = client.post(complaintStatusPath, Map.of("ticket_id", t.getComplaintTicketId()),
                new CallContext(CreditCardApiLog.COMPLAINT_STATUS, merchantId, t.getBillerId(), t.getCardLast4(),
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

    /** Turns a vendor refusal into a merchant-safe exception (internal vendor/account details stay in the log). */
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
     * not the merchant's business, so they get a generic message. Problems with
     * what the merchant typed (wrong card digits, no bill, already paid) keep
     * the vendor's own wording.
     */
    private String userMessage(int httpStatus, String vendorMessage) {
        // "Payment failed. Rs.X refunded. <reason>": show only the biller's reason. The amount in
        // that text includes SuivrePay's own fee, which is not the merchant's business.
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

    /** Commission via the existing charge setup (payment mode CREDIT_CARD); zero when not configured yet. */
    private BigDecimal calculateChargesSafely(BigDecimal amount, Long merchantId) {
        try {
            BigDecimal charges = paymentChargeService.calculateCharges(amount, PAYMENT_MODE_CODE, merchantId);
            return charges == null ? BigDecimal.ZERO : charges;
        } catch (Exception ex) {
            log.warn("No CREDIT_CARD charge configuration (merchantId={}), using zero commission: {}",
                    merchantId, ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private void safeEvent(TransactionSourceType type, Long sourceId, Long merchantId, BigDecimal amount,
                           TransactionEventStatus status, String note) {
        try {
            transactionEventService.recordEvent(type, sourceId, "MERCHANT", merchantId, amount, status, note);
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
    // Wallet & ledger (same bookkeeping as the BBPS / payout flows)
    // =====================================================================

    private BigDecimal deductMerchantBalance(Long merchantId, BigDecimal total) {
        MerchantWallet wallet = merchantWalletRepository.findByMerchantIdForUpdate(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));

        BigDecimal available = nvl(wallet.getAvailableBalance());
        if (available.compareTo(total) < 0) {
            throw new IllegalStateException("Insufficient wallet balance. Available: " + available
                    + ", Required: " + total);
        }

        BigDecimal newBalance = available.subtract(total);
        wallet.setAvailableBalance(newBalance);
        wallet.setLastUpdatedAmount(total.negate());
        wallet.setLastUpdatedAt(LocalDateTime.now());
        merchantWalletRepository.save(wallet);
        return newBalance;
    }

    private MerchantTransactionDetails recordDebitLedger(Long merchantId, BigDecimal total,
                                                         BigDecimal balanceAfterDebit,
                                                         CreditCardBillTransaction t) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found."));

        MerchantTransactionDetails mtd = new MerchantTransactionDetails();
        mtd.setMerchant(merchant);
        mtd.setActionOnBalance("DEBIT");
        mtd.setAmount(total.negate());
        mtd.setCharge(t.getCharges());
        mtd.setNetAmount(t.getBillAmount().negate());
        mtd.setBalBeforeTran(balanceAfterDebit.add(total));
        mtd.setBalAfterTran(balanceAfterDebit);
        mtd.setFinalBalance(balanceAfterDebit);
        mtd.setTransactionDate(LocalDateTime.now());
        mtd.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        mtd.setTranStatus("PENDING");
        mtd.setTransactionType("DEBIT");
        mtd.setService("CREDIT_CARD");
        mtd.setRemarks("Credit card bill payment - " + (t.getBillerName() != null ? t.getBillerName() : t.getBillerId())
                + " - card ending " + t.getCardLast4());
        return merchantTransDetRepository.save(mtd);
    }

    private void updateLedger(Long ledgerId, String status, String failureRemarks, String vendorTxnId) {
        if (ledgerId == null) {
            return;
        }
        merchantTransDetRepository.findById(ledgerId).ifPresent(mtd -> {
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
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found."));

        BigDecimal newBalance = nvl(wallet.getAvailableBalance()).add(refundAmount);
        wallet.setAvailableBalance(newBalance);
        wallet.setLastUpdatedAmount(refundAmount);
        wallet.setLastUpdatedAt(LocalDateTime.now());
        merchantWalletRepository.save(wallet);

        Merchant merchant = merchantRepository.findById(merchantId).orElseThrow();
        MerchantTransactionDetails refund = new MerchantTransactionDetails();
        refund.setMerchant(merchant);
        refund.setActionOnBalance("CREDIT");
        refund.setAmount(refundAmount);
        refund.setBalBeforeTran(newBalance.subtract(refundAmount));
        refund.setBalAfterTran(newBalance);
        refund.setFinalBalance(newBalance);
        refund.setTransactionDate(LocalDateTime.now());
        refund.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
        refund.setTranStatus("SUCCESS");
        refund.setTransactionType("CREDIT");
        refund.setService("CREDIT_CARD_REFUND");
        refund.setRemarks(remark);
        merchantTransDetRepository.save(refund);

        log.info("Refunded merchant {} for failed credit card payment: amount={}", merchantId, refundAmount);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
