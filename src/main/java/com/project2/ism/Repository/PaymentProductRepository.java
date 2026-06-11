package com.project2.ism.Repository;

import com.project2.ism.Model.Payment.PaymentProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PaymentProductRepository extends JpaRepository<PaymentProduct, Long> {

    Optional<PaymentProduct> findByProductCode(String productCode);

    boolean existsByProductCode(String productCode);

    @Query("SELECT p FROM PaymentProduct p WHERE " +
            "LOWER(p.productName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :search, '%'))")
    java.util.List<PaymentProduct> search(String search);
}
