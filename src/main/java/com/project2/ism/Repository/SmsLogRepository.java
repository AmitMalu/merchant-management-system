package com.project2.ism.Repository;

import com.project2.ism.Model.Sms.SmsLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {

    boolean existsByTemplateKeyAndRefIdAndStatus(String templateKey, String refId, String status);
}
