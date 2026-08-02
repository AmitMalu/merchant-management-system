package com.project2.ism.DTO;

import com.project2.ism.Model.PricingScheme.CardRate;

public record CardRateDTO(
        Long id,
        Long productCategoryId,
        String productCategoryName,
        String cardName,
        Double franchiseRate,
        Double merchantRate,
        String category
) {

    public static CardRateDTO fromEntity(CardRate entity) {
        return new CardRateDTO(
                entity.getId(),
                entity.getProductCategory() != null
                        ? entity.getProductCategory().getId()
                        : null,
                entity.getProductCategory() != null
                        ? entity.getProductCategory().getCategoryName()
                        : null,
                entity.getCardName(),
                entity.getFranchiseRate(),
                entity.getMerchantRate(),
                entity.getCategory()
        );
    }
}
