package com.project2.ism.Repository;

import com.project2.ism.Model.Payment.PaymentVendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentVendorRepository extends JpaRepository<PaymentVendor, Long> {

    boolean existsByVendorName(String vendorName);

    Optional<PaymentVendor> findByVendorNameAndStatus(
            String vendorName,
            Boolean status
    );
}
