package com.project2.ism.Model.Logs;


import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "razorpay_notification_logs",
        indexes = {
                @Index(name = "idx_rzp_txn_id", columnList = "txn_id"),
                @Index(name = "idx_rzp_created_at", columnList = "created_at")
        })
public class RazorpayNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Razorpay transaction ID ("txnId")
    @Column(name = "txn_id", length = 40)
    private String txnId;

    // Raw JSON received from Razorpay
    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;

    // Status: RECEIVED / SUCCESS / FAILED
    @Column(name = "process_status", length = 20)
    private String processStatus;

    // Error message if mapping failed
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Optional: retry count if needed later
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // used in async processing performance monitoring
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    public RazorpayNotificationLog() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public String getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }
}
