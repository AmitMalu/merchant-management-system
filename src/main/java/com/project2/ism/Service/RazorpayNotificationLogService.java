package com.project2.ism.Service;

import com.project2.ism.Model.Logs.RazorpayNotificationLog;
import com.project2.ism.Repository.RazorpayNotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RazorpayNotificationLogService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayNotificationLogService.class);
    private final RazorpayNotificationLogRepository razorpayNotificationLogRepository;

    public RazorpayNotificationLogService(RazorpayNotificationLogRepository razorpayNotificationLogRepository) {
        this.razorpayNotificationLogRepository = razorpayNotificationLogRepository;
    }

    /**
     * CREATE log entry immediately and return the ID.
     * This cannot be async because we must return logId to caller.
     */
    public Long logReceived(String rawJson, String txnId) {
        RazorpayNotificationLog entry = new RazorpayNotificationLog();
        entry.setRawJson(rawJson);
        entry.setTxnId(txnId);
        entry.setProcessStatus("RECEIVED");

        entry = razorpayNotificationLogRepository.save(entry);
        return entry.getId(); // returning log ID
    }

    /**
     * Update existing log with SUCCESS (async)
     */
    @Async("razorpayNotificationExecutor")
    public void logSuccess(Long logId, long timeMs) {
        razorpayNotificationLogRepository.findById(logId).ifPresent(entry -> {
            entry.setProcessStatus("SUCCESS");
            entry.setProcessingTimeMs(timeMs);
            razorpayNotificationLogRepository.save(entry);
        });
    }

    /**
     * Update existing log with FAILURE (async)
     */
    @Async("razorpayNotificationExecutor")
    public void logFailure(Long logId, String error, long timeMs) {
        razorpayNotificationLogRepository.findById(logId).ifPresent(entry -> {
            entry.setProcessStatus("FAILED");
            entry.setErrorMessage(error);
            entry.setProcessingTimeMs(timeMs);
            razorpayNotificationLogRepository.save(entry);
        });
    }

    /**
     * For cases where DTO parsing fails and we never got a logId.
     */
    public void logFailureNoDTO(String rawJson, String txnId, String error, long timeMs) {
        RazorpayNotificationLog entry = new RazorpayNotificationLog();
        entry.setRawJson(rawJson);
        entry.setTxnId(txnId);
        entry.setProcessStatus("FAILED");
        entry.setErrorMessage(error);
        entry.setProcessingTimeMs(timeMs);

        razorpayNotificationLogRepository.save(entry);
    }
}
