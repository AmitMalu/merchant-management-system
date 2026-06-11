package com.project2.ism.Repository;



import com.project2.ism.Model.Payment.PaymentVendorRouting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentVendorRoutingRepository extends JpaRepository<PaymentVendorRouting, Long> {

    Optional<PaymentVendorRouting> findByPaymentProductId(Long productId);


    Page<PaymentVendorRouting> findAll(Pageable pageable);

    @Query("SELECT vr FROM PaymentVendorRouting vr LEFT JOIN FETCH vr.paymentVendorRules WHERE vr.id = :id")
    Optional<PaymentVendorRouting> findByIdWithRules(Long id);

    @Query("SELECT vr FROM PaymentVendorRouting vr LEFT JOIN FETCH vr.paymentProduct WHERE vr.id = :id")
    Optional<PaymentVendorRouting> findByIdWithPaymentProduct(Long id);
}
