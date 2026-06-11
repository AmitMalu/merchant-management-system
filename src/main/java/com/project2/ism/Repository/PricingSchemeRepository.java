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



    @Query("SELECT CASE WHEN COUNT(ps) > 0 THEN true ELSE false END FROM PricingScheme ps " +
            "WHERE ps.schemeCode = :schemeCode AND ps.rentalByMonth = :rentalByMonth " +
            "AND ps.customerType = :customerType")
    boolean existsDuplicateSchemeForNew(@Param("schemeCode") String schemeCode,
                                        @Param("rentalByMonth") Double rentalByMonth,
                                        @Param("customerType") String customerType);

    Optional<PricingScheme> findTopByOrderBySchemeCodeDesc();


    List<PricingScheme> findByProductCategory_IdAndCustomerType(Long productCategoryId, String customerType);

    // Repository method
    @Query("SELECT p.customerType, COUNT(p) FROM PricingScheme p GROUP BY p.customerType")
    List<Object[]> countByCustomerType();
}