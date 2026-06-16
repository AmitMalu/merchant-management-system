package com.project2.ism.Repository;

import com.project2.ism.Model.Logs.MosambeeNotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MosambeeNotificationLogRepository extends JpaRepository<MosambeeNotificationLog, Long> {

    Page<MosambeeNotificationLog> findByOrderByCreatedAtDesc(Pageable pageable);

    List<MosambeeNotificationLog> findByTxnIdOrderByCreatedAtDesc(String txnId);

    Page<MosambeeNotificationLog> findByProcessStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<MosambeeNotificationLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Long countByProcessStatus(String status);

    void deleteByCreatedAtBefore(LocalDateTime before);
}
