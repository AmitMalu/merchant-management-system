package com.project2.ism.Model.Bbps;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tracks a BBPS bill-payment transaction across its lifecycle — mirrors the
 * PayoutTransaction entity's role for the Payout vendor flow.
 *
 * @author SHUBHAM KHOPADE
 */
@Entity
@Table(name = "bbps_transactions",
        indexes = {
                @Index(name = "idx_bbps_request_id", columnList = "request_id"),
                @Index(name = "idx_bbps_txn_ref_id", columnList = "txn_ref_id"),
                @Index(name = "idx_bbps_merchant", columnList = "merchant_id,created_at DESC")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_bbps_request_id", columnNames = {"request_id"})
        })
public class BbpsTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "biller_id", nullable = false)
    private String billerId;

    @Column(name = "biller_name")
    private String billerName;

    @Column(name = "category")
    private String category;

    @Column(name = "consumer_number")
    private String consumerNumber;

    @Column(name = "customer_mobile")
    private String customerMobile;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "cust_conv_fee", precision = 10, scale = 2)
    private BigDecimal custConvFee;

    @Column(name = "charges", precision = 10, scale = 2)
    private BigDecimal charges;

    @Column(name = "total_deducted", precision = 15, scale = 2)
    private BigDecimal totalDeducted;

    @Column(name = "payment_mode")
    private String paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BbpsStatus status;

    @Column(name = "response_code")
    private String responseCode;

    @Column(name = "response_message")
    private String responseMessage;

    @Column(name = "txn_ref_id")
    private String txnRefId;

    @Column(name = "approval_ref_number")
    private String approvalRefNumber;

    @Column(name = "ledger_merchant_txn_id")
    private Long ledgerMerchantTxnId;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum BbpsStatus {
        PENDING,
        SUCCESS,
        FAILED
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getBillerId() { return billerId; }
    public void setBillerId(String billerId) { this.billerId = billerId; }

    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getConsumerNumber() { return consumerNumber; }
    public void setConsumerNumber(String consumerNumber) { this.consumerNumber = consumerNumber; }

    public String getCustomerMobile() { return customerMobile; }
    public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getCustConvFee() { return custConvFee; }
    public void setCustConvFee(BigDecimal custConvFee) { this.custConvFee = custConvFee; }

    public BigDecimal getCharges() { return charges; }
    public void setCharges(BigDecimal charges) { this.charges = charges; }

    public BigDecimal getTotalDeducted() { return totalDeducted; }
    public void setTotalDeducted(BigDecimal totalDeducted) { this.totalDeducted = totalDeducted; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public BbpsStatus getStatus() { return status; }
    public void setStatus(BbpsStatus status) { this.status = status; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }

    public String getTxnRefId() { return txnRefId; }
    public void setTxnRefId(String txnRefId) { this.txnRefId = txnRefId; }

    public String getApprovalRefNumber() { return approvalRefNumber; }
    public void setApprovalRefNumber(String approvalRefNumber) { this.approvalRefNumber = approvalRefNumber; }

    public Long getLedgerMerchantTxnId() { return ledgerMerchantTxnId; }
    public void setLedgerMerchantTxnId(Long ledgerMerchantTxnId) { this.ledgerMerchantTxnId = ledgerMerchantTxnId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
