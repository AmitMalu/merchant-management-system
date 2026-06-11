package com.project2.ism.DTO;

import com.project2.ism.Model.PricingScheme.PricingScheme;

import java.util.List;

public record PricingSchemeDTO(
        Long id,
        String schemeCode,
        Double rentalByMonth,
        String customerType,
        String description,
        Long productCategoryId,
        String productCategoryName,
        List<CardRateDTO> cardRates
) {
    public static PricingSchemeDTO fromEntity(PricingScheme entity) {
        return new PricingSchemeDTO(
                entity.getId(),
                entity.getSchemeCode(),
                entity.getRentalByMonth(),
                entity.getCustomerType(),
                entity.getDescription(),
                entity.getProductCategory().getId(),
                entity.getProductCategory().getCategoryName(),
                entity.getCardRates().stream()
                        .map(CardRateDTO::fromEntity)
                        .toList()
        );
    }
}
