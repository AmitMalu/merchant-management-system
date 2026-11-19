package com.project2.ism.Model.Payout;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One try on a vendor for a payment. Payout today: exactly one attempt per payout. Later phases add more
 * attempts (retries, the next vendor) for the same payment.
 *
 * Status: CREATED (saved with the debit, vendor not answered yet), SUCCESS, PENDING, FAILED, UNKNOWN
 * (the vendor call broke and nobody knows if it went through: a person must check).
 */
@Entity
@Table(name = "payment_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_attempt_source",
                columnNames = {"source_type", "source_id", "attempt_no"}),
        indexes = @Index(name = "idx_attempt_created", columnList = "created_at"))
public class PaymentAttempt {

    public static final String SOURCE_PAYOUT = "PAYOUT";

    public static final String CREATED = "CREATED";
    public static final String SUCCESS = "SUCCESS";
    public static final String PENDING = "PENDING";
    public static final String FAILED = "FAILED";
    public static final String UNKNOWN = "UNKNOWN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What was being paid: PAYOUT, and later other products. */
    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    /** The id of that payment (payout_transactions.id for a payout). */
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 1;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    /** The reference this attempt carries to the vendor (it matches a callback to the attempt). */
    @Column(name = "vendor_reference", length = 100)
    private String vendorReference;

    @Column(name = "status", nullable = false, length = 20)
    private String status = CREATED;

    @Column(name = "response_code", length = 50)
    private String responseCode;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "vendor_txn_id", length = 100)
    private String vendorTxnId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getVendorReference() { return vendorReference; }
    public void setVendorReference(String vendorReference) { this.vendorReference = vendorReference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) {
        this.message = message != null && message.length() > 500 ? message.substring(0, 500) : message;
    }
    public String getVendorTxnId() { return vendorTxnId; }
    public void setVendorTxnId(String vendorTxnId) { this.vendorTxnId = vendorTxnId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
