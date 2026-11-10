package com.project2.ism.Model.Bbps;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A complaint a merchant raised with Bill Avenue about one of their own bill payments.
 * Kept so a merchant can only track complaints that are theirs.
 */
@Entity
@Table(name = "bbps_complaints",
        indexes = {
                @Index(name = "idx_bbps_complaint_merchant", columnList = "merchant_id,created_at"),
                @Index(name = "idx_bbps_complaint_id", columnList = "complaint_id")
        })
public class BbpsComplaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "bbps_transaction_id")
    private Long bbpsTransactionId;

    /** Bill Avenue transaction id the complaint is about. */
    @Column(name = "txn_ref_id", nullable = false)
    private String txnRefId;

    @Column(name = "complaint_type", nullable = false)
    private String complaintType;

    @Column(name = "disposition", length = 255)
    private String disposition;

    @Column(name = "description", length = 255)
    private String description;

    /** Complaint id given by Bill Avenue. */
    @Column(name = "complaint_id", nullable = false)
    private String complaintId;

    @Column(name = "complaint_assigned")
    private String complaintAssigned;

    /** Last status Bill Avenue told us (for example ASSIGNED). */
    @Column(name = "status")
    private String status;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public Long getBbpsTransactionId() { return bbpsTransactionId; }
    public void setBbpsTransactionId(Long bbpsTransactionId) { this.bbpsTransactionId = bbpsTransactionId; }

    public String getTxnRefId() { return txnRefId; }
    public void setTxnRefId(String txnRefId) { this.txnRefId = txnRefId; }

    public String getComplaintType() { return complaintType; }
    public void setComplaintType(String complaintType) { this.complaintType = complaintType; }

    public String getDisposition() { return disposition; }
    public void setDisposition(String disposition) { this.disposition = disposition; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getComplaintId() { return complaintId; }
    public void setComplaintId(String complaintId) { this.complaintId = complaintId; }

    public String getComplaintAssigned() { return complaintAssigned; }
    public void setComplaintAssigned(String complaintAssigned) { this.complaintAssigned = complaintAssigned; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
}
