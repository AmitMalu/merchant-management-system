package com.project2.ism.Repository;

import com.project2.ism.Model.Payout.PayoutTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** One vendor that has payouts (for the vendor filter of the Payout Transaction report). */
    interface PayoutVendorRow {
        Long getId();

        String getName();
    }

    @Query(value = "SELECT DISTINCT v.id AS id, v.vendor_name AS name "
            + "FROM payout_transactions p JOIN payment_vendors v ON v.id = p.vendor_id "
            + "ORDER BY v.vendor_name", nativeQuery = true)
    List<PayoutVendorRow> findPayoutVendors();

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM PayoutTransaction p
            WHERE p.ledgerMerchantTxnId = :transactionId
            """)
    Optional<PayoutTransaction> findByIdForManualUpdate(
            @Param("transactionId")
            Long payoutTransactionId
    );

    /**
     * Same as findByIdForManualUpdate, for a franchise's OWN payout: the id is the franchise
     * ledger row (franchise_transaction_details), which is a different table (and numbering)
     * from the merchant ledger.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM PayoutTransaction p
            WHERE p.ledgerFranchiseTxnId = :transactionId
            """)
    Optional<PayoutTransaction> findByFranchiseLedgerIdForManualUpdate(
            @Param("transactionId")
            Long ledgerFranchiseTxnId
    );
}
