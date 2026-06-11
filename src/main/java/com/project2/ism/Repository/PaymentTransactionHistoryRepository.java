package com.project2.ism.Repository;

import com.project2.ism.Model.Payment.PaymentTransactionHistory;
import com.project2.ism.Model.Payment.PaymentVendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentTransactionHistoryRepository extends JpaRepository<PaymentTransactionHistory, Long> {
    // Fetch last 5 transactions for failure pattern detection
    List<PaymentTransactionHistory> findTop5ByPaymentVendorOrderByCreatedAtDesc(PaymentVendor vendor);

    // Cleanup old logs periodically
    @Modifying
    @Query("""
           DELETE FROM PaymentTransactionHistory th
           WHERE th.createdAt < :cutoffDate
           """)
    void deleteOldRecords(@Param("cutoffDate") LocalDateTime cutoffDate);

}