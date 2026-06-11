package com.project2.ism.Model.Vendor;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Entity
@Table(name = "vendor_card_rates")
public class VendorCardRates {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "card type required")
    private String cardType;

    @NotNull(message = "card rate required ")
    @DecimalMin(value = "0.0", inclusive = true, message = "Rate must be positive")
    @DecimalMax(value = "100.0", inclusive = true, message = "Rate must be less than or equal to 100")
    private BigDecimal rate;

    @ManyToOne
    @JoinColumn(name = "vendor_rate_id", nullable = false)
    @JsonBackReference
    private VendorRates vendorRates;

    public VendorCardRates() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public VendorRates getVendorRates() {
        return vendorRates;
    }

    public void setVendorRates(VendorRates vendorRates) {
        this.vendorRates = vendorRates;
    }
}