package com.project2.ism.Repository;

import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreditCardBillTransactionRepository extends JpaRepository<CreditCardBillTransaction, Long> {

    /** Row lock, so two clicks / two tabs can never pay the same fetched bill twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CreditCardBillTransaction t where t.requestId = :requestId and t.merchantId = :merchantId")
    Optional<CreditCardBillTransaction> findByRequestIdAndMerchantIdForUpdate(@Param("requestId") String requestId,
                                                                              @Param("merchantId") Long merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CreditCardBillTransaction t where t.id = :id")
    Optional<CreditCardBillTransaction> findByIdForUpdate(@Param("id") Long id);

    Optional<CreditCardBillTransaction> findByIdAndMerchantId(Long id, Long merchantId);

    /** A bill for the same card that was fetched a moment ago and not paid yet (see fetch-bill reuse). */
    Optional<CreditCardBillTransaction>
    findFirstByMerchantIdAndBillerIdAndCardLast4AndCustomerMobileAndStatusAndFetchedAtAfterOrderByFetchedAtDesc(
            Long merchantId, String billerId, String cardLast4, String customerMobile,
            CreditCardBillTransaction.Status status, java.time.LocalDateTime fetchedAfter);

    /**
     * Payments (still pending or already successful) made after `since` for the same
     * biller, card, mobile and amount: the "same payment twice" check. Read with a lock so
     * it sees the latest committed rows even when two requests arrive at the same moment.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from CreditCardBillTransaction t where t.merchantId = :merchantId " +
            "and t.billerId = :billerId and t.cardLast4 = :cardLast4 " +
            "and t.customerMobile = :mobile and t.billAmount = :amount " +
            "and t.status in :statuses and t.paidAt >= :since and t.id <> :excludeId " +
            "order by t.paidAt desc")
    List<CreditCardBillTransaction> findRecentSimilarPayments(
            @Param("merchantId") Long merchantId,
            @Param("billerId") String billerId,
            @Param("cardLast4") String cardLast4,
            @Param("mobile") String mobile,
            @Param("amount") java.math.BigDecimal amount,
            @Param("statuses") java.util.Collection<CreditCardBillTransaction.Status> statuses,
            @Param("since") java.time.LocalDateTime since,
            @Param("excludeId") Long excludeId);

    /** Merchant's own payments, newest first. Pass FETCHED as `excluded` to hide bills that were only looked up. */
    @Query("select t from CreditCardBillTransaction t where t.merchantId = :merchantId " +
            "and t.status <> :excluded order by t.createdAt desc")
    List<CreditCardBillTransaction> findHistory(@Param("merchantId") Long merchantId,
                                                @Param("excluded") CreditCardBillTransaction.Status excluded,
                                                Pageable pageable);
}
