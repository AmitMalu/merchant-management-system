package com.project2.ism.Repository;

import com.project2.ism.Model.PricingScheme.CardRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardRateRepository extends JpaRepository<CardRate, Long> {
    Optional<CardRate> findByPricingScheme_IdAndCardNameIgnoreCase(Long schemeId, String cardName);
    Optional<CardRate> findByPricingScheme_IdAndCardNameContainingIgnoreCase(Long schemeId, String partOfCardName);

    /**
     * Exact lookup: scheme + product category + card name (case-insensitive).
     * Used when the product category is resolved from the transaction's payment gateway.
     */
    Optional<CardRate> findByPricingScheme_IdAndProductCategory_IdAndCardNameIgnoreCase(
            Long schemeId, Long productCategoryId, String cardName);

    /**
     * Fallback: scheme + product category + card name containing (for partial match like "DEFAULT").
     */
    Optional<CardRate> findByPricingScheme_IdAndProductCategory_IdAndCardNameContainingIgnoreCase(
            Long schemeId, Long productCategoryId, String partOfCardName);

    @Modifying
    @Query("DELETE FROM CardRate c WHERE c.pricingScheme.id = :pricingSchemeId")
    void deleteByPricingSchemeId(@Param("pricingSchemeId") Long pricingSchemeId);
}
