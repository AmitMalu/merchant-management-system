package com.project2.ism.Model.Bbps;

import com.project2.ism.Model.Payment.PaymentVendor;
import jakarta.persistence.*;

import java.time.LocalDateTime;
@Entity
@Table(name = "bill_avenue_config")
public class BillAvenueConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_vendor_id", nullable = false)
    private PaymentVendor paymentVendor;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(name = "provider_name", nullable = false, length = 200)
    private String providerName;

    @Lob
    @Column(name = "raw_config", columnDefinition = "JSON")
    private String rawConfig;    // Full config in JSON string

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Auto-set timestamps
    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PaymentVendor getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(PaymentVendor paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getRawConfig() {
        return rawConfig;
    }

    public void setRawConfig(String rawConfig) {
        this.rawConfig = rawConfig;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
