package com.project2.ism.Repository;


import com.project2.ism.Model.InventoryTransactions.ReturnTransactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface ReturnTransactionRepository extends JpaRepository<ReturnTransactions, Long> {

    boolean existsByReturnNumber(String returnNumber);

    List<ReturnTransactions> findByFranchiseId(@Param("franchiseId") Long franchiseId);

    List<ReturnTransactions> findByMerchantId(Long merchantId);

    @Query("SELECT COUNT(rt) FROM ReturnTransactions rt WHERE rt.franchise.id = :franchiseId")
    Long countByFranchiseId(@Param("franchiseId") Long franchiseId);

    @Query("SELECT COUNT(rt) FROM ReturnTransactions rt WHERE rt.merchant.id = :merchantId")
    Long countByMerchantId(@Param("merchantId") Long merchantId);
}