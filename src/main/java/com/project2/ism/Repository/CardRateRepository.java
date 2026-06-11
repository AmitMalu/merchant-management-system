package com.project2.ism.Repository;

import com.project2.ism.Model.PricingScheme.CardRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardRateRepository extends JpaRepository<CardRate, Long> {
    Optional<CardRate> findByPricingScheme_IdAndCardNameIgnoreCase(Long schemeId, String cardName);
    Optional<CardRate> findByPricingScheme_IdAndCardNameContainingIgnoreCase(Long schemeId, String partOfCardName);


}
