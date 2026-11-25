package com.project2.ism.Repository;

import com.project2.ism.Model.Bbps.BbpsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author SHUBHAM KHOPADE
 */
public interface BbpsTransactionRepository extends JpaRepository<BbpsTransaction, Long> {

    Optional<BbpsTransaction> findByRequestId(String requestId);

    Optional<BbpsTransaction> findByTxnRefId(String txnRefId);

    List<BbpsTransaction> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    // The merchant's own latest successful payments (they have a Bill Avenue transaction id)
    List<BbpsTransaction> findTop50ByMerchantIdAndStatusAndTxnRefIdIsNotNullOrderByCreatedAtDesc(
            Long merchantId, BbpsTransaction.BbpsStatus status);

    // Used by the Transaction Monitoring stuck-pending sweep
    List<BbpsTransaction> findByStatusAndCreatedAtBefore(
            BbpsTransaction.BbpsStatus status, LocalDateTime cutoffTime);

    // ---- Admin dashboard (Bill payment today). Rows are Object[]: see AdminPaymentsTodayService.

    /** Per status: [status, count, amount, charges] for bill payments created in [from, to). */
    @Query("select t.status, count(t), coalesce(sum(t.amount), 0), coalesce(sum(t.charges), 0) "
            + "from BbpsTransaction t where t.createdAt >= :from and t.createdAt < :to group by t.status")
    List<Object[]> dashboardTotalsByStatus(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Most common failure messages: [message, count], most common first (pass a page to limit). */
    @Query("select t.responseMessage, count(t) from BbpsTransaction t "
            + "where t.status = :status "
            + "and t.createdAt >= :from and t.createdAt < :to and t.responseMessage is not null "
            + "group by t.responseMessage order by count(t) desc")
    List<Object[]> dashboardFailureReasons(@Param("status") BbpsTransaction.BbpsStatus status,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                           org.springframework.data.domain.Pageable page);

    /** Bill payments still PENDING that were created before the cut-off (any day). */
    @Query("select count(t) from BbpsTransaction t "
            + "where t.status = :status and t.createdAt < :cutoff")
    long dashboardStuckPending(@Param("status") BbpsTransaction.BbpsStatus status,
                               @Param("cutoff") LocalDateTime cutoff);

}
