package com.project2.ism.Repository;

import com.project2.ism.Model.FranchiseWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface FranchiseWalletRepository extends JpaRepository<FranchiseWallet, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM FranchiseWallet w WHERE w.franchise.id = :franchiseId")
    Optional<FranchiseWallet> findByFranchiseIdForUpdate(@Param("franchiseId") Long franchiseId);

    Optional<FranchiseWallet> findByFranchiseId(Long franchiseId);
    @Query("SELECT COALESCE(SUM(fw.availableBalance), 0) FROM FranchiseWallet fw")
    BigDecimal getTotalFranchiseWalletBalance();

    @Query("SELECT f.availableBalance FROM FranchiseWallet f WHERE f.franchise.id = :franchiseId")
    Optional<BigDecimal> findAvailableBalanceById(@Param("franchiseId") Long franchiseId);

}