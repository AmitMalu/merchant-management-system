package com.project2.ism.Model.Users;


import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Franchise extends CustomerBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Franchise name required")
    private String franchiseName;
    // Add these fields to your Franchise.java entity:

    @Column(name = "status")
    private String status = "ACTIVE";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    @OneToMany(mappedBy = "franchise", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonBackReference
    private List<Merchant> merchants = new ArrayList<>();

    // Feature flags set by admin (Merchant Settings screen). A flag that is false blocks the
    // transaction even when the menu is visible.
    //  - Franchises created before these flags existed have NULL here: payout stays allowed for
    //    them (so nothing stops working at deploy time) until an admin saves their settings;
    //    credit card bill payment is new, so NULL means "not enabled".
    //  - New franchises start with both false.
    @Column(name = "is_payout")
    private Boolean isPayout = false;

    @Column(name = "is_credit_card_bill_payment")
    private Boolean isCreditCardBillPayment = false;

    public Boolean getPayout() { return isPayout; }
    public void setPayout(Boolean payout) { this.isPayout = payout; }

    public Boolean getCreditCardBillPayment() { return isCreditCardBillPayment; }
    public void setCreditCardBillPayment(Boolean creditCardBillPayment) { this.isCreditCardBillPayment = creditCardBillPayment; }

    public Franchise() {
    }
    // Add getters and setters for these fields
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // Add these methods to automatically set timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

// =====================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFranchiseName() {
        return franchiseName;
    }

    public void setFranchiseName(String franchiseName) {
        this.franchiseName = franchiseName;
    }

    public List<Merchant> getMerchants() {
        return merchants;
    }

    public void setMerchants(List<Merchant> merchants) {
        this.merchants = merchants;
    }
}
