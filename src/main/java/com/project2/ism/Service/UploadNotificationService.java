package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.MosambeeNotificationDTO;
import com.project2.ism.Model.VendorTransactions;
import com.project2.ism.Repository.FranchiseOrMerchantNotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends the franchise / merchant notification for transactions that arrived
 * through a file upload, exactly like the Mosambee webhook does.
 *
 * The callback lookup (MID -> device -> franchise, else merchant) and the HTTP
 * call + log row are NOT re-implemented here: they are done by
 * {@link MosambeeTransactionService#processFranchiseOrMerchantNotification}.
 * This class only builds the JSON body (same field names as the Mosambee
 * payload, since an uploaded row has no vendor JSON) and skips transactions
 * that were already notified.
 */
@Service
public class UploadNotificationService {

    private static final Logger log = LoggerFactory.getLogger(UploadNotificationService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MosambeeTransactionService notificationSender;
    private final FranchiseOrMerchantNotificationLogRepository notificationLogRepo;
    private final ObjectMapper mapper;

    public UploadNotificationService(MosambeeTransactionService notificationSender,
                                     FranchiseOrMerchantNotificationLogRepository notificationLogRepo,
                                     ObjectMapper mapper) {
        this.notificationSender = notificationSender;
        this.notificationLogRepo = notificationLogRepo;
        this.mapper = mapper;
    }

    @Async("uploadNotificationExecutor")
    public void notifyUploadedTransactions(List<VendorTransactions> transactions) {

        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        log.info("Upload notification started | count={}", transactions.size());

        int attempted = 0;
        int skippedAlreadySent = 0;
        int skippedInvalid = 0;

        for (VendorTransactions tx : transactions) {
            try {
                if (isBlank(tx.getTransactionReferenceId()) || isBlank(tx.getMid())) {
                    skippedInvalid++;
                    continue;
                }

                // Re-upload protection: never notify the same transaction twice.
                if (notificationLogRepo.existsSentByTxnId(tx.getTransactionReferenceId())) {
                    skippedAlreadySent++;
                    continue;
                }

                String payload = mapper.writeValueAsString(toPayload(tx));
                notificationSender.processFranchiseOrMerchantNotification(tx, payload);
                attempted++;

            } catch (Exception e) {
                // One bad row must not stop the rest of the file.
                log.error("Upload notification failed for txnId={}", tx.getTransactionReferenceId(), e);
            }
        }

        log.info("Upload notification finished | total={} | attempted={} | skippedAlreadySent={} | skippedInvalid={}",
                transactions.size(), attempted, skippedAlreadySent, skippedInvalid);
    }

    /** Same field names / formats as the Mosambee webhook payload. */
    private MosambeeNotificationDTO toPayload(VendorTransactions tx) {

        MosambeeNotificationDTO dto = new MosambeeNotificationDTO();

        dto.setTransactionID(tx.getTransactionReferenceId());
        dto.setName(tx.getUsername());
        dto.setCardHolderName(tx.getConsumer());
        dto.setMerchantId(tx.getMid());
        dto.setBusinessName(tx.getMerchant());
        dto.setTransactionTerminalId(tx.getTid());

        if (tx.getDate() != null) {
            dto.setTransactionDate(tx.getDate().format(DATE_FMT));
            dto.setTransactionTime(tx.getDate().format(TIME_FMT));
        }

        dto.setTransactionAmount(amount(tx.getAmount()));
        dto.setTipAmount(amount(tx.getTip()));
        dto.setCashBack(amount(tx.getCashAtPos()));

        dto.setTransactionRRN(tx.getRrn());
        dto.setTransactionAuthCode(tx.getAuthCode());
        dto.setTransactionCardNumber(tx.getCardLastFourDigit());
        dto.setCreditDebitCardType(tx.getCardType());
        dto.setCardType(tx.getBrandType());
        dto.setInvoiceNumber(tx.getInvoiceNumber());
        dto.setAcquirerName(tx.getAcquiringBank());
        dto.setTransactionTypeName(tx.getTxnType());
        dto.setTransactionStatus(tx.getStatus());
        dto.setTransactionBatchNumber(tx.getBatchNumber());
        dto.setRefTxnId(tx.getReferenceTransactionId());
        dto.setApn(tx.getDeviceSerial());
        dto.setTransactionLat(tx.getLatitude());
        dto.setTransactionLong(tx.getLongitude());
        dto.setTransactionSTAN(tx.getRef1());

        return dto;
    }

    private static String amount(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
