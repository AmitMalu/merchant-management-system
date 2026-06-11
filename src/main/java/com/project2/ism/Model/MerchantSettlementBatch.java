

package com.project2.ism.Model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "merchant_settlement_batch")
public class MerchantSettlementBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "franchise_batch_id", nullable = true)
    private Long franchiseBatchId;


    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDateTime windowEnd;

    @Column(name = "cycle_key", nullable = false)
    private String cycleKey;

    @Column(name = "status", nullable = false)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private String createdBy;

    // New fields for enhanced tracking
    @Column(name = "total_transactions")
    private Integer totalTransactions = 0;

    @Column(name = "processed_transactions")
    private Integer processedTransactions = 0;

    @Column(name = "failed_transactions")
    private Integer failedTransactions = 0;

    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_fees", precision = 19, scale = 2)
    private BigDecimal totalFees = BigDecimal.ZERO;

    @Column(name = "total_net_amount", precision = 19, scale = 2)
    private BigDecimal totalNetAmount = BigDecimal.ZERO;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processing_completed_at")
    private LocalDateTime processingCompletedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // Constructors
    public MerchantSettlementBatch() {
    }

    public MerchantSettlementBatch(Long merchantId,Long productId, LocalDateTime windowStart, LocalDateTime windowEnd,
                                   String cycleKey, String createdBy) {
        this.merchantId = merchantId;
        this.productId = productId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.cycleKey = cycleKey;
        this.createdBy = createdBy;
        this.status = "OPEN";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getCycleKey() {
        return cycleKey;
    }

    public void setCycleKey(String cycleKey) {
        this.cycleKey = cycleKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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

    public Long getFranchiseBatchId() {
        return franchiseBatchId;
    }

    public void setFranchiseBatchId(Long franchiseBatchId) {
        this.franchiseBatchId = franchiseBatchId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}