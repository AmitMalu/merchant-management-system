package com.project2.ism.Repository;

import com.project2.ism.Model.InventoryTransactions.OutwardTransactions;
import org.hibernate.usertype.LoggableUserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
public interface OutwardTransactionRepository extends JpaRepository<OutwardTransactions, Long> {

    boolean existsByDeliveryNumber(String deliveryNumber);

    long countByDispatchDate(LocalDate dispatchDate);

    @Query("SELECT o FROM OutwardTransactions o LEFT JOIN FETCH o.productSerialNumbers WHERE o.franchise.id = :franchiseId")
    List<OutwardTransactions> findByFranchiseIdWithSerials(@Param("franchiseId") Long franchiseId);

    List<OutwardTransactions> findByFranchiseId(@Param("franchiseId") Long franchiseId);
    List<OutwardTransactions> findByFranchiseIdAndReceivedDateIsNotNull(@Param("franchiseId") Long franchiseId);
    List<OutwardTransactions> findByMerchantId(Long merchantId);

    @Query("SELECT CASE " +
            "WHEN ot.franchise.id IS NOT NULL THEN 'FRANCHISE' " +
            "WHEN ot.merchant.id IS NOT NULL THEN 'MERCHANT' " +
            "ELSE 'UNKNOWN' END, COUNT(ot) " +
            "FROM OutwardTransactions ot GROUP BY " +
            "CASE WHEN ot.franchise.id IS NOT NULL THEN 'FRANCHISE' " +
            "WHEN ot.merchant.id IS NOT NULL THEN 'MERCHANT' ELSE 'UNKNOWN' END")
    List<Object[]> groupByCustomerType();

    default Map<String, Long> countByCustomerType() {
        return groupByCustomerType().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }

    // OutwardTransactionRepository
    @Query("SELECT COUNT(ot) FROM OutwardTransactions ot WHERE ot.franchise.id = :franchiseId")
    Long countByFranchiseId(@Param("franchiseId") Long franchiseId);

    @Query("SELECT COUNT(ot) FROM OutwardTransactions ot WHERE ot.merchant.id = :merchantId")
    Long countByMerchantId(@Param("merchantId") Long merchantId);

    List<OutwardTransactions> findByMerchantIdAndProductId(Long merchantId, Long productId);

    List<OutwardTransactions> findByFranchiseIdAndProductId(Long merchantId, Long productId);

    Page<OutwardTransactions> findByDispatchDateAfter(
            LocalDateTime startDate, Pageable pageable);

    Page<OutwardTransactions> findByDispatchDateBefore(
            LocalDateTime endDate, Pageable pageable);

    Page<OutwardTransactions> findByDispatchDateBetween(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Add streaming methods for export
    @Query("SELECT o FROM OutwardTransactions o LEFT JOIN FETCH o.productSerialNumbers")
    Stream<OutwardTransactions> streamAllBy();

    @Query("SELECT o FROM OutwardTransactions o LEFT JOIN FETCH o.productSerialNumbers " +
            "WHERE o.dispatchDate BETWEEN :startDate AND :endDate")
    Stream<OutwardTransactions> streamByDispatchDateBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}