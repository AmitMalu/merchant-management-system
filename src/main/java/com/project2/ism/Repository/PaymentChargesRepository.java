package com.project2.ism.Repository;


import com.project2.ism.Model.Payment.PaymentCharges;
import com.project2.ism.Model.Payment.PaymentMode;
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
}

