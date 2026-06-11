package com.project2.ism.DTO.TempDTOs;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BatchProgressDTO {
    private Long batchId;
    private String status;
    private Integer totalTransactions;
    private Integer processedTransactions;
    private Integer failedTransactions;
    private BigDecimal totalAmount;
    private BigDecimal totalFees;
    private BigDecimal totalNetAmount;
    private LocalDateTime processingStartedAt;
    private LocalDateTime processingCompletedAt;
    private String errorMessage;
    private Double progressPercentage;

    public BatchProgressDTO() {
    }

    // Getters and setters
    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(Integer totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public Integer getProcessedTransactions() {
        return processedTransactions;
    }

    public void setProcessedTransactions(Integer processedTransactions) {
        this.processedTransactions = processedTransactions;
    }

    public Integer getFailedTransactions() {
        return failedTransactions;
    }

    public void setFailedTransactions(Integer failedTransactions) {
        this.failedTransactions = failedTransactions;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getTotalFees() {
        return totalFees;
    }

    public void setTotalFees(BigDecimal totalFees) {
        this.totalFees = totalFees;
    }

    public BigDecimal getTotalNetAmount() {
        return totalNetAmount;
    }

    public void setTotalNetAmount(BigDecimal totalNetAmount) {
        this.totalNetAmount = totalNetAmount;
    }

    public LocalDateTime getProcessingStartedAt() {
        return processingStartedAt;
    }

    public void setProcessingStartedAt(LocalDateTime processingStartedAt) {
        this.processingStartedAt = processingStartedAt;
    }

    public LocalDateTime getProcessingCompletedAt() {
        return processingCompletedAt;
    }

    public void setProcessingCompletedAt(LocalDateTime processingCompletedAt) {
        this.processingCompletedAt = processingCompletedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Double getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(Double progressPercentage) {
        this.progressPercentage = progressPercentage;
    }
}