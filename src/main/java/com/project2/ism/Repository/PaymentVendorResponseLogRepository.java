package com.project2.ism.Repository;

import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PaymentVendorResponseLogRepository extends JpaRepository<PaymentVendorResponseLog, Long> {

    Page<PaymentVendorResponseLog> findByOrderByCreatedOnDesc(Pageable pageable);

    Page<PaymentVendorResponseLog> findByVendorIdOrderByCreatedOnDesc(Long vendorId, Pageable pageable);

    Page<PaymentVendorResponseLog> findByApiNameOrderByCreatedOnDesc(String apiName, Pageable pageable);

    Page<PaymentVendorResponseLog> findByStatusCodeOrderByCreatedOnDesc(Integer statusCode, Pageable pageable);

    Page<PaymentVendorResponseLog> findByCreatedOnBetweenOrderByCreatedOnDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    Long countByVendorId(Long vendorId);

    void deleteByCreatedOnBefore(LocalDateTime before);
}