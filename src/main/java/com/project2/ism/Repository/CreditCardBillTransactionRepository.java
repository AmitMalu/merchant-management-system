package com.project2.ism.Repository;

import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every lookup is by customer type (MERCHANT / FRANCHISE) AND id, because merchant ids and
 * franchise ids overlap. The id column is still called merchant_id.
 */
@Repository
public interface CreditCardBillTransactionRepository extends JpaRepository<CreditCardBillTransaction, Long> {

    /** Row lock, so two clicks / two tabs can never pay the same fetched bill twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CreditCardBillTransaction t where t.requestId = :requestId " +
            "and t.customerType = :customerType and t.merchantId = :customerId")
    Optional<CreditCardBillTransaction> findByRequestIdForUpdate(@Param("requestId") String requestId,
                                                                 @Param("customerType") String customerType,
                                                                 @Param("customerId") Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CreditCardBillTransaction t where t.id = :id")
    Optional<CreditCardBillTransaction> findByIdForUpdate(@Param("id") Long id);

    @Query("select t from CreditCardBillTransaction t where t.id = :id " +
            "and t.customerType = :customerType and t.merchantId = :customerId")
    Optional<CreditCardBillTransaction> findOwned(@Param("id") Long id,
                                                  @Param("customerType") String customerType,
                                                  @Param("customerId") Long customerId);

    /** Same as findByRequestIdForUpdate, without the lock (for a quote, which changes nothing). */
    @Query("select t from CreditCardBillTransaction t where t.requestId = :requestId " +
            "and t.customerType = :customerType and t.merchantId = :customerId")
    Optional<CreditCardBillTransaction> findOwnedByRequestId(@Param("requestId") String requestId,
                                                             @Param("customerType") String customerType,
                                                             @Param("customerId") Long customerId);

    /** A bill for the same card that was fetched a moment ago and not paid yet (see fetch-bill reuse). */
    @Query("select t from CreditCardBillTransaction t where t.customerType = :customerType " +
            "and t.merchantId = :customerId and t.billerId = :billerId and t.cardLast4 = :cardLast4 " +
            "and t.customerMobile = :mobile and t.status = :status and t.fetchedAt > :fetchedAfter " +
            "order by t.fetchedAt desc")
    List<CreditCardBillTransaction> findRecentFetched(@Param("customerType") String customerType,
                                                      @Param("customerId") Long customerId,
                                                      @Param("billerId") String billerId,
                                                      @Param("cardLast4") String cardLast4,
                                                      @Param("mobile") String mobile,
                                                      @Param("status") CreditCardBillTransaction.Status status,
                                                      @Param("fetchedAfter") LocalDateTime fetchedAfter,
                                                      Pageable pageable);

    /**
     * Payments (still pending or already successful) made after `since` for the same
     * biller, card, mobile and amount: the "same payment twice" check. Read with a lock so
     * it sees the latest committed rows even when two requests arrive at the same moment.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from CreditCardBillTransaction t where t.customerType = :customerType " +
            "and t.merchantId = :customerId " +
            "and t.billerId = :billerId and t.cardLast4 = :cardLast4 " +
            "and t.customerMobile = :mobile and coalesce(t.paidAmount, t.billAmount) = :amount " +
            "and t.status in :statuses and t.paidAt >= :since and t.id <> :excludeId " +
            "order by t.paidAt desc")
    List<CreditCardBillTransaction> findRecentSimilarPayments(
            @Param("customerType") String customerType,
            @Param("customerId") Long customerId,
            @Param("billerId") String billerId,
            @Param("cardLast4") String cardLast4,
            @Param("mobile") String mobile,
            @Param("amount") BigDecimal amount,
            @Param("statuses") Collection<CreditCardBillTransaction.Status> statuses,
            @Param("since") LocalDateTime since,
            @Param("excludeId") Long excludeId);

    /** Own payments, newest first. Pass FETCHED as `excluded` to hide bills that were only looked up. */
    @Query("select t from CreditCardBillTransaction t where t.customerType = :customerType " +
            "and t.merchantId = :customerId and t.status <> :excluded order by t.createdAt desc")
    List<CreditCardBillTransaction> findHistory(@Param("customerType") String customerType,
                                                @Param("customerId") Long customerId,
                                                @Param("excluded") CreditCardBillTransaction.Status excluded,
                                                Pageable pageable);

    // ---- Admin dashboard (credit card payments through SuivrePay). A FETCHED row is only a bill lookup:
    // it is not a payment, so it is left out. Rows are Object[]: see AdminPaymentsTodayService.

    /** Per status: [status, count, amount, charges] for credit card payments created in [from, to). */
    @Query("select t.status, count(t), coalesce(sum(coalesce(t.paidAmount, t.billAmount)), 0), coalesce(sum(t.charges), 0) "
            + "from CreditCardBillTransaction t where t.createdAt >= :from and t.createdAt < :to "
            + "and t.status <> :excluded group by t.status")
    List<Object[]> dashboardTotalsByStatus(@Param("excluded") CreditCardBillTransaction.Status excluded,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Most common failure messages: [message, count], most common first (pass a page to limit). */
    @Query("select t.responseMessage, count(t) from CreditCardBillTransaction t "
            + "where t.status = :status "
            + "and t.createdAt >= :from and t.createdAt < :to and t.responseMessage is not null "
            + "group by t.responseMessage order by count(t) desc")
    List<Object[]> dashboardFailureReasons(@Param("status") CreditCardBillTransaction.Status status,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                           Pageable page);

    /** Credit card payments still PENDING that were created before the cut-off (any day). */
    @Query("select count(t) from CreditCardBillTransaction t "
            + "where t.status = :status and t.createdAt < :cutoff")
    long dashboardStuckPending(@Param("status") CreditCardBillTransaction.Status status,
                               @Param("cutoff") LocalDateTime cutoff);

}
