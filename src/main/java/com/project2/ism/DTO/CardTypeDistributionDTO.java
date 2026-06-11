package com.project2.ism.DTO;

import java.math.BigDecimal;

public class CardTypeDistributionDTO {
    private String cardType;
    private Long count;
    private BigDecimal percentage;

    public CardTypeDistributionDTO(String cardType, Long count, BigDecimal percentage) {
        this.cardType = cardType;
        this.count = count;
        this.percentage = percentage;
    }

    // Getters and Setters
    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }
}
