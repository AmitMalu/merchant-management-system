
package com.project2.ism.Model.PricingScheme;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "card_rates")
public class CardRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_name", nullable = false)
    private String cardName;

    @Column(name = "rate")
    private Double rate;//-for direct merchant

    @Column(name = "franchise_rate")
    private Double franchiseRate;

    @Column(name = "merchant_rate")
    private Double merchantRate;//merchant belonging to franchise

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_scheme_id")
    @JsonBackReference
    private PricingScheme pricingScheme;

    @Column(name = "category")
    private String category;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCardName() {
        return cardName;
    }

    public void setCardName(String cardName) {
        this.cardName = cardName;
    }

    public Double getRate() {
        return rate;
    }

    public void setRate(Double rate) {
        this.rate = rate;
    }

    public Double getFranchiseRate() {
        return franchiseRate;
    }

    public void setFranchiseRate(Double franchiseRate) {
        this.franchiseRate = franchiseRate;
    }

    public Double getMerchantRate() {
        return merchantRate;
    }

    public void setMerchantRate(Double merchantRate) {
        this.merchantRate = merchantRate;
    }

    public PricingScheme getPricingScheme() {
        return pricingScheme;
    }

    public void setPricingScheme(PricingScheme pricingScheme) {
        this.pricingScheme = pricingScheme;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}

