package com.project2.ism.Repository;

import com.project2.ism.Model.Logs.PaytmNotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PaytmNotificationLogRepository extends JpaRepository<PaytmNotificationLog, Long> {

    /** How many notifications were stored since `since` (used to cap what a flood can write). */
    long countByCreatedAtAfter(LocalDateTime since);

    @Modifying
    @Query("DELETE FROM PaytmNotificationLog p WHERE p.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    @Query("""
            SELECT p FROM PaytmNotificationLog p
            WHERE (:reference IS NULL OR p.orderId = :reference OR p.txnId = :reference)
            AND (:processStatus IS NULL OR p.processStatus = :processStatus)
            AND (:start IS NULL OR p.createdAt >= :start)
            AND (:end IS NULL OR p.createdAt <= :end)
            ORDER BY p.createdAt DESC
            """)
    Page<PaytmNotificationLog> findLogsByFilters(
            @Param("reference") String reference,
            @Param("processStatus") String processStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);
}
