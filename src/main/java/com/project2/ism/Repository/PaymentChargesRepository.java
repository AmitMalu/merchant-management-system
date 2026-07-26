package com.project2.ism.Repository;


import com.project2.ism.Enum.RequestedType;
import com.project2.ism.Model.Payment.PaymentCharges;
import com.project2.ism.Model.Payment.PaymentMode;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentChargesRepository extends JpaRepository<PaymentCharges, Long> {

    // Check if mode already exists (using entity reference)
    boolean existsByMode(PaymentMode mode);

    // Check if mode exists excluding a specific charge ID
    @Query("SELECT CASE WHEN COUNT(pc) > 0 THEN true ELSE false END " +
            "FROM PaymentCharges pc WHERE pc.mode = :mode AND pc.id <> :id")
    boolean existsByModeAndIdNot(@Param("mode") PaymentMode mode, @Param("id") Long id);

    // Find charges by mode
    Optional<PaymentCharges> findByMode(PaymentMode mode);

    // Search by mode description
    @Query("SELECT pc FROM PaymentCharges pc " +
            "WHERE LOWER(pc.mode.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<PaymentCharges> searchByMode(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Get all active charges
    Page<PaymentCharges> findByStatus(Boolean status, Pageable pageable);

    // Count by status
    long countByStatus(Boolean status);

    Optional<PaymentCharges> findByModeAndStatusTrue(PaymentMode mode);

    List<PaymentCharges> findByStatusTrue();

    boolean existsByModeAndChargeScopeAndMerchantIsNullAndFranchiseIsNull(
            PaymentMode mode,
            RequestedType chargeScope
    );

    boolean existsByModeAndChargeScopeAndMerchantAndFranchiseIsNull(
            PaymentMode mode,
            RequestedType chargeScope,
            Merchant merchant
    );

    boolean existsByModeAndChargeScopeAndMerchantIsNullAndFranchise(
            PaymentMode mode,
            RequestedType chargeScope,
            Franchise franchise
    );

    boolean existsByModeAndChargeScopeAndMerchantAndFranchise(
            PaymentMode mode,
            RequestedType chargeScope,
            Merchant merchant,
            Franchise franchise
    );

    @Query("""
            SELECT DISTINCT pc
            FROM PaymentCharges pc
            LEFT JOIN FETCH pc.mode
            LEFT JOIN FETCH pc.merchant
            LEFT JOIN FETCH pc.franchise
            LEFT JOIN FETCH pc.slabs
            WHERE pc.id = :id
            """)
    Optional<PaymentCharges> findByIdWithDetails(
            @Param("id") Long id
    );

    @Query("""
SELECT DISTINCT pc
FROM PaymentCharges pc
LEFT JOIN pc.mode mode
LEFT JOIN pc.merchant merchant
LEFT JOIN pc.franchise franchise
WHERE
    LOWER(mode.code) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
    OR LOWER(mode.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
    OR LOWER(CAST(pc.chargeScope AS string)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
    OR LOWER(merchant.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
    OR LOWER(franchise.franchiseName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
""")
    Page<PaymentCharges> searchPaymentCharges(
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );

    boolean existsByModeAndChargeScopeAndMerchantIsNullAndFranchiseIsNullAndIdNot(
            PaymentMode mode,
            RequestedType chargeScope,
            Long id
    );

    boolean existsByModeAndChargeScopeAndMerchantAndFranchiseIsNullAndIdNot(
            PaymentMode mode,
            RequestedType chargeScope,
            Merchant merchant,
            Long id
    );

    boolean existsByModeAndChargeScopeAndMerchantIsNullAndFranchiseAndIdNot(
            PaymentMode mode,
            RequestedType chargeScope,
            Franchise franchise,
            Long id
    );

    boolean existsByModeAndChargeScopeAndMerchantAndFranchiseAndIdNot(
            PaymentMode mode,
            RequestedType chargeScope,
            Merchant merchant,
            Franchise franchise,
            Long id
    );

    @Query("""
        SELECT DISTINCT pc
        FROM PaymentCharges pc
        LEFT JOIN FETCH pc.slabs
        WHERE pc.mode = :mode
          AND pc.status = true
          AND pc.chargeScope = :scope
          AND pc.merchant IS NULL
          AND pc.franchise IS NULL
        """)
    Optional<PaymentCharges> findActiveGlobalCharge(
            @Param("mode") PaymentMode mode,
            @Param("scope") RequestedType scope
    );

    @Query("""
        SELECT DISTINCT pc
        FROM PaymentCharges pc
        LEFT JOIN FETCH pc.slabs
        WHERE pc.mode = :mode
          AND pc.status = true
          AND pc.chargeScope = :scope
          AND pc.merchant = :merchant
          AND pc.franchise = :franchise
        """)
    Optional<PaymentCharges> findActiveFranchiseMerchantCharge(
            @Param("mode") PaymentMode mode,
            @Param("scope") RequestedType scope,
            @Param("merchant") Merchant merchant,
            @Param("franchise") Franchise franchise
    );

    @Query("""
        SELECT DISTINCT pc
        FROM PaymentCharges pc
        LEFT JOIN FETCH pc.slabs
        WHERE pc.mode = :mode
          AND pc.status = true
          AND pc.chargeScope = :scope
          AND pc.franchise = :franchise
          AND pc.merchant IS NULL
        """)
    Optional<PaymentCharges> findActiveFranchiseCharge(
            @Param("mode") PaymentMode mode,
            @Param("scope") RequestedType scope,
            @Param("franchise") Franchise franchise
    );

    @Query("""
        SELECT DISTINCT pc
        FROM PaymentCharges pc
        LEFT JOIN FETCH pc.slabs
        WHERE pc.mode = :mode
          AND pc.status = true
          AND pc.chargeScope = :scope
          AND pc.merchant = :merchant
          AND pc.franchise IS NULL
        """)
    Optional<PaymentCharges> findActiveDirectMerchantCharge(
            @Param("mode") PaymentMode mode,
            @Param("scope") RequestedType scope,
            @Param("merchant") Merchant merchant
    );
}

