package com.project2.ism.Model.Payment;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;



@Entity
@Table(name = "payment_vendor_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_vendor_date",
                        columnNames = {"payment_vendor_id", "log_date"}
                )
        }
)
public class PaymentVendorLog {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "payment_vendor_id", nullable = false)
        private PaymentVendor paymentVendor;

        @Column(name = "log_date", nullable = false)
        private LocalDate logDate;

    @Column(name = "total_amount_processed", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmountProcessed = BigDecimal.ZERO;


    @Column(name = "transaction_count", nullable = false)
        private Integer transactionCount = 0;

        @Column(name = "failure_count", nullable = false)
        private Integer failureCount = 0;

        @CreationTimestamp
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @UpdateTimestamp
        @Column(name = "updated_at")
        private LocalDateTime updatedAt;

        // Unique constraint on vendor + date
        // Add in schema: UNIQUE KEY unique_vendor_date (vendor_id, log_date)

        public PaymentVendorLog() {
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public PaymentVendor getVendor() {
            return paymentVendor;
        }

        public void setVendor(PaymentVendor paymentVendor) {
            this.paymentVendor = paymentVendor;
        }

        public LocalDate getLogDate() {
            return logDate;
        }

        public void setLogDate(LocalDate logDate) {
            this.logDate = logDate;
        }

    public PaymentVendor getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(PaymentVendor paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public BigDecimal getTotalAmountProcessed() {
        return totalAmountProcessed;
    }

    public void setTotalAmountProcessed(BigDecimal totalAmountProcessed) {
        this.totalAmountProcessed = totalAmountProcessed;
    }

    public Integer getTransactionCount() {
            return transactionCount;
        }

        public void setTransactionCount(Integer transactionCount) {
            this.transactionCount = transactionCount;
        }

        public Integer getFailureCount() {
            return failureCount;
        }

        public void setFailureCount(Integer failureCount) {
            this.failureCount = failureCount;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

