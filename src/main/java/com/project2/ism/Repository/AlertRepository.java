package com.project2.ism.Repository;

import com.project2.ism.Enum.AlertSeverity;
import com.project2.ism.Enum.AlertStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Model.Monitoring.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // Alert list — every filter is optional (null = don't filter on it), so
    // this single query backs the unfiltered list, single-dimension filters,
    // and combined filters (e.g. status + date range together) alike.
    // initiatorName is a case-insensitive partial match against the
    // denormalized merchant/franchise name (e.g. "tattha" matches "TATTHA
    // MITRA KENDRA") — pass it already wrapped in %...% by the caller.
    @Query("SELECT a FROM Alert a WHERE " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:severity IS NULL OR a.severity = :severity) AND " +
            "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR a.createdAt <= :endDate) AND " +
            "(:initiatorName IS NULL OR LOWER(a.initiatorName) LIKE LOWER(:initiatorName)) " +
            "ORDER BY a.createdAt DESC")
    Page<Alert> findFiltered(@Param("status") AlertStatus status,
                              @Param("severity") AlertSeverity severity,
                              @Param("startDate") LocalDateTime startDate,
                              @Param("endDate") LocalDateTime endDate,
                              @Param("initiatorName") String initiatorName,
                              Pageable pageable);

    long countByStatus(AlertStatus status);

    long countByStatusAndSeverity(AlertStatus status, AlertSeverity severity);

    // Dedup guard: don't raise a duplicate alert for the same rule+initiator
    // while an earlier one raised in the same window is still open.
    List<Alert> findByRuleIdAndInitiatorTypeAndInitiatorIdAndStatusAndCreatedAtAfter(
            Long ruleId, String initiatorType, Long initiatorId, AlertStatus status, LocalDateTime after);

    // Stuck-pending dedup guard (per source row rather than per initiator)
    List<Alert> findByRuleIdAndTransactionEventIdAndStatus(Long ruleId, Long transactionEventId, AlertStatus status);

    // Auto-resolution: find any still-open alert about this exact source row
    // (regardless of which rule raised it) — used when the underlying
    // transaction finally gets a real status via a late vendor callback.
    List<Alert> findBySourceTypeAndTransactionEventIdAndStatus(
            TransactionSourceType sourceType, Long transactionEventId, AlertStatus status);

    // CSV export — every filter is optional (null = don't filter on it)
    @Query("SELECT a FROM Alert a WHERE " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:severity IS NULL OR a.severity = :severity) AND " +
            "(:ruleId IS NULL OR a.ruleId = :ruleId) AND " +
            "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR a.createdAt <= :endDate) AND " +
            "(:initiatorName IS NULL OR LOWER(a.initiatorName) LIKE LOWER(:initiatorName)) " +
            "ORDER BY a.createdAt DESC")
    List<Alert> findForExport(@Param("status") AlertStatus status,
                               @Param("severity") AlertSeverity severity,
                               @Param("ruleId") Long ruleId,
                               @Param("startDate") LocalDateTime startDate,
                               @Param("endDate") LocalDateTime endDate,
                               @Param("initiatorName") String initiatorName);
}
