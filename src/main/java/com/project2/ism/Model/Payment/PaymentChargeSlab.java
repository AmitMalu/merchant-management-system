package com.project2.ism.Model.Payment;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project2.ism.Enum.ChargeType;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "payment_charge_slabs")
public class PaymentChargeSlab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "min_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 20)
    private ChargeType chargeType;

    @Column(name = "charge_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal chargeValue;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_charge_id", nullable = false)
    private PaymentCharges payoutCharge;

    // Enum for charge type


    // Constructor - no args
    public PaymentChargeSlab() {
    }

    // Constructor - with fields
    public PaymentChargeSlab(BigDecimal minAmount, BigDecimal maxAmount,
                             ChargeType chargeType, BigDecimal chargeValue) {

        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chargeType = chargeType;
        this.chargeValue = chargeValue;
    }

    // Getters and Setters - add these yourself

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public ChargeType getChargeType() {
        return chargeType;
    }

    public void setChargeType(ChargeType chargeType) {
        this.chargeType = chargeType;
    }

    public BigDecimal getChargeValue() {
        return chargeValue;
    }

    public void setChargeValue(BigDecimal chargeValue) {
        this.chargeValue = chargeValue;
    }

    public PaymentCharges getPayoutCharge() {
        return payoutCharge;
    }

    public void setPayoutCharge(PaymentCharges payoutCharge) {
        this.payoutCharge = payoutCharge;
    }
}