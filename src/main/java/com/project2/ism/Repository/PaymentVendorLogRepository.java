package com.project2.ism.Repository;

import com.project2.ism.Model.Payment.PaymentVendorLog;
import com.project2.ism.Model.Payment.PaymentVendor;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentVendorLogRepository extends JpaRepository<PaymentVendorLog, Long> {

    // Fetch log for vendor + day (NO LOCK)
    @Query("SELECT vl FROM PaymentVendorLog vl WHERE vl.paymentVendor = :vendor AND vl.logDate = :date")
    PaymentVendorLog findByVendorAndLogDate(@Param("vendor") PaymentVendor vendor,
                                            @Param("date") LocalDate date);

    // Same, but with WRITE LOCK for concurrency safety
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vl FROM PaymentVendorLog vl WHERE vl.paymentVendor = :vendor AND vl.logDate = :date")
    PaymentVendorLog findByVendorAndLogDateWithLock(@Param("vendor") PaymentVendor vendor,
                                                    @Param("date") LocalDate date);

    // Monthly aggregate: amount, count, failures per vendor
    @Query("""
           SELECT vl.paymentVendor,
                  SUM(vl.totalAmountProcessed),
                  SUM(vl.transactionCount),
                  SUM(vl.failureCount)
           FROM PaymentVendorLog vl
           WHERE vl.logDate BETWEEN :startDate AND :endDate
           GROUP BY vl.paymentVendor
           """)
    List<Object[]> getMonthlyAggregates(@Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    // Daily performance for vendor
    @Query("""
           SELECT vl FROM PaymentVendorLog vl
           WHERE vl.paymentVendor = :vendor
             AND vl.logDate BETWEEN :startDate AND :endDate
           ORDER BY vl.logDate DESC
           """)
    List<PaymentVendorLog> findByVendorAndDateRange(@Param("vendor") PaymentVendor vendor,
                                                    @Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    // NEW METHODS FOR LogService
    Page<PaymentVendorLog> findByOrderByLogDateDesc(Pageable pageable);

    Page<PaymentVendorLog> findByPaymentVendorIdOrderByLogDateDesc(Long vendorId, Pageable pageable);

    Optional<PaymentVendorLog> findByPaymentVendorIdAndLogDate(Long vendorId, LocalDate date);

    List<PaymentVendorLog> findByPaymentVendorIdAndLogDateBetweenOrderByLogDateDesc(Long vendorId, LocalDate start, LocalDate end);
}
