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
            "and t.customerMobile = :mobile and t.billAmount = :amount " +
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
}
