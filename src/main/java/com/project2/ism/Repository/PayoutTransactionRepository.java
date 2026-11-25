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

    // Locked reads: a vendor callback and the end of a payout (V2) must not overwrite each other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayoutTransaction p WHERE p.id = :id")
    Optional<PayoutTransaction> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayoutTransaction p WHERE p.merchantRefId = :ref")
    Optional<PayoutTransaction> findByMerchantRefIdForUpdate(@Param("ref") String ref);

    /** One vendor that has payouts (for the vendor filter of the Payout Transaction report). */
    interface PayoutVendorRow {
        Long getId();

        String getName();
    }

    @Query(value = "SELECT DISTINCT v.id AS id, v.vendor_name AS name "
            + "FROM payout_transactions p JOIN payment_vendors v ON v.id = p.vendor_id "
            + "ORDER BY v.vendor_name", nativeQuery = true)
    List<PayoutVendorRow> findPayoutVendors();

    /** What one vendor has taken today: how many payouts and how much. Pending and successful ones count. */
    interface DailyUsage {
        Long getCount();

        java.math.BigDecimal getTotal();
    }

    @Query("SELECT COUNT(p) AS count, SUM(p.amount) AS total FROM PayoutTransaction p "
            + "WHERE p.vendorId = :vendorId AND p.createdAt >= :from AND p.status IN :statuses")
    DailyUsage dailyUsage(@Param("vendorId") Long vendorId, @Param("from") LocalDateTime from,
                          @Param("statuses") java.util.Collection<PayoutTransaction.PayoutStatus> statuses);

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

    // ---- Admin dashboard (Payout today). Rows are Object[]: see AdminPaymentsTodayService.

    /** Per status: [status, count, amount, charges] for payouts created in [from, to). */
    @Query("select p.status, count(p), coalesce(sum(p.amount), 0), coalesce(sum(p.charges), 0) "
            + "from PayoutTransaction p where p.createdAt >= :from and p.createdAt < :to group by p.status")
    List<Object[]> dashboardTotalsByStatus(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Per vendor and status: [vendorId, status, count, amount] for payouts created in [from, to). */
    @Query("select p.vendorId, p.status, count(p), coalesce(sum(p.amount), 0) "
            + "from PayoutTransaction p where p.createdAt >= :from and p.createdAt < :to group by p.vendorId, p.status")
    List<Object[]> dashboardTotalsByVendor(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Most common failure messages: [message, count], most common first (pass a page to limit). */
    @Query("select p.responseMessage, count(p) from PayoutTransaction p "
            + "where p.status = :status "
            + "and p.createdAt >= :from and p.createdAt < :to and p.responseMessage is not null "
            + "group by p.responseMessage order by count(p) desc")
    List<Object[]> dashboardFailureReasons(@Param("status") PayoutTransaction.PayoutStatus status,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                           org.springframework.data.domain.Pageable page);

    /** Payouts still PENDING that were created before the cut-off (any day). */
    @Query("select count(p) from PayoutTransaction p "
            + "where p.status = :status and p.createdAt < :cutoff")
    long dashboardStuckPending(@Param("status") PayoutTransaction.PayoutStatus status,
                               @Param("cutoff") LocalDateTime cutoff);

}
