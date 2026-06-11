package com.project2.ism.DTO.PayoutDTO;

import com.project2.ism.Model.Payout.PayoutTransaction;

import java.util.List;

public  class PayoutStats {
    private long totalCount;
    private long successCount;
    private long failedCount;
    private long pendingCount;
    private java.math.BigDecimal totalAmount;
    private java.math.BigDecimal totalCharges;

    public PayoutStats(List<PayoutTransaction> transactions) {
        this.totalCount = transactions.size();
        this.successCount = transactions.stream()
                .filter(t -> t.getStatus() == PayoutTransaction.PayoutStatus.SUCCESS)
                .count();
        this.failedCount = transactions.stream()
                .filter(t -> t.getStatus() == PayoutTransaction.PayoutStatus.FAILED)
                .count();
        this.pendingCount = transactions.stream()
                .filter(t -> t.getStatus() == PayoutTransaction.PayoutStatus.PENDING)
                .count();
        this.totalAmount = transactions.stream()
                .map(PayoutTransaction::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        this.totalCharges = transactions.stream()
                .map(PayoutTransaction::getCharges)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    // Getters
    public long getTotalCount() { return totalCount; }
    public long getSuccessCount() { return successCount; }
    public long getFailedCount() { return failedCount; }
    public long getPendingCount() { return pendingCount; }
    public java.math.BigDecimal getTotalAmount() { return totalAmount; }
    public java.math.BigDecimal getTotalCharges() { return totalCharges; }
}