package com.project2.ism.DTO.ReportDTO;



import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TransactionReportDTO {

    // Request DTOs
    public static class TransactionReportRequest {
        @NotNull(message = "Start date is required")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startDate;

        @NotNull(message = "End date is required")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endDate;

        private Long merchantId;
        private Long franchiseId;
        private String transactionStatus;
        private String transactionType;
        private int page = 0;
        private int size = 50;
        // NEW FIELDS - Add these to existing TransactionReportRequest
        private String dateFilterType = "TRANSACTION_DATE"; // TRANSACTION_DATE or SETTLEMENT_DATE
        private String merchantType = "ALL"; // ALL, DIRECT, FRANCHISE

        // Generate: getters, setters, constructors (noargs, allargs)

        public TransactionReportRequest() {
        }

        public TransactionReportRequest(LocalDateTime startDate, LocalDateTime endDate, Long merchantId, Long franchiseId, String transactionStatus, String transactionType, int page, int size) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.merchantId = merchantId;
            this.franchiseId = franchiseId;
            this.transactionStatus = transactionStatus;
            this.transactionType = transactionType;
            this.page = page;
            this.size = size;
        }

        public LocalDateTime getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDateTime startDate) {
            this.startDate = startDate;
        }

        public LocalDateTime getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDateTime endDate) {
            this.endDate = endDate;
        }

        public Long getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(Long merchantId) {
            this.merchantId = merchantId;
        }

        public Long getFranchiseId() {
            return franchiseId;
        }

        public void setFranchiseId(Long franchiseId) {
            this.franchiseId = franchiseId;
        }

        public String getTransactionStatus() {
            return transactionStatus;
        }

        public void setTransactionStatus(String transactionStatus) {
            this.transactionStatus = transactionStatus;
        }

        public String getTransactionType() {
            return transactionType;
        }

        public void setTransactionType(String transactionType) {
            this.transactionType = transactionType;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
        public String getDateFilterType() {
            return dateFilterType;
        }

        public void setDateFilterType(String dateFilterType) {
            this.dateFilterType = dateFilterType;
        }

        public String getMerchantType() {
            return merchantType;
        }

        public void setMerchantType(String merchantType) {
            this.merchantType = merchantType;
        }
    }


    public static class TransactionSummary {
        private Long totalTransactions;
        private BigDecimal totalAmount;
        private BigDecimal totalNetAmount;
        private BigDecimal totalCharges;
        private BigDecimal totalCommission;
        private BigDecimal averageTransactionValue;
        private Long successfulTransactions;
        private Long failedTransactions;
        private Double successRate;

        // Generate: getters, setters, constructors (noargs, allargs)

        public TransactionSummary() {
        }

        public TransactionSummary(Long totalTransactions, BigDecimal totalAmount, BigDecimal totalNetAmount, BigDecimal totalCharges, BigDecimal totalCommission, BigDecimal averageTransactionValue, Long successfulTransactions, Long failedTransactions, Double successRate) {
            this.totalTransactions = totalTransactions;
            this.totalAmount = totalAmount;
            this.totalNetAmount = totalNetAmount;
            this.totalCharges = totalCharges;
            this.totalCommission = totalCommission;
            this.averageTransactionValue = averageTransactionValue;
            this.successfulTransactions = successfulTransactions;
            this.failedTransactions = failedTransactions;
            this.successRate = successRate;
        }

        public Long getTotalTransactions() {
            return totalTransactions;
        }

        public void setTotalTransactions(Long totalTransactions) {
            this.totalTransactions = totalTransactions;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public BigDecimal getTotalNetAmount() {
            return totalNetAmount;
        }

        public void setTotalNetAmount(BigDecimal totalNetAmount) {
            this.totalNetAmount = totalNetAmount;
        }

        public BigDecimal getTotalCharges() {
            return totalCharges;
        }

        public void setTotalCharges(BigDecimal totalCharges) {
            this.totalCharges = totalCharges;
        }

        public BigDecimal getTotalCommission() {
            return totalCommission;
        }

        public void setTotalCommission(BigDecimal totalCommission) {
            this.totalCommission = totalCommission;
        }

        public BigDecimal getAverageTransactionValue() {
            return averageTransactionValue;
        }

        public void setAverageTransactionValue(BigDecimal averageTransactionValue) {
            this.averageTransactionValue = averageTransactionValue;
        }

        public Long getSuccessfulTransactions() {
            return successfulTransactions;
        }

        public void setSuccessfulTransactions(Long successfulTransactions) {
            this.successfulTransactions = successfulTransactions;
        }

        public Long getFailedTransactions() {
            return failedTransactions;
        }

        public void setFailedTransactions(Long failedTransactions) {
            this.failedTransactions = failedTransactions;
        }

        public Double getSuccessRate() {
            return successRate;
        }

        public void setSuccessRate(Double successRate) {
            this.successRate = successRate;
        }
    }

    public static class TransactionReportResponse<T> {
        private List<T> transactions;
        private Object summary;
        private LocalDateTime reportGeneratedAt;
        private String reportType;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
        private boolean hasPrevious;

        // Generate: getters, setters, constructors (noargs, allargs)

        public TransactionReportResponse() {
        }

        public TransactionReportResponse(List<T> transactions, TransactionSummary summary, LocalDateTime reportGeneratedAt, String reportType, int totalPages, long totalElements, boolean hasNext, boolean hasPrevious) {
            this.transactions = transactions;
            this.summary = summary;
            this.reportGeneratedAt = reportGeneratedAt;
            this.reportType = reportType;
            this.totalPages = totalPages;
            this.totalElements = totalElements;
            this.hasNext = hasNext;
            this.hasPrevious = hasPrevious;
        }

        public List<T> getTransactions() {
            return transactions;
        }

        public void setTransactions(List<T> transactions) {
            this.transactions = transactions;
        }

        public Object getSummary() {
            return summary;
        }

        public void setSummary(Object summary) {
            this.summary = summary;
        }

        public LocalDateTime getReportGeneratedAt() {
            return reportGeneratedAt;
        }

        public void setReportGeneratedAt(LocalDateTime reportGeneratedAt) {
            this.reportGeneratedAt = reportGeneratedAt;
        }

        public String getReportType() {
            return reportType;
        }

        public void setReportType(String reportType) {
            this.reportType = reportType;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }

        public long getTotalElements() {
            return totalElements;
        }

        public void setTotalElements(long totalElements) {
            this.totalElements = totalElements;
        }

        public boolean isHasNext() {
            return hasNext;
        }

        public void setHasNext(boolean hasNext) {
            this.hasNext = hasNext;
        }

        public boolean isHasPrevious() {
            return hasPrevious;
        }

        public void setHasPrevious(boolean hasPrevious) {
            this.hasPrevious = hasPrevious;
        }
    }

    public static class CommissionBreakdown {
        private BigDecimal grossCommission;
        private BigDecimal netCommission;
        private BigDecimal commissionRate;
        private Long merchantCount;
        private BigDecimal averageCommissionPerMerchant;

        // Generate: getters, setters, constructors (noargs, allargs)

        public CommissionBreakdown() {
        }

        public CommissionBreakdown(BigDecimal grossCommission, BigDecimal netCommission, BigDecimal commissionRate, Long merchantCount, BigDecimal averageCommissionPerMerchant) {
            this.grossCommission = grossCommission;
            this.netCommission = netCommission;
            this.commissionRate = commissionRate;
            this.merchantCount = merchantCount;
            this.averageCommissionPerMerchant = averageCommissionPerMerchant;
        }

        public BigDecimal getGrossCommission() {
            return grossCommission;
        }

        public void setGrossCommission(BigDecimal grossCommission) {
            this.grossCommission = grossCommission;
        }

        public BigDecimal getNetCommission() {
            return netCommission;
        }

        public void setNetCommission(BigDecimal netCommission) {
            this.netCommission = netCommission;
        }

        public BigDecimal getCommissionRate() {
            return commissionRate;
        }

        public void setCommissionRate(BigDecimal commissionRate) {
            this.commissionRate = commissionRate;
        }

        public Long getMerchantCount() {
            return merchantCount;
        }

        public void setMerchantCount(Long merchantCount) {
            this.merchantCount = merchantCount;
        }

        public BigDecimal getAverageCommissionPerMerchant() {
            return averageCommissionPerMerchant;
        }

        public void setAverageCommissionPerMerchant(BigDecimal averageCommissionPerMerchant) {
            this.averageCommissionPerMerchant = averageCommissionPerMerchant;
        }
    }


}