package com.project2.ism.Repository;

import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductSerialNumbersRepository extends JpaRepository<ProductSerialNumbers, Long> {

    Optional<ProductSerialNumbers> findByMid(String mid);

    @Query("""
    SELECT COUNT(psn)
    FROM ProductSerialNumbers psn
    WHERE psn.merchant.id = :merchantId
      AND psn.franchise.id = :franchiseId
      AND psn.product.id = :productId
      AND psn.productDistribution IS NOT NULL
""")
    Long findQuantityByMerchantFranchiseAndProduct(
            @Param("merchantId") Long merchantId,
            @Param("franchiseId") Long franchiseId,
            @Param("productId") Long productId
    );

}
