package com.project2.ism.Repository;

import com.project2.ism.Model.InventoryTransactions.ProductDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductDistributionRepository extends JpaRepository<ProductDistribution,Long> {
    List<ProductDistribution> findByFranchiseId(Long franchiseId);
    List<ProductDistribution> findByMerchantId(Long merchantId);

    @Query("""
    SELECT pd.quantity
    FROM ProductDistribution pd
    WHERE pd.merchant.id = :merchantId
      AND pd.franchise.id = :franchiseId
""")
    Integer findQuantityByMerchantAndFranchise(
            @Param("merchantId") Long merchantId,
            @Param("franchiseId") Long franchiseId
    );

}
