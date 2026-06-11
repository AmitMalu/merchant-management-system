package com.project2.ism.Repository;

import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface FranchiseOrMerchantNotificationLogRepository
        extends JpaRepository<FranchiseOrMerchantNotificationLog, Long> {

    Page<FranchiseOrMerchantNotificationLog>
    findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<FranchiseOrMerchantNotificationLog>
    findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    Page<FranchiseOrMerchantNotificationLog>
    findByIsSendAndCreatedAtBetweenOrderByCreatedAtDesc(
            boolean isSend,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    Page<FranchiseOrMerchantNotificationLog>
    findByIsSendOrderByCreatedAtDesc(
            boolean isSend,
            Pageable pageable
    );
}

