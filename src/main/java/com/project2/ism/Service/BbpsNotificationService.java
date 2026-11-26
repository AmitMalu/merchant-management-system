package com.project2.ism.Service;

import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Repository.BbpsTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * SMS to the customer whose mobile number was entered on the bill: after a successful bill payment
 * and after a complaint is registered. Sent in the background, after the payment has been saved,
 * so it never slows down or changes a payment.
 */
@Service
public class BbpsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(BbpsNotificationService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final BbpsTransactionRepository transactions;
    private final SmsService smsService;
    private final SurepassSmsService surepass;

    @Autowired
    public BbpsNotificationService(BbpsTransactionRepository transactions, SmsService smsService,
                                   SurepassSmsService surepass) {
        this.transactions = transactions;
        this.smsService = smsService;
        this.surepass = surepass;
    }

    public BbpsNotificationService(BbpsTransactionRepository transactions, SmsService smsService) {
        this(transactions, smsService, null);
    }

    public void notifyPaymentSuccess(String txnRefId) {
        CompletableFuture.runAsync(() -> sendPaymentSuccess(txnRefId));
    }

    public void notifyComplaintRegistered(String txnRefId, String complaintId) {
        CompletableFuture.runAsync(() -> sendComplaintRegistered(txnRefId, complaintId));
    }

    void sendPaymentSuccess(String txnRefId) {
        try {
            log.info("Payment SMS requested for txn {}", txnRefId);
            BbpsTransaction t = find(txnRefId);
            if (t == null) {
                log.warn("Payment SMS NOT sent: no saved payment found for txn {}", txnRefId);
                return;
            }
            String biller = t.getBillerName() != null ? t.getBillerName()
                    : (t.getCategory() != null ? t.getCategory() : "your bill");
            LocalDateTime when = t.getCompletedAt() != null ? t.getCompletedAt() : LocalDateTime.now();

            // The admin can switch this SMS off; then no sender sends it.
            if (surepass != null && !surepass.isEnabled(SurepassSmsService.BILL_AVENUE_PAYMENT)) {
                log.info("Payment SMS NOT sent: switched off by an admin (SMS Settings) for txn {}", txnRefId);
                return;
            }
            // Surepass (DLT template) takes over once it is ready for this message.
            // Until then the old sender below is used, exactly as before.
            if (surepass != null && surepass.isActive(SurepassSmsService.BILL_AVENUE_PAYMENT)) {
                surepass.sendPaymentSuccess(SurepassSmsService.BILL_AVENUE_PAYMENT, t.getCustomerMobile(), t.getAmount(), biller, t.getConsumerNumber(),
                        t.getTxnRefId(), when, t.getPaymentMode() != null ? t.getPaymentMode() : "WALLET",
                        "BBPS:" + t.getTxnRefId());
                return;
            }

            smsService.send(t.getCustomerMobile(),
                    "Thank you for payment of Rs." + t.getAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                            + " against " + biller
                            + ", Consumer no " + (t.getConsumerNumber() != null ? t.getConsumerNumber() : "-")
                            + ", B-connect Txn ID " + t.getTxnRefId()
                            + " on " + when.format(WHEN)
                            + " vide " + (t.getPaymentMode() != null ? t.getPaymentMode() : "WALLET") + ".");
        } catch (Exception ex) {
            log.warn("Payment SMS failed for txn {}: {}", txnRefId, ex.getMessage());
        }
    }

    void sendComplaintRegistered(String txnRefId, String complaintId) {
        try {
            log.info("Complaint SMS requested for txn {}", txnRefId);
            BbpsTransaction t = find(txnRefId);
            if (t == null) {
                log.warn("Complaint SMS NOT sent: no saved payment found for txn {}", txnRefId);
                return;
            }
            if (surepass != null && !surepass.isEnabled(SurepassSmsService.BILL_AVENUE_COMPLAINT)) {
                log.info("Complaint SMS NOT sent: switched off by an admin (SMS Settings) for txn {}", txnRefId);
                return;
            }
            // Surepass (DLT template) takes over once it is ready for this message.
            if (surepass != null && surepass.isActive(SurepassSmsService.BILL_AVENUE_COMPLAINT)) {
                surepass.sendComplaintRegistered(t.getCustomerMobile(), t.getTxnRefId(), complaintId,
                        "BBPS-COMPLAINT:" + complaintId);
                return;
            }
            smsService.send(t.getCustomerMobile(),
                    "Your Complaint has been registered successfully for B-connect Txn ID " + t.getTxnRefId()
                            + ". Your Complaint ID is " + complaintId
                            + ". You can track status of your complaint using your Complaint ID.");
        } catch (Exception ex) {
            log.warn("Complaint SMS failed for txn {}: {}", txnRefId, ex.getMessage());
        }
    }

    private BbpsTransaction find(String txnRefId) {
        if (txnRefId == null || txnRefId.isBlank()) {
            return null;
        }
        return transactions.findByTxnRefId(txnRefId).orElse(null);
    }
}
