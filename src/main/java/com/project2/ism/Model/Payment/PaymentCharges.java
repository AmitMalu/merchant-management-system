package com.project2.ism.Model.Payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payment_charges")
public class PaymentCharges {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mode_id", nullable = false)
    private PaymentMode mode;


    @Column(nullable = false)
    private Boolean status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "payoutCharge",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonIgnoreProperties("payoutCharge")
    private List<PaymentChargeSlab> slabs = new ArrayList<>();



    public PaymentCharges() {}

    public PaymentCharges(PaymentMode mode, Boolean status) {
        this.mode = mode;
        this.status = status;
    }

    // Helper method to add slab
    public void addSlab(PaymentChargeSlab slab) {
        slabs.add(slab);
        slab.setPayoutCharge(this);
    }

    // Helper method to remove slab
    public void removeSlab(PaymentChargeSlab slab) {
        slabs.remove(slab);
        slab.setPayoutCharge(null);
    }

    // Use this when updating slabs in service
    public void replaceSlabs(List<PaymentChargeSlab> newSlabs) {
        slabs.clear();
        for (PaymentChargeSlab slab : newSlabs) {
            addSlab(slab);
        }
    }

    // getters + setters...

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PaymentMode getMode() {
        return mode;
    }

    public void setMode(PaymentMode mode) {
        this.mode = mode;
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

    public List<PaymentChargeSlab> getSlabs() {
        return slabs;
    }

    public void setSlabs(List<PaymentChargeSlab> slabs) {
        this.slabs = slabs;
    }

}
