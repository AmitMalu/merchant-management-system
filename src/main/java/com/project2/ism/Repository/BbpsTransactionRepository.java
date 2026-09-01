package com.project2.ism.Repository;

import com.project2.ism.Model.Bbps.BbpsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author SHUBHAM KHOPADE
 */
public interface BbpsTransactionRepository extends JpaRepository<BbpsTransaction, Long> {

    Optional<BbpsTransaction> findByRequestId(String requestId);

    Optional<BbpsTransaction> findByTxnRefId(String txnRefId);

    List<BbpsTransaction> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    // Used by the Transaction Monitoring stuck-pending sweep
    List<BbpsTransaction> findByStatusAndCreatedAtBefore(
            BbpsTransaction.BbpsStatus status, LocalDateTime cutoffTime);
}
