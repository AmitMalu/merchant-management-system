package com.project2.ism.Repository;

import com.project2.ism.Model.Payout.PayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for PayoutTransaction
 */
@Repository
public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, Long> {

    Optional<PayoutTransaction> findByMerchantRefId(String merchantRefId);

    boolean existsByMerchantRefId(String merchantRefId);

    Optional<PayoutTransaction> findByVendorTxnId(String vendorTxnId);

    List<PayoutTransaction> findByInitiatorTypeAndInitiatorIdOrderByCreatedAtDesc(
            String initiatorType, Long initiatorId);

    List<PayoutTransaction> findByInitiatorTypeAndInitiatorIdAndStatusOrderByCreatedAtDesc(
            String initiatorType, Long initiatorId, PayoutTransaction.PayoutStatus status);

    @Query("SELECT p FROM PayoutTransaction p WHERE p.initiatorType = :initiatorType " +
            "AND p.initiatorId = :initiatorId AND p.createdAt BETWEEN :from AND :to " +
            "ORDER BY p.createdAt DESC")
    List<PayoutTransaction> findByInitiatorAndDateRange(
            @Param("initiatorType") String initiatorType,
            @Param("initiatorId") Long initiatorId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    List<PayoutTransaction> findByStatusAndCreatedAtBefore(
            PayoutTransaction.PayoutStatus status, LocalDateTime cutoffTime);
}
