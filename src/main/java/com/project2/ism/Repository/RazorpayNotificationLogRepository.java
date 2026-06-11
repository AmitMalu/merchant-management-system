package com.project2.ism.Repository;




import com.project2.ism.Model.Logs.RazorpayNotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RazorpayNotificationLogRepository extends JpaRepository<RazorpayNotificationLog, Long> {
    Page<RazorpayNotificationLog> findByOrderByCreatedAtDesc(Pageable pageable);

    List<RazorpayNotificationLog> findByTxnIdOrderByCreatedAtDesc(String txnId);

    Page<RazorpayNotificationLog> findByProcessStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<RazorpayNotificationLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Long countByProcessStatus(String status);

    void deleteByCreatedAtBefore(LocalDateTime before);
}

