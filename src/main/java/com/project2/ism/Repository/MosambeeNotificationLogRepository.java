package com.project2.ism.Repository;

import com.project2.ism.Model.Logs.MosambeeNotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MosambeeNotificationLogRepository extends JpaRepository<MosambeeNotificationLog, Long> {

    Page<MosambeeNotificationLog> findByOrderByCreatedAtDesc(Pageable pageable);

    List<MosambeeNotificationLog> findByTxnIdOrderByCreatedAtDesc(String txnId);

    Page<MosambeeNotificationLog> findByProcessStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<MosambeeNotificationLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Long countByProcessStatus(String status);

    void deleteByCreatedAtBefore(LocalDateTime before);

    @Query("""
        SELECT m FROM MosambeeNotificationLog m
        WHERE (:txnId IS NULL OR m.txnId = :txnId)
        AND (:processStatus IS NULL OR m.processStatus = :processStatus)
        AND (:start IS NULL OR m.createdAt >= :start)
        AND (:end IS NULL OR m.createdAt <= :end)
        ORDER BY m.createdAt DESC
        """)
    Page<MosambeeNotificationLog> findLogsByFilters(
            @Param("txnId") String txnId,
            @Param("processStatus") String processStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);
}
