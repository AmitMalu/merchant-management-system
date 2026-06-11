package com.project2.ism.Repository;


import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payment.PaymentVendorCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentVendorCredentialsRepository extends JpaRepository<PaymentVendorCredentials, Long> {

    Optional<PaymentVendorCredentials> findByPaymentVendorIdAndIsActive(Long vendorId, Boolean isActive);

    Optional<PaymentVendorCredentials> findByPaymentVendorId(Long vendorId);

    Optional<PaymentVendorCredentials> findByPaymentVendorAndIsActive(
            PaymentVendor paymentVendor,
            Boolean isActive
    );
}
