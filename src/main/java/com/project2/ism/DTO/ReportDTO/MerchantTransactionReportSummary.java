package com.project2.ism.DTO.ReportDTO;

import java.math.BigDecimal;

/**
 * Comprehensive summary for Merchant Transaction Reports
 * Separates CREDIT (settlements) from DEBIT (payouts/refunds)
 */
public class MerchantTransactionReportSummary {

    // ==================== SETTLEMENT (CREDIT) ====================
    private Long settlementCount;
    private BigDecimal totalTransactionAmount;       // Gross transaction amount
    private BigDecimal totalSettlementReceived;      // What merchant actually received
    private BigDecimal totalChargesPaid;             // Total charges paid

    // ==================== PAYOUT (DEBIT) ====================
    private Long payoutCount;
    private BigDecimal totalPayoutAmount;            // Absolute value of payouts
    private BigDecimal totalPayoutFees;              // Fees charged for payouts
    private Long successfulPayouts;
    private Long failedPayouts;
    private Long pendingPayouts;

    // ==================== REFUND ====================
    private Long refundCount;
    private BigDecimal totalRefundAmount;

    // ==================== NET POSITION ====================
    private BigDecimal netCreditAmount;              // Total CREDITS
    private BigDecimal netDebitAmount;               // Total DEBITS (as negative)
    private BigDecimal netBalance;                   // CREDITS + DEBITS

    // ==================== OVERALL STATS ====================
    private Long totalTransactions;
    private Long successCount;
    private Long failureCount;
    private Long pendingCount;

    // Constructors
    public MerchantTransactionReportSummary() {
    }

    // Getters and Setters
    public Long getSettlementCount() {
        return settlementCount;
    }

    public void setSettlementCount(Long settlementCount) {
        this.settlementCount = settlementCount;
    }

    public BigDecimal getTotalTransactionAmount() {
        return totalTransactionAmount;
    }

    public void setTotalTransactionAmount(BigDecimal totalTransactionAmount) {
        this.totalTransactionAmount = totalTransactionAmount;
    }

    public BigDecimal getTotalSettlementReceived() {
        return totalSettlementReceived;
    }

    public void setTotalSettlementReceived(BigDecimal totalSettlementReceived) {
        this.totalSettlementReceived = totalSettlementReceived;
    }

    public BigDecimal getTotalChargesPaid() {
        return totalChargesPaid;
    }

    public void setTotalChargesPaid(BigDecimal totalChargesPaid) {
        this.totalChargesPaid = totalChargesPaid;
    }

    public Long getPayoutCount() {
        return payoutCount;
    }

    public void setPayoutCount(Long payoutCount) {
        this.payoutCount = payoutCount;
    }

    public BigDecimal getTotalPayoutAmount() {
        return totalPayoutAmount;
    }

    public void setTotalPayoutAmount(BigDecimal totalPayoutAmount) {
        this.totalPayoutAmount = totalPayoutAmount;
    }

    public BigDecimal getTotalPayoutFees() {
        return totalPayoutFees;
    }

    public void setTotalPayoutFees(BigDecimal totalPayoutFees) {
        this.totalPayoutFees = totalPayoutFees;
    }

    public Long getSuccessfulPayouts() {
        return successfulPayouts;
    }

    public void setSuccessfulPayouts(Long successfulPayouts) {
        this.successfulPayouts = successfulPayouts;
    }

    public Long getFailedPayouts() {
        return failedPayouts;
    }

    public void setFailedPayouts(Long failedPayouts) {
        this.failedPayouts = failedPayouts;
    }

    public Long getPendingPayouts() {
        return pendingPayouts;
    }

    public void setPendingPayouts(Long pendingPayouts) {
        this.pendingPayouts = pendingPayouts;
    }

    public Long getRefundCount() {
        return refundCount;
    }

    public void setRefundCount(Long refundCount) {
        this.refundCount = refundCount;
    }

    public BigDecimal getTotalRefundAmount() {
        return totalRefundAmount;
    }

    public void setTotalRefundAmount(BigDecimal totalRefundAmount) {
        this.totalRefundAmount = totalRefundAmount;
    }

    public BigDecimal getNetCreditAmount() {
        return netCreditAmount;
    }

    public void setNetCreditAmount(BigDecimal netCreditAmount) {
        this.netCreditAmount = netCreditAmount;
    }

    public BigDecimal getNetDebitAmount() {
        return netDebitAmount;
    }

    public void setNetDebitAmount(BigDecimal netDebitAmount) {
        this.netDebitAmount = netDebitAmount;
    }

    public BigDecimal getNetBalance() {
        return netBalance;
    }

    public void setNetBalance(BigDecimal netBalance) {
        this.netBalance = netBalance;
    }

    public Long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(Long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public Long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Long successCount) {
        this.successCount = successCount;
    }

    public Long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(Long failureCount) {
        this.failureCount = failureCount;
    }

    public Long getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(Long pendingCount) {
        this.pendingCount = pendingCount;
    }
}