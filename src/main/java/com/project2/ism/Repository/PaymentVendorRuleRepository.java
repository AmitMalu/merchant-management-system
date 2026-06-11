package com.project2.ism.Repository;


import com.project2.ism.Model.Payment.PaymentVendorRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentVendorRuleRepository extends JpaRepository<PaymentVendorRule, Long> {

    List<PaymentVendorRule> findByPaymentVendorRoutingId(Long paymentVendorRoutingId);

    List<PaymentVendorRule> findByPaymentVendorId(Long paymentVendorId);

    void deleteByPaymentVendorRoutingId(Long vendorRoutingId);
}
