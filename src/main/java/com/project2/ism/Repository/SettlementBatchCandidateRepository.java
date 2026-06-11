package com.project2.ism.Repository;

import com.project2.ism.Model.SettlementBatchCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementBatchCandidateRepository extends JpaRepository<SettlementBatchCandidate, Long> {

    List<SettlementBatchCandidate> findByBatchId(Long batchId);

    List<SettlementBatchCandidate> findByBatchIdAndStatus(
            Long batchId,
            SettlementBatchCandidate.CandidateStatus status);

    @Modifying
    @Query("DELETE FROM SettlementBatchCandidate s WHERE s.batchId = :batchId")
    void deleteByBatchId(@Param("batchId") Long batchId);

    @Modifying
    @Query("UPDATE SettlementBatchCandidate s SET s.status = 'SELECTED' WHERE s.batchId = :batchId AND s.status = 'FAILED'")
    void updateFailedCandidatesToSelected(@Param("batchId") Long batchId);

    long countByBatchIdAndStatus(Long batchId, SettlementBatchCandidate.CandidateStatus status);
}