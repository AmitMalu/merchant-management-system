package com.project2.ism.Model;


import com.project2.ism.Model.InventoryTransactions.OutwardTransactions;
import com.project2.ism.Model.PricingScheme.PricingScheme;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import jakarta.persistence.*;

import java.time.LocalDate;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "customer_scheme_assignments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_franchise_product_scheme_date_range",
                        columnNames = {"franchise_id", "product_id", "scheme_id", "effective_date", "expiry_date"}
                ),
                @UniqueConstraint(
                        name = "uk_merchant_product_scheme_date_range",
                        columnNames = {"merchant_id", "product_id", "scheme_id", "effective_date", "expiry_date"}
                )
        }
)
public class CustomerSchemeAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link to Pricing Scheme
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private PricingScheme scheme;

    // Either franchise or merchant
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "franchise_id")
    private Franchise franchise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "customer_type", nullable = false)
    private String customerType; // Optional but useful: "FRANCHISE" or "MERCHANT"

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    private String remarks;

    @Column(name = "is_rate_changed", nullable = false)
    private Boolean isRateChanged = false;

    // Automatic timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // --- Lifecycle hooks for timestamps ---
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters...


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PricingScheme getScheme() {
        return scheme;
    }

    public void setScheme(PricingScheme scheme) {
        this.scheme = scheme;
    }

    public Franchise getFranchise() {
        return franchise;
    }

    public void setFranchise(Franchise franchise) {
        this.franchise = franchise;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Boolean getIsRateChanged() {
        return isRateChanged;
    }

    public void setIsRateChanged(Boolean isRateChanged) {
        this.isRateChanged = isRateChanged;
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
