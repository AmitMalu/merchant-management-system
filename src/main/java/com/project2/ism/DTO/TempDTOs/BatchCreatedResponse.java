package com.project2.ism.DTO.TempDTOs;

import com.project2.ism.Model.FranchiseSettlementBatch;
import com.project2.ism.Model.MerchantSettlementBatch;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BatchCreatedResponse {
    public Long batchId;
    public String status;
    public LocalDateTime windowStart;
    public LocalDateTime windowEnd;

    // Enhanced fields for tracking
    public Integer totalTransactions;
    public Integer processedTransactions;
    public Integer failedTransactions;
    public BigDecimal totalAmount;
    public BigDecimal totalFees;
    public BigDecimal totalNetAmount;
    public LocalDateTime processingStartedAt;
    public LocalDateTime processingCompletedAt;
    public String errorMessage;

    public BatchCreatedResponse() {
    }

    public BatchCreatedResponse(MerchantSettlementBatch batch) {
        this.batchId = batch.getId();
        this.status = batch.getStatus();
        this.windowStart = batch.getWindowStart();
        this.windowEnd = batch.getWindowEnd();
        this.totalTransactions = batch.getTotalTransactions();
        this.processedTransactions = batch.getProcessedTransactions();
        this.failedTransactions = batch.getFailedTransactions();
        this.totalAmount = batch.getTotalAmount();
        this.totalFees = batch.getTotalFees();
        this.totalNetAmount = batch.getTotalNetAmount();
        this.processingStartedAt = batch.getProcessingStartedAt();
        this.processingCompletedAt = batch.getProcessingCompletedAt();
        this.errorMessage = batch.getErrorMessage();
    }

    public BatchCreatedResponse(FranchiseSettlementBatch franchiseSettlementBatch) {
    }
}