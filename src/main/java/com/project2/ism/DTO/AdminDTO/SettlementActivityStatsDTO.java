package com.project2.ism.DTO.AdminDTO;

import java.math.BigDecimal;

public class SettlementActivityStatsDTO {
    private Long totalBatches;
    private Long totalProcessedTransactions;
    private BigDecimal totalAmount;
    private BigDecimal totalFees;
    private BigDecimal totalNetAmount;

    public SettlementActivityStatsDTO() {
    }

    public SettlementActivityStatsDTO(Long totalBatches, Long totalProcessedTransactions, BigDecimal totalAmount, BigDecimal totalFees, BigDecimal totalNetAmount) {
        this.totalBatches = totalBatches;
        this.totalProcessedTransactions = totalProcessedTransactions;
        this.totalAmount = totalAmount;
        this.totalFees = totalFees;
        this.totalNetAmount = totalNetAmount;
    }

    public Long getTotalBatches() {
        return totalBatches;
    }

    public void setTotalBatches(Long totalBatches) {
        this.totalBatches = totalBatches;
    }

    public Long getTotalProcessedTransactions() {
        return totalProcessedTransactions;
    }

    public void setTotalProcessedTransactions(Long totalProcessedTransactions) {
        this.totalProcessedTransactions = totalProcessedTransactions;
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
}
