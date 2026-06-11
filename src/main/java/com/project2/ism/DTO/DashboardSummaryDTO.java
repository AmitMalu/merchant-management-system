// 1. DashboardSummaryDTO.java
package com.project2.ism.DTO;

import java.math.BigDecimal;

public class DashboardSummaryDTO {
    private Long totalFranchises;
    private Long totalMerchants;
    private Long totalDirectMerchants;
    private Long totalFranchiseMerchants;
    private Long totalTransactionsToday;
    private Long totalTransactionsThisMonth;
    private BigDecimal totalRevenueThisMonth;
    private Long successfulTransactions;
    private Long failedTransactions;
    private Long pendingSettlements;
    private Long completedSettlementsToday;
    private BigDecimal totalFranchiseWalletBalance;
    private BigDecimal totalDirectMerchantWalletBalance;

    // Constructors
    public DashboardSummaryDTO() {}

    // Getters and Setters
    public Long getTotalFranchises() { return totalFranchises; }
    public void setTotalFranchises(Long totalFranchises) { this.totalFranchises = totalFranchises; }

    public Long getTotalMerchants() { return totalMerchants; }
    public void setTotalMerchants(Long totalMerchants) { this.totalMerchants = totalMerchants; }

    public Long getTotalDirectMerchants() { return totalDirectMerchants; }
    public void setTotalDirectMerchants(Long totalDirectMerchants) { this.totalDirectMerchants = totalDirectMerchants; }

    public Long getTotalFranchiseMerchants() { return totalFranchiseMerchants; }
    public void setTotalFranchiseMerchants(Long totalFranchiseMerchants) { this.totalFranchiseMerchants = totalFranchiseMerchants; }

    public Long getTotalTransactionsToday() { return totalTransactionsToday; }
    public void setTotalTransactionsToday(Long totalTransactionsToday) { this.totalTransactionsToday = totalTransactionsToday; }

    public Long getTotalTransactionsThisMonth() { return totalTransactionsThisMonth; }
    public void setTotalTransactionsThisMonth(Long totalTransactionsThisMonth) { this.totalTransactionsThisMonth = totalTransactionsThisMonth; }

    public BigDecimal getTotalRevenueThisMonth() { return totalRevenueThisMonth; }
    public void setTotalRevenueThisMonth(BigDecimal totalRevenueThisMonth) { this.totalRevenueThisMonth = totalRevenueThisMonth; }

    public Long getSuccessfulTransactions() { return successfulTransactions; }
    public void setSuccessfulTransactions(Long successfulTransactions) { this.successfulTransactions = successfulTransactions; }

    public Long getFailedTransactions() { return failedTransactions; }
    public void setFailedTransactions(Long failedTransactions) { this.failedTransactions = failedTransactions; }

    public Long getPendingSettlements() { return pendingSettlements; }
    public void setPendingSettlements(Long pendingSettlements) { this.pendingSettlements = pendingSettlements; }

    public Long getCompletedSettlementsToday() { return completedSettlementsToday; }
    public void setCompletedSettlementsToday(Long completedSettlementsToday) { this.completedSettlementsToday = completedSettlementsToday; }

    public BigDecimal getTotalFranchiseWalletBalance() { return totalFranchiseWalletBalance; }
    public void setTotalFranchiseWalletBalance(BigDecimal totalFranchiseWalletBalance) { this.totalFranchiseWalletBalance = totalFranchiseWalletBalance; }

    public BigDecimal getTotalDirectMerchantWalletBalance() { return totalDirectMerchantWalletBalance; }
    public void setTotalDirectMerchantWalletBalance(BigDecimal totalDirectMerchantWalletBalance) { this.totalDirectMerchantWalletBalance = totalDirectMerchantWalletBalance; }
}