package com.project2.ism.Model.Payment;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "payment_vendor_routing",
        uniqueConstraints = @UniqueConstraint(columnNames = {"payout_product_id"})
)
public class PaymentVendorRouting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_product_id", nullable = false)
    private PaymentProduct paymentProduct;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_vendor1_id", nullable = false)
    private PaymentVendor paymentVendor1;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_vendor2_id", nullable = false)
    private PaymentVendor paymentVendor2;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_vendor3_id", nullable = false)
    private PaymentVendor paymentVendor3;

    @OneToMany(mappedBy = "paymentVendorRouting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentVendorRule> paymentVendorRules = new ArrayList<>();

    @Column(nullable = false)
    private Boolean status = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void addVendorRule(PaymentVendorRule rule) {
        paymentVendorRules.add(rule);
        rule.setPaymentVendorRouting(this);
    }

    public void removeVendorRule(PaymentVendorRule rule) {
        paymentVendorRules.remove(rule);
        rule.setPaymentVendorRouting(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PaymentProduct getPayoutProduct() {
        return paymentProduct;
    }

    public void setPayoutProduct(PaymentProduct paymentProduct) {
        this.paymentProduct = paymentProduct;
    }

    public PaymentVendor getPaymentVendor1() {
        return paymentVendor1;
    }

    public void setPaymentVendor1(PaymentVendor paymentVendor1) {
        this.paymentVendor1 = paymentVendor1;
    }

    public PaymentVendor getPaymentVendor2() {
        return paymentVendor2;
    }

    public void setPaymentVendor2(PaymentVendor paymentVendor2) {
        this.paymentVendor2 = paymentVendor2;
    }

    public PaymentVendor getPaymentVendor3() {
        return paymentVendor3;
    }

    public void setPaymentVendor3(PaymentVendor paymentVendor3) {
        this.paymentVendor3 = paymentVendor3;
    }

    public List<PaymentVendorRule> getPaymentVendorRules() {
        return paymentVendorRules;
    }

    public void setPaymentVendorRules(List<PaymentVendorRule> paymentVendorRules) {
        this.paymentVendorRules = paymentVendorRules;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
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
