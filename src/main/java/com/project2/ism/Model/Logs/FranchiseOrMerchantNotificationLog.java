package com.project2.ism.Model.Logs;


import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "franchise_or_merchant_notification_logs",
        indexes = {
                @Index(name = "idx_rzp_txn_id", columnList = "txn_id"),
                @Index(name = "idx_rzp_created_at", columnList = "created_at")
        })
public class FranchiseOrMerchantNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Razorpay transaction ID ("txnId")
    @Column(name = "txn_id", length = 40)
    private String txnId;

    // Raw JSON received from Razorpay
    @Column(name = "raw_json", columnDefinition = "LONGTEXT")
    private String rawJson;

    //isSend : SUCCESS / FAILED
    @Column(name = "is_send")
    private boolean isSend = false;

    // Error message if mapping failed
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // used in async processing performance monitoring
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    public FranchiseOrMerchantNotificationLog() {}

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

    public boolean isSend() {
        return isSend;
    }

    public void setSend(boolean send) {
        isSend = send;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
