package com.project2.ism.Repository;

import com.project2.ism.Model.Routing.PaymentRoutingTrial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRoutingTrialRepository extends JpaRepository<PaymentRoutingTrial, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM PaymentRoutingTrial t WHERE t.createdAt < :cutoff")
    int purgeOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT t FROM PaymentRoutingTrial t WHERE (:onlyDifferent = false OR t.matched = false) "
            + "ORDER BY t.createdAt DESC, t.id DESC")
    Page<PaymentRoutingTrial> search(@Param("onlyDifferent") boolean onlyDifferent, Pageable pageable);

    /** product, path, vendor used, vendor routing would choose, matched, no routing, count, amount. */
    @Query("SELECT t.productName, t.source, t.usedVendorName, t.chosenVendorName, t.matched, t.noRouting, "
            + "COUNT(t), COALESCE(SUM(t.amount), 0) FROM PaymentRoutingTrial t "
            + "GROUP BY t.productName, t.source, t.usedVendorName, t.chosenVendorName, t.matched, t.noRouting "
            + "ORDER BY t.productName, t.source")
    List<Object[]> summary();
}
