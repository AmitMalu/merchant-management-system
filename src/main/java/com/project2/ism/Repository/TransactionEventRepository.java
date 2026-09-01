package com.project2.ism.Repository;

import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Model.Monitoring.TransactionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionEventRepository extends JpaRepository<TransactionEvent, Long> {

    // Velocity / failure-rate window queries
    List<TransactionEvent> findByInitiatorTypeAndInitiatorIdAndOccurredAtAfter(
            String initiatorType, Long initiatorId, LocalDateTime after);

    List<TransactionEvent> findByInitiatorTypeAndInitiatorIdAndStatusAndOccurredAtAfter(
            String initiatorType, Long initiatorId, TransactionEventStatus status, LocalDateTime after);

    // Distinct initiators active in a window, so the scheduler only re-evaluates
    // initiators who actually did something instead of scanning every merchant.
    @Query("SELECT DISTINCT e.initiatorType, e.initiatorId FROM TransactionEvent e WHERE e.occurredAt >= :after")
    List<Object[]> findDistinctInitiatorsSince(@Param("after") LocalDateTime after);

    // Dashboard aggregation: counts per source/status in a window
    @Query("SELECT e.sourceType, e.status, COUNT(e) FROM TransactionEvent e " +
            "WHERE e.occurredAt >= :since GROUP BY e.sourceType, e.status")
    List<Object[]> countBySourceTypeAndStatusSince(@Param("since") LocalDateTime since);

    // Dashboard aggregation: settlement/commission volume by card network
    // (Visa/Mastercard/RuPay/...) in a window — only events that actually
    // came from a card-present transaction carry a cardBrand.
    @Query("SELECT e.cardBrand, COUNT(e), SUM(e.amount) FROM TransactionEvent e " +
            "WHERE e.cardBrand IS NOT NULL AND e.occurredAt >= :since GROUP BY e.cardBrand")
    List<Object[]> countByCardBrandSince(@Param("since") LocalDateTime since);

    List<TransactionEvent> findTop50ByOrderByOccurredAtDesc();
}
