package com.project2.ism.Service;

import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Repository.CreditCardBillTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * SMS to the customer after a credit card bill payment (SuivrePay) succeeds. Sent in the background,
 * after the payment has been saved, so it never slows down or changes a payment.
 * Goes out only through Surepass, and only when an admin has this SMS switched on and its template id is set.
 */
@Service
public class CreditCardNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CreditCardNotificationService.class);

    private final CreditCardBillTransactionRepository transactions;
    private final SurepassSmsService surepass;

    public CreditCardNotificationService(CreditCardBillTransactionRepository transactions, SurepassSmsService surepass) {
        this.transactions = transactions;
        this.surepass = surepass;
    }

    public void notifyPaymentSuccess(Long transactionId) {
        CompletableFuture.runAsync(() -> sendPaymentSuccess(transactionId));
    }

    void sendPaymentSuccess(Long transactionId) {
        try {
            if (transactionId == null || !surepass.isActive(SurepassSmsService.SUIVREPAY_PAYMENT)) {
                return;
            }
            CreditCardBillTransaction t = transactions.findById(transactionId).orElse(null);
            if (t == null || t.getStatus() != CreditCardBillTransaction.Status.SUCCESS) {
                log.warn("Credit card payment SMS NOT sent: no successful payment found for id {}", transactionId);
                return;
            }
            String biller = t.getBillerName() != null ? t.getBillerName() : "Credit Card";
            // Only the last 4 digits of the card are ever stored, so that is all the SMS can show.
            String card = t.getCardLast4() != null ? "XXXX" + t.getCardLast4() : "-";
            String txnId = first(t.getApiRefNumber(), t.getVendorTxnId(), t.getWalletTxnId(), t.getRequestId());
            LocalDateTime when = t.getCompletedAt() != null ? t.getCompletedAt() : LocalDateTime.now();

            surepass.sendPaymentSuccess(SurepassSmsService.SUIVREPAY_PAYMENT, t.getCustomerMobile(), t.effectiveAmount(), biller, card, txnId, when,
                    "WALLET", "CC:" + transactionId);
        } catch (Exception ex) {
            log.warn("Credit card payment SMS failed for id {}: {}", transactionId, ex.getMessage());
        }
    }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
