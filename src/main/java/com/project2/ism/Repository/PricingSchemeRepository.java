package com.project2.ism.Repository;


import com.project2.ism.Model.PricingScheme.PricingScheme;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingSchemeRepository extends JpaRepository<PricingScheme, Long> {

    Optional<PricingScheme> findBySchemeCode(String schemeCode);

    boolean existsBySchemeCode(String schemeCode);

    @Query("SELECT ps FROM PricingScheme ps WHERE " +
            "LOWER(ps.schemeCode) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(ps.description) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(ps.customerType) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<PricingScheme> searchSchemes(@Param("query") String query, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(ps) > 0 THEN true ELSE false END FROM PricingScheme ps " +
            "WHERE ps.schemeCode = :schemeCode AND ps.rentalByMonth = :rentalByMonth " +
            "AND ps.customerType = :customerType AND ps.id != :excludeId")
    boolean existsDuplicateScheme(@Param("schemeCode") String schemeCode,
                                  @Param("rentalByMonth") Double rentalByMonth,
                                  @Param("customerType") String customerType,
                                  @Param("excludeId") Long excludeId);

    Optional<PricingScheme> findTopByOrderBySchemeCodeDesc();

    // Repository method
    @Query("SELECT p.customerType, COUNT(p) FROM PricingScheme p GROUP BY p.customerType")
    List<Object[]> countByCustomerType();

    @Query("""
    SELECT DISTINCT ps
    FROM PricingScheme ps
    LEFT JOIN FETCH ps.cardRates cr
    LEFT JOIN FETCH cr.productCategory
    WHERE ps.id = :id
""")
    Optional<PricingScheme> findByIdWithCardRatesAndProductCategory(
            @Param("id") Long id
    );

    @Query("""
    SELECT DISTINCT ps
    FROM PricingScheme ps
    JOIN FETCH ps.cardRates cr
    JOIN FETCH cr.productCategory pc
    WHERE pc.id = :productCategoryId
      AND UPPER(ps.customerType) = UPPER(:customerType)
""")
    List<PricingScheme> findValidSchemesByProductCategoryAndCustomerType(
            @Param("productCategoryId") Long productCategoryId,
            @Param("customerType") String customerType
    );

    @Query(
            value = """
                SELECT DISTINCT ps
                FROM PricingScheme ps
                LEFT JOIN FETCH ps.cardRates cr
                LEFT JOIN FETCH cr.productCategory
                """,
            countQuery = """
                SELECT COUNT(ps)
                FROM PricingScheme ps
                """
    )
    Page<PricingScheme> findAllWithCardRatesAndProductCategory(
            Pageable pageable
    );
}