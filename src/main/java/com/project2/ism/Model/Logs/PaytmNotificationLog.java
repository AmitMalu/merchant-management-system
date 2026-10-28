package com.project2.ism.Model.Logs;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One row for every notification (webhook call) Paytm sends to /paytm/notification, so it can be
 * seen in Admin > Logs > Paytm Notification Logs. The body is stored as received (capped), and
 * order id / txn id are picked out of it when the payload has them, to make searching easy.
 */
@Entity
@Table(name = "paytm_notification_logs",
        indexes = {
                @Index(name = "idx_paytm_created_at", columnList = "created_at"),
                @Index(name = "idx_paytm_order_id", columnList = "order_id")
        })
public class PaytmNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "order_id", length = 100)
    private String orderId;

    @Column(name = "txn_id", length = 100)
    private String txnId;

    @Column(name = "remote_addr", length = 64)
    private String remoteAddr;

    @Column(name = "forwarded_for", length = 255)
    private String forwardedFor;

    @Column(name = "content_type", length = 150)
    private String contentType;

    // Size of what Paytm sent (the stored body may be shorter, see bodyTruncated)
    @Column(name = "body_length")
    private Integer bodyLength;

    @Column(name = "body_truncated")
    private Boolean bodyTruncated = false;

    // Request headers, without credentials
    @Column(name = "headers", columnDefinition = "TEXT")
    private String headers;

    // The body exactly as received (up to the cap)
    @Column(name = "raw_body", columnDefinition = "LONGTEXT")
    private String rawBody;

    // RECEIVED for now (we only log). SUCCESS / FAILED once the notification is processed.
    @Column(name = "process_status", length = 20)
    private String processStatus = "RECEIVED";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public PaytmNotificationLog() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getRemoteAddr() { return remoteAddr; }
    public void setRemoteAddr(String remoteAddr) { this.remoteAddr = remoteAddr; }

    public String getForwardedFor() { return forwardedFor; }
    public void setForwardedFor(String forwardedFor) { this.forwardedFor = forwardedFor; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Integer getBodyLength() { return bodyLength; }
    public void setBodyLength(Integer bodyLength) { this.bodyLength = bodyLength; }

    public Boolean getBodyTruncated() { return bodyTruncated; }
    public void setBodyTruncated(Boolean bodyTruncated) { this.bodyTruncated = bodyTruncated; }

    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }

    public String getRawBody() { return rawBody; }
    public void setRawBody(String rawBody) { this.rawBody = rawBody; }

    public String getProcessStatus() { return processStatus; }
    public void setProcessStatus(String processStatus) { this.processStatus = processStatus; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
