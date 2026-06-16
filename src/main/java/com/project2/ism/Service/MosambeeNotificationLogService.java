package com.project2.ism.Service;

import com.project2.ism.Model.Logs.MosambeeNotificationLog;
import com.project2.ism.Repository.MosambeeNotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MosambeeNotificationLogService {

    private static final Logger log = LoggerFactory.getLogger(MosambeeNotificationLogService.class);
    private final MosambeeNotificationLogRepository mosambeeNotificationLogRepository;

    public MosambeeNotificationLogService(MosambeeNotificationLogRepository mosambeeNotificationLogRepository) {
        this.mosambeeNotificationLogRepository = mosambeeNotificationLogRepository;
    }

    /**
     * CREATE log entry immediately and return the ID.
     * This cannot be async because we must return logId to caller.
     */
    public Long logReceived(String rawJson, String txnId) {
        MosambeeNotificationLog entry = new MosambeeNotificationLog();
        entry.setRawJson(rawJson);
        entry.setTxnId(txnId);
        entry.setProcessStatus("RECEIVED");

        entry = mosambeeNotificationLogRepository.save(entry);
        return entry.getId(); // returning log ID
    }

    /**
     * Update existing log with SUCCESS (async)
     */
    @Async("razorpayNotificationExecutor")
    public void logSuccess(Long logId, long timeMs) {
        mosambeeNotificationLogRepository.findById(logId).ifPresent(entry -> {
            entry.setProcessStatus("SUCCESS");
            entry.setProcessingTimeMs(timeMs);
            mosambeeNotificationLogRepository.save(entry);
        });
    }

    /**
     * Update existing log with FAILURE (async)
     */
    @Async("razorpayNotificationExecutor")
    public void logFailure(Long logId, String error, long timeMs) {
        mosambeeNotificationLogRepository.findById(logId).ifPresent(entry -> {
            entry.setProcessStatus("FAILED");
            entry.setErrorMessage(error);
            entry.setProcessingTimeMs(timeMs);
            mosambeeNotificationLogRepository.save(entry);
        });
    }

    /**
     * For cases where DTO parsing fails and we never got a logId.
     */
    public void logFailureNoDTO(String rawJson, String txnId, String error, long timeMs) {
        MosambeeNotificationLog entry = new MosambeeNotificationLog();
        entry.setRawJson(rawJson);
        entry.setTxnId(txnId);
        entry.setProcessStatus("FAILED");
        entry.setErrorMessage(error);
        entry.setProcessingTimeMs(timeMs);

        mosambeeNotificationLogRepository.save(entry);
    }
}
