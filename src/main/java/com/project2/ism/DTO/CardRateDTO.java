package com.project2.ism.DTO;

import com.project2.ism.Model.PricingScheme.CardRate;

public record CardRateDTO(
        Long id,
        String cardName,
        Double franchiseRate,
        Double merchantRate,
        Double rate
) {
    public static CardRateDTO fromEntity(CardRate entity) {
        return new CardRateDTO(
                entity.getId(),
                entity.getCardName(),
                entity.getFranchiseRate(),
                entity.getMerchantRate(),
                entity.getRate()
        );
    }
}
