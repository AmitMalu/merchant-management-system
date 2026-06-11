package com.project2.ism.Repository;

import com.project2.ism.Model.FranchiseBatchMerchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FranchiseBatchMerchantRepository extends JpaRepository<FranchiseBatchMerchant, Long> {

    List<FranchiseBatchMerchant> findByFranchiseBatchId(Long franchiseBatchId);

    List<FranchiseBatchMerchant> findByFranchiseBatchIdAndStatus(
            Long franchiseBatchId,
            FranchiseBatchMerchant.MerchantProcessingStatus status);

    @Modifying
    @Query("DELETE FROM FranchiseBatchMerchant f WHERE f.franchiseBatchId = :batchId")
    void deleteByFranchiseBatchId(@Param("batchId") Long batchId);

    boolean existsByFranchiseBatchIdAndMerchantId(Long franchiseBatchId, Long merchantId);

    FranchiseBatchMerchant findByFranchiseBatchIdAndMerchantId(Long batchId, Long merchantId);
}