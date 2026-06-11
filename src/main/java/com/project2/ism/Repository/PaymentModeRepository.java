package com.project2.ism.Repository;

import com.project2.ism.Model.Payment.PaymentMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface PaymentModeRepository extends JpaRepository<PaymentMode, Long> {

    boolean existsByCode(String code);

    Optional<PaymentMode> findByCode(String modeName);

    List<PaymentMode> findByStatusTrue();
}

