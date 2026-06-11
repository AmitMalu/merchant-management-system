package com.project2.ism.Repository;

import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Users.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public interface ProductSerialsRepository extends JpaRepository<ProductSerialNumbers,Long> {
    boolean existsBySid(String sid);
    boolean existsByMid(String mid);
    boolean existsByTid(String tid);
    boolean existsByVpaid(String vpaid);
    boolean existsByMobNumber(String mobNumber);
    long countByProductId(Long productId);
    List<ProductSerialNumbers> findByProduct_IdAndMerchantIsNullAndOutwardTransactionIsNull(Long productId);


    List<ProductSerialNumbers> findByOutwardTransaction_IdAndMerchantIsNotNullAndReceivedDateIsNull(Long outwardID);

    List<ProductSerialNumbers> findByOutwardTransaction_IdAndMerchantIsNullAndReceivedDateIsNull(Long outwardID);
    List<ProductSerialNumbers> findByOutwardTransaction_IdAndMerchantIsNullAndReceivedDateIsNotNull(Long outwardID);

    List<ProductSerialNumbers> findByFranchise_IdAndProduct_IdAndMerchant_IdIsNullAndReceivedDateByFranchiseIsNotNull(Long franchiseId,Long productId);
    List<ProductSerialNumbers> findByMerchant_Id(Long merchantId);
    List<ProductSerialNumbers> findByFranchise_IdAndReceivedDateByFranchiseIsNotNull(Long franchiseId);
    @Modifying
    @Query("UPDATE ProductSerialNumbers ps SET ps.merchant.id = :merchantId, ps.productDistribution.id = :distributionId WHERE ps.id IN :serialIds")
    int assignMerchantToSerials(@Param("merchantId") Long merchantId,
                                @Param("serialIds") List<Long> serialIds,
                                @Param("distributionId") Long distributionId);

    @Query("SELECT " +
            "CASE " +
            "WHEN psn.returnTransaction IS NOT NULL THEN 'RETURNED' " +
            "WHEN psn.outwardTransaction IS NOT NULL THEN 'ALLOCATED' " +
            "ELSE 'AVAILABLE' END, COUNT(psn) " +
            "FROM ProductSerialNumbers psn " +
            "GROUP BY CASE " +
            "WHEN psn.returnTransaction IS NOT NULL THEN 'RETURNED' " +
            "WHEN psn.outwardTransaction IS NOT NULL THEN 'ALLOCATED' " +
            "ELSE 'AVAILABLE' END")
    List<Object[]> groupByStatus();

    default Map<String, Long> countByStatus() {
        return groupByStatus().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }

    // ProductSerialsRepository
    @Query("SELECT COUNT(psn) FROM ProductSerialNumbers psn WHERE psn.merchant.id = :merchantId")
    Long countByMerchantId(@Param("merchantId") Long merchantId);

    List<ProductSerialNumbers> findByProduct_Id(Long id);

    @Query("select psn.mid, psn.tid, psn.sid, psn.vpaid from ProductSerialNumbers psn where psn.merchant.id = :merchantId")
    List<Object[]> findIdentifiersByMerchant(@Param("merchantId") Long merchantId);



    /**
     * Get device identifiers for a specific merchant
     */
    @Query("""
        SELECT psn.mid, psn.tid, psn.sid , psn.vpaid
        FROM ProductSerialNumbers psn 
        WHERE psn.merchant.id = :merchantId 
        AND psn.outwardTransaction IS NOT NULL
        """)
    List<Object[]> findDeviceIdentifiersByMerchant(@Param("merchantId") Long merchantId);

    /**
     * Find device by MID and TID combination (most reliable)
     */
//    @Query("""
//        SELECT psn FROM ProductSerialNumbers psn
//        WHERE psn.mid = :mid AND psn.tid = :tid
//        AND psn.outwardTransaction IS NOT NULL
//        """)
//    Optional<ProductSerialNumbers> findByMidAndTid(@Param("mid") String mid, @Param("tid") String tid);

    List<ProductSerialNumbers> findByMidAndTid(String mid, String tid);


    /**
     * Find device by MID only
     */
    @Query("""
        SELECT psn FROM ProductSerialNumbers psn 
        WHERE psn.mid = :mid 
        AND psn.outwardTransaction IS NOT NULL
        ORDER BY psn.id DESC
        """)
    Optional<ProductSerialNumbers> findByMid(@Param("mid") String mid);

    /**
     * Find device by TID only
     */
    @Query("""
        SELECT psn FROM ProductSerialNumbers psn 
        WHERE psn.tid = :tid 
        AND psn.outwardTransaction IS NOT NULL
        ORDER BY psn.id DESC
        """)
    Optional<ProductSerialNumbers> findByTid(@Param("tid") String tid);

    // ProductSerialsRepository
    List<ProductSerialNumbers> findByMerchantAndProduct(Merchant merchant, Product product);


    @Modifying
    @Query("UPDATE ProductSerialNumbers ps SET ps.productDistribution = NULL, ps.merchant = NULL WHERE ps.productDistribution.id = :distributionId")
    void clearDistributionFromSerials(@Param("distributionId") Long distributionId);


    @Query("SELECT DISTINCT psn FROM ProductSerialNumbers psn " +
            "LEFT JOIN FETCH psn.product p " +
            "LEFT JOIN FETCH p.productCategory " +
            "LEFT JOIN FETCH psn.merchant m " +
            "LEFT JOIN FETCH psn.franchise f " +
            "LEFT JOIN FETCH psn.inwardTransaction it " +
            "LEFT JOIN FETCH psn.outwardTransaction ot " +
            "LEFT JOIN FETCH ot.merchant otm " +
            "LEFT JOIN FETCH ot.franchise otf " +
            "LEFT JOIN FETCH psn.returnTransaction rt " +
            "LEFT JOIN FETCH psn.productDistribution pd " +
            "WHERE (:status IS NULL OR " +
            "   (:status = 'AVAILABLE' AND psn.outwardTransaction IS NULL AND psn.returnTransaction IS NULL AND psn.merchant IS NULL AND psn.franchise IS NULL) OR " +
            "   (:status = 'ALLOCATED' AND (psn.outwardTransaction IS NOT NULL OR psn.merchant IS NOT NULL OR psn.franchise IS NOT NULL) AND psn.returnTransaction IS NULL) OR " +
            "   (:status = 'RETURNED' AND psn.returnTransaction IS NOT NULL)) " +
            "AND (:productId IS NULL OR p.id = :productId) " +
            "AND (:merchantId IS NULL OR m.id = :merchantId OR otm.id = :merchantId) " +
            "AND (:franchiseId IS NULL OR f.id = :franchiseId OR otf.id = :franchiseId) " +
            "AND (:fromDate IS NULL OR psn.receivedDate >= :fromDate OR it.receivedDate >= :fromDate) " +
            "AND (:toDate IS NULL OR psn.receivedDate <= :toDate OR it.receivedDate <= :toDate)")
    List<ProductSerialNumbers> findByFilters(
            @Param("status") String status,
            @Param("productId") Long productId,
            @Param("merchantId") Long merchantId,
            @Param("franchiseId") Long franchiseId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );
}
