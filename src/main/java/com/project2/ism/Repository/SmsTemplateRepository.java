package com.project2.ism.Repository;

import com.project2.ism.Model.Sms.SmsTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SmsTemplateRepository extends JpaRepository<SmsTemplate, Long> {

    Optional<SmsTemplate> findByTemplateKey(String templateKey);
}
