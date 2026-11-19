package com.project2.ism.Repository;

import com.project2.ism.Model.Routing.PaymentProductVendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentProductVendorRepository extends JpaRepository<PaymentProductVendor, Long> {

    @Query("SELECT l FROM PaymentProductVendor l JOIN FETCH l.product JOIN FETCH l.vendor "
            + "WHERE (:productId IS NULL OR l.product.id = :productId) ORDER BY l.product.id, l.vendor.vendorName")
    List<PaymentProductVendor> findAllWithDetails(@Param("productId") Long productId);

    @Query("SELECT l FROM PaymentProductVendor l JOIN FETCH l.product JOIN FETCH l.vendor "
            + "WHERE l.product.id = :productId AND l.vendor.id = :vendorId")
    Optional<PaymentProductVendor> findByProductAndVendor(@Param("productId") Long productId,
                                                          @Param("vendorId") Long vendorId);
}
