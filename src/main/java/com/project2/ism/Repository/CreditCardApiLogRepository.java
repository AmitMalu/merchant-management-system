package com.project2.ism.Repository;

import com.project2.ism.Model.CreditCard.CreditCardApiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditCardApiLogRepository
        extends JpaRepository<CreditCardApiLog, Long>, JpaSpecificationExecutor<CreditCardApiLog> {
}
