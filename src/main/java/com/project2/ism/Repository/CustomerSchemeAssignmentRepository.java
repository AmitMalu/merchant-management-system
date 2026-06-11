package com.project2.ism.Repository;

import com.project2.ism.Model.CustomerSchemeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CustomerSchemeAssignmentRepository extends JpaRepository<CustomerSchemeAssignment, Long> {

    // Find assignments by franchise
    List<CustomerSchemeAssignment> findByFranchiseId(Long franchiseId);

    // Find assignments by merchant
    List<CustomerSchemeAssignment> findByMerchantId(Long merchantId);

//    // Active scheme for Merchant on given date (without specific product)
//    @Query("""
//        SELECT a FROM CustomerSchemeAssignment a
//        WHERE a.merchant.id = :merchantId
//          AND a.effectiveDate <= :onDate
//          AND (a.expiryDate IS NULL OR a.expiryDate >= :onDate)
//        ORDER BY a.effectiveDate DESC
//    """)
//    Optional<CustomerSchemeAssignment> findActiveSchemeForMerchant(
//            @Param("merchantId") Long merchantId,
//            @Param("onDate") LocalDate onDate);
//
//    // Active scheme for Franchise on given date (without specific product)
//    @Query("""
//        SELECT a FROM CustomerSchemeAssignment a
//        WHERE a.franchise.id = :franchiseId
//          AND a.effectiveDate <= :onDate
//          AND (a.expiryDate IS NULL OR a.expiryDate >= :onDate)
//        ORDER BY a.effectiveDate DESC
//    """)
//    Optional<CustomerSchemeAssignment> findActiveSchemeForFranchise(
//            @Param("franchiseId") Long franchiseId,
//            @Param("onDate") LocalDate onDate);

    // Active scheme for Franchise, specific product, on given date
    @Query("""
        SELECT a FROM CustomerSchemeAssignment a
        WHERE a.franchise.id = :franchiseId
          AND a.product.id = :productId
          AND a.effectiveDate <= :onDate
          AND (a.expiryDate IS NULL OR a.expiryDate >= :onDate)
        ORDER BY a.effectiveDate DESC
    """)
    Optional<CustomerSchemeAssignment> findActiveSchemeForFranchiseAndProduct(
            @Param("franchiseId") Long franchiseId,
            @Param("productId") Long productId,
            @Param("onDate") LocalDate onDate);

    // Active scheme for Merchant, specific product, on given date
    @Query("""
        SELECT a FROM CustomerSchemeAssignment a
        WHERE a.merchant.id = :merchantId
          AND a.product.id = :productId
          AND (a.expiryDate IS NULL OR a.expiryDate >= :onDate)
        ORDER BY a.effectiveDate DESC
    """)
    Optional<CustomerSchemeAssignment> findActiveSchemeForMerchantAndProduct(
            @Param("merchantId") Long merchantId,
            @Param("productId") Long productId,
            @Param("onDate") LocalDate onDate);

    //AND a.effectiveDate <= :onDate
    // Find all active schemes on a given date
    @Query("""
        SELECT a FROM CustomerSchemeAssignment a
        WHERE a.effectiveDate <= :onDate
          AND (a.expiryDate IS NULL OR a.expiryDate >= :onDate)
        ORDER BY a.effectiveDate DESC
    """)
    List<CustomerSchemeAssignment> findAllActiveSchemes(@Param("onDate") LocalDate onDate);

    // Find schemes expiring soon (within specified days)
    @Query("""
        SELECT a FROM CustomerSchemeAssignment a
        WHERE a.expiryDate IS NOT NULL
          AND a.expiryDate BETWEEN :startDate AND :endDate
        ORDER BY a.expiryDate ASC
    """)
    List<CustomerSchemeAssignment> findSchemesExpiringBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Find by customer type (using customerType field)
    List<CustomerSchemeAssignment> findByCustomerType(String customerType);

    // Find active schemes by customer type
    @Query("""
        SELECT a FROM CustomerSchemeAssignment a
        WHERE a.customerType = :customerType
          AND a.effectiveDate <= :onDate
          AND (a.expiryDate IS NULL OR a.expiryDate >= :onDate)
        ORDER BY a.effectiveDate DESC
    """)
    List<CustomerSchemeAssignment> findActiveSchemesByCustomerType(
            @Param("customerType") String customerType,
            @Param("onDate") LocalDate onDate);

    @Query("""
        SELECT c FROM CustomerSchemeAssignment c
        WHERE c.product.id = :productId
          AND (
              (c.franchise.id = :customerId AND :customerType = 'FRANCHISE')
              OR
              (c.merchant.id = :customerId AND :customerType = 'MERCHANT')
          )
          AND (
              (c.effectiveDate <= :expiryDate OR :expiryDate IS NULL)
              AND (c.expiryDate IS NULL OR c.expiryDate >= :effectiveDate)
          )
    """)
    List<CustomerSchemeAssignment> findOverlappingAssignments(
            @Param("customerId") Long customerId,
            @Param("customerType") String customerType,
            @Param("productId") Long productId,
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("expiryDate") LocalDate expiryDate
    );


    List<CustomerSchemeAssignment> findTop5ByExpiryDateGreaterThanEqualOrderByExpiryDateAsc(LocalDate currentDate);

//----------------------------------For ProductScheme Reports------------------------------------------------



    /**
     * Fetch all scheme assignments with complete product, scheme, and customer details
     * This query uses JOIN FETCH to eagerly load all related entities
     */
    @Query("""
        SELECT DISTINCT a FROM CustomerSchemeAssignment a
        LEFT JOIN FETCH a.scheme s
        LEFT JOIN FETCH a.product p
        LEFT JOIN FETCH p.vendor
        LEFT JOIN FETCH a.franchise f
        LEFT JOIN FETCH a.merchant m
        ORDER BY a.createdAt DESC
    """)
    List<CustomerSchemeAssignment> findAllWithCompleteDetails();

    /**
     * Fetch scheme assignments filtered by customer type
     */
    @Query("""
        SELECT DISTINCT a FROM CustomerSchemeAssignment a
        LEFT JOIN FETCH a.scheme s
        LEFT JOIN FETCH a.product p
        LEFT JOIN FETCH p.vendor
        LEFT JOIN FETCH a.franchise f
        LEFT JOIN FETCH a.merchant m
        WHERE a.customerType = :customerType
        ORDER BY a.createdAt DESC
    """)
    List<CustomerSchemeAssignment> findAllWithCompleteDetailsByCustomerType(@Param("customerType") String customerType);

    /**
     * Fetch scheme assignments for a specific product
     */
    @Query("""
        SELECT DISTINCT a FROM CustomerSchemeAssignment a
        LEFT JOIN FETCH a.scheme s
        LEFT JOIN FETCH a.product p
        LEFT JOIN FETCH p.productCategory
        LEFT JOIN FETCH p.vendor
        LEFT JOIN FETCH a.franchise f
        LEFT JOIN FETCH a.merchant m
        WHERE p.id = :productId
        ORDER BY a.createdAt DESC
    """)
    List<CustomerSchemeAssignment> findAllWithCompleteDetailsByProduct(@Param("productId") Long productId);

    /**
     * Fetch scheme assignments for a specific scheme
     */
    @Query("""
        SELECT DISTINCT a FROM CustomerSchemeAssignment a
        LEFT JOIN FETCH a.scheme s
        LEFT JOIN FETCH a.product p
        LEFT JOIN FETCH p.productCategory
        LEFT JOIN FETCH p.vendor
        LEFT JOIN FETCH a.franchise f
        LEFT JOIN FETCH a.merchant m
        WHERE s.id = :schemeId
        ORDER BY a.createdAt DESC
    """)
    List<CustomerSchemeAssignment> findAllWithCompleteDetailsByScheme(@Param("schemeId") Long schemeId);

    /**
     * Fetch active scheme assignments (not expired)
     */
    @Query("""
        SELECT DISTINCT a FROM CustomerSchemeAssignment a
        LEFT JOIN FETCH a.scheme s
        LEFT JOIN FETCH a.product p
        LEFT JOIN FETCH p.productCategory
        LEFT JOIN FETCH p.vendor
        LEFT JOIN FETCH a.franchise f
        LEFT JOIN FETCH a.merchant m
        WHERE a.effectiveDate <= :currentDate
        AND (a.expiryDate IS NULL OR a.expiryDate >= :currentDate)
        ORDER BY a.createdAt DESC
    """)
    List<CustomerSchemeAssignment> findAllActiveWithCompleteDetails(@Param("currentDate") LocalDate currentDate);

    /**
     * Fetch scheme assignments with filters
     */
    @Query("""
        SELECT DISTINCT a FROM CustomerSchemeAssignment a
        LEFT JOIN FETCH a.scheme s
        LEFT JOIN FETCH a.product p
        LEFT JOIN FETCH p.productCategory pc
        LEFT JOIN FETCH p.vendor v
        LEFT JOIN FETCH a.franchise f
        LEFT JOIN FETCH a.merchant m
        WHERE (:customerType IS NULL OR a.customerType = :customerType)
        AND (:productId IS NULL OR p.id = :productId)
        AND (:schemeId IS NULL OR s.id = :schemeId)
        AND (:categoryId IS NULL OR pc.id = :categoryId)
        AND (:activeOnly = false OR (a.effectiveDate <= :currentDate AND (a.expiryDate IS NULL OR a.expiryDate >= :currentDate)))
        ORDER BY a.createdAt DESC
    """)
    List<CustomerSchemeAssignment> findAllWithCompleteDetailsFiltered(
            @Param("customerType") String customerType,
            @Param("productId") Long productId,
            @Param("schemeId") Long schemeId,
            @Param("categoryId") Long categoryId,
            @Param("activeOnly") boolean activeOnly,
            @Param("currentDate") LocalDate currentDate
    );

}