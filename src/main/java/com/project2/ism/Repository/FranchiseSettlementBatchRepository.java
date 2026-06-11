package com.project2.ism.Repository;

import com.project2.ism.DTO.AdminDTO.SettlementActivityStatsDTO;
import com.project2.ism.Model.FranchiseSettlementBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FranchiseSettlementBatchRepository extends JpaRepository<FranchiseSettlementBatch, Long> {

    List<FranchiseSettlementBatch> findByFranchiseIdOrderByCreatedAtDesc(Long franchiseId);

    @Query("SELECT f FROM FranchiseSettlementBatch f WHERE f.franchiseId = :franchiseId AND f.status IN :statuses")
    Optional<FranchiseSettlementBatch> findByFranchiseIdAndStatusIn(
            @Param("franchiseId") Long franchiseId,
            @Param("statuses") List<FranchiseSettlementBatch.BatchStatus> statuses);

    @Query("SELECT f FROM FranchiseSettlementBatch f WHERE f.franchiseId = :franchiseId AND f.cycleKey = :cycleKey AND f.status IN :statuses")
    Optional<FranchiseSettlementBatch> findByFranchiseIdAndCycleKeyAndStatusIn(
            @Param("franchiseId") Long franchiseId,
            @Param("cycleKey") String cycleKey,
            @Param("statuses") List<FranchiseSettlementBatch.BatchStatus> statuses);

    Long countByStatus(FranchiseSettlementBatch.BatchStatus status);

    Long countByStatusAndProcessingCompletedAtBetween(FranchiseSettlementBatch.BatchStatus completed, LocalDateTime startOfDay, LocalDateTime endOfDay);

    @Query("""
        SELECT new com.project2.ism.DTO.AdminDTO.SettlementActivityStatsDTO(
            COUNT(f.id),
            COALESCE(SUM(f.processedTransactions), 0L),
            COALESCE(SUM(f.totalAmount), 0.0),
            COALESCE(SUM(f.totalFees), 0.0),
            COALESCE(SUM(f.totalNetAmount), 0.0)
        )
        FROM FranchiseSettlementBatch f
        WHERE DATE(f.createdAt) = CURRENT_DATE
    """)
    SettlementActivityStatsDTO getTodaysFranchiseSettlementStats(@Param("createdBy") String createdBy);

}




//f.createdBy = :createdBy removed this so to get today settlement in whole system
//        AND