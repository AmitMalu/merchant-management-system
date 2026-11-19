package com.project2.ism.Repository;

import com.project2.ism.Model.Routing.PaymentRoutingAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRoutingAuditRepository extends JpaRepository<PaymentRoutingAudit, Long> {

    @Query("SELECT a FROM PaymentRoutingAudit a WHERE (:profileId IS NULL OR a.profileId = :profileId) "
            + "ORDER BY a.createdAt DESC, a.id DESC")
    Page<PaymentRoutingAudit> search(@Param("profileId") Long profileId, Pageable pageable);
}
