package com.project2.ism.Repository;

import com.project2.ism.Model.Routing.PaymentRoutingSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRoutingSettingRepository extends JpaRepository<PaymentRoutingSetting, String> {
}
