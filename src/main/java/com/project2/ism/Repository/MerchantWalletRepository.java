package com.project2.ism.Repository;

import com.project2.ism.Model.MerchantWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface MerchantWalletRepository extends JpaRepository<MerchantWallet, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from MerchantWallet w where w.merchant.id = :merchantId")
    Optional<MerchantWallet> findByMerchantIdForUpdate(@Param("merchantId") Long merchantId);

    Optional<MerchantWallet> findByMerchantId(Long merchantId);

    // For direct merchants (no franchise_id in merchant table)
    @Query("""
        SELECT COALESCE(SUM(mw.availableBalance), 0) 
        FROM MerchantWallet mw 
        JOIN Merchant m ON mw.merchant.id = m.id 
        WHERE m.franchise.id IS NULL
        """)
    BigDecimal getTotalDirectMerchantWalletBalance();

    // For franchise merchants (have franchise_id in merchant table)
    @Query("""
        SELECT COALESCE(SUM(mw.availableBalance), 0) 
        FROM MerchantWallet mw 
        JOIN Merchant m ON mw.merchant.id = m.id 
        WHERE m.franchise.id IS NOT NULL
        """)
    BigDecimal getTotalFranchiseMerchantWalletBalance();

    @Query("SELECT m.availableBalance FROM MerchantWallet m WHERE m.merchant.id = :merchantId")
    Optional<BigDecimal> findAvailableBalanceById(@Param("merchantId") Long merchantId);
}
