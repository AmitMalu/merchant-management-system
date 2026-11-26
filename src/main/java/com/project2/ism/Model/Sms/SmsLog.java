package com.project2.ism.Model.Sms;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One row for every SMS attempt through the vendor, so "did the customer get it?" can be answered.
 * The mobile number is stored masked (last 4 digits only).
 */
@Entity
@Table(name = "sms_logs",
        indexes = {
                @Index(name = "idx_sms_log_ref", columnList = "template_key, ref_id"),
                @Index(name = "idx_sms_log_created", columnList = "created_at")
        })
public class SmsLog {

    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", nullable = false, length = 60)
    private String templateKey;

    /** What the SMS was about, e.g. BBPS:CC0125301466289 or CC:57. Used to never send the same SMS twice. */
    @Column(name = "ref_id", length = 100)
    private String refId;

    @Column(name = "mobile_masked", length = 20)
    private String mobileMasked;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "vendor_response", length = 500)
    private String vendorResponse;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }

    public String getMobileMasked() { return mobileMasked; }
    public void setMobileMasked(String mobileMasked) { this.mobileMasked = mobileMasked; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVendorResponse() { return vendorResponse; }
    public void setVendorResponse(String vendorResponse) { this.vendorResponse = vendorResponse; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
