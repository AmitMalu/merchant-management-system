package com.project2.ism.Model.Payment;


import com.project2.ism.Enum.TransactionStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transaction_history",
        indexes = {
                @Index(name = "idx_vendor_created", columnList = "payment_vendor_id,created_at DESC")
        })
public class PaymentTransactionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_vendor_id", nullable = false)
    private PaymentVendor paymentVendor;

    @Column(name = "transaction_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status; // SUCCESS, FAILED

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PaymentTransactionHistory() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PaymentVendor getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(PaymentVendor paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
