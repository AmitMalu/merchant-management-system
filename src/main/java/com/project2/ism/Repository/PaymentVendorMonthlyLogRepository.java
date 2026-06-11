package com.project2.ism.Repository;

import com.project2.ism.Model.Payment.PaymentVendorMonthlyLog;
import com.project2.ism.Model.Payment.PaymentVendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentVendorMonthlyLogRepository extends JpaRepository<PaymentVendorMonthlyLog, Long> {

    Optional<PaymentVendorMonthlyLog> findByPaymentVendorAndYearAndMonth(
            PaymentVendor vendor,
            Integer year,
            Integer month
    );

    // Get vendor performance for a full year (12 monthly logs)
    @Query("""
           SELECT vml FROM PaymentVendorMonthlyLog vml
           WHERE vml.paymentVendor = :vendor AND vml.year = :year
           ORDER BY vml.month
           """)
    List<PaymentVendorMonthlyLog> findByVendorAndYear(@Param("vendor") PaymentVendor vendor,
                                                      @Param("year") Integer year);

    // Compare all vendors in a given month
    @Query("""
           SELECT vml FROM PaymentVendorMonthlyLog vml
           WHERE vml.year = :year AND vml.month = :month
           ORDER BY vml.totalAmountProcessed DESC
           """)
    List<PaymentVendorMonthlyLog> findByYearAndMonth(@Param("year") Integer year,
                                                     @Param("month") Integer month);

    // NEW METHODS FOR LogService
    Page<PaymentVendorMonthlyLog> findByOrderByYearDescMonthDesc(Pageable pageable);

    Page<PaymentVendorMonthlyLog> findByPaymentVendorIdOrderByYearDescMonthDesc(Long vendorId, Pageable pageable);

    Optional<PaymentVendorMonthlyLog> findByPaymentVendorIdAndYearAndMonth(Long vendorId, Integer year, Integer month);

    List<PaymentVendorMonthlyLog> findByPaymentVendorIdAndYearOrderByMonthDesc(Long vendorId, Integer year);
}
