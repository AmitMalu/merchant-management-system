package com.project2.ism.Repository;

import com.project2.ism.Model.Payout.PaymentAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    /** Every try made for one payment (a payout), oldest first. */
    java.util.List<PaymentAttempt> findBySourceTypeAndSourceIdOrderByAttemptNoAsc(String sourceType, Long sourceId);

    @Query("SELECT a FROM PaymentAttempt a ORDER BY a.createdAt DESC, a.id DESC")
    Page<PaymentAttempt> recent(Pageable pageable);
}
