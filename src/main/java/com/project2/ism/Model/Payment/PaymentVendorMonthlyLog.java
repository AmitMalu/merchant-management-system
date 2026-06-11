package com.project2.ism.Model.Payment;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;



@Entity
@Table(name = "payment_vendor_monthly_logs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_vendor_year_month",
                        columnNames = {"payment_vendor_id", "year", "month"}
                )
        })
public class PaymentVendorMonthlyLog {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "payment_vendor_id", nullable = false)
        private PaymentVendor paymentVendor;

        @Column(name = "year", nullable = false)
        private Integer year;

        @Column(name = "month", nullable = false)
        private Integer month; // 1-12

    @Column(name = "total_amount_processed", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmountProcessed = BigDecimal.ZERO;


    @Column(name = "total_transactions", nullable = false)
        private Integer totalTransactions = 0;

        @Column(name = "total_failures", nullable = false)
        private Integer totalFailures = 0;

        @Column(name = "total_success", nullable = false)
        private Integer totalSuccess = 0;

        @Column(name = "average_transaction_amount")
        private Double averageTransactionAmount;

        @CreationTimestamp
        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @UpdateTimestamp
        @Column(name = "updated_at")
        private LocalDateTime updatedAt;

        // Unique constraint: UNIQUE KEY unique_vendor_year_month (vendor_id, year, month)

        public PaymentVendorMonthlyLog() {
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

        public Integer getYear() {
            return year;
        }

        public void setYear(Integer year) {
            this.year = year;
        }

        public Integer getMonth() {
            return month;
        }

        public void setMonth(Integer month) {
            this.month = month;
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

    public Integer getTotalTransactions() {
            return totalTransactions;
        }

        public void setTotalTransactions(Integer totalTransactions) {
            this.totalTransactions = totalTransactions;
        }

        public Integer getTotalFailures() {
            return totalFailures;
        }

        public void setTotalFailures(Integer totalFailures) {
            this.totalFailures = totalFailures;
        }

        public Integer getTotalSuccess() {
            return totalSuccess;
        }

        public void setTotalSuccess(Integer totalSuccess) {
            this.totalSuccess = totalSuccess;
        }

        public Double getAverageTransactionAmount() {
            return averageTransactionAmount;
        }

        public void setAverageTransactionAmount(Double averageTransactionAmount) {
            this.averageTransactionAmount = averageTransactionAmount;
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
