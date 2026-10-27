package com.project2.ism.Model.CreditCard;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per credit-card bill the merchant looked up (Fetch Credit Card Bill).
 * The row is created as FETCHED and then moves FETCHED -> PENDING -> SUCCESS / FAILED
 * when the merchant pays it.
 *
 * Keeping the fetched bill on our side means the amount that gets paid always
 * comes from here (what the card issuer reported), never from the browser, and
 * a request_id can only ever be paid by the merchant who fetched it.
 *
 * Only the LAST 4 digits of the card are ever stored.
 */
@Entity
@Table(name = "credit_card_bill_transactions",
        indexes = {
                @Index(name = "idx_ccbt_merchant", columnList = "merchant_id, created_at"),
                @Index(name = "idx_ccbt_request", columnList = "request_id", unique = true)
        })
public class CreditCardBillTransaction {

    public enum Status {FETCHED, PENDING, SUCCESS, FAILED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MERCHANT or FRANCHISE. Rows from before franchises could pay get MERCHANT from the column default. */
    @Column(name = "customer_type", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'MERCHANT'")
    private String customerType = "MERCHANT";

    /** Id of the merchant or franchise (see customerType). The column keeps its original name. */
    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "biller_id", length = 50)
    private String billerId;

    @Column(name = "biller_name")
    private String billerName;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "customer_mobile", length = 15)
    private String customerMobile;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "bill_number", length = 100)
    private String billNumber;

    @Column(name = "bill_date", length = 30)
    private String billDate;

    @Column(name = "due_date", length = 30)
    private String dueDate;

    @Column(name = "bill_amount", precision = 15, scale = 2)
    private BigDecimal billAmount;

    @Column(name = "charges", precision = 15, scale = 2)
    private BigDecimal charges;

    @Column(name = "total_deducted", precision = 15, scale = 2)
    private BigDecimal totalDeducted;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.FETCHED;

    @Column(name = "customer_pan", length = 10)
    private String customerPan;

    @Column(name = "vendor_txn_id", length = 100)
    private String vendorTxnId;

    @Column(name = "wallet_txn_id", length = 100)
    private String walletTxnId;

    @Column(name = "api_ref_number", length = 100)
    private String apiRefNumber;

    @Column(name = "response_code", length = 20)
    private String responseCode;

    @Column(name = "response_message", length = 500)
    private String responseMessage;

    @Column(name = "raw_pay_response", columnDefinition = "LONGTEXT")
    private String rawPayResponse;

    @Column(name = "ledger_merchant_txn_id")
    private Long ledgerMerchantTxnId;

    @Column(name = "complaint_ticket_id", length = 100)
    private String complaintTicketId;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getBillerId() { return billerId; }
    public void setBillerId(String billerId) { this.billerId = billerId; }

    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }

    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }

    public String getCustomerMobile() { return customerMobile; }
    public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getBillNumber() { return billNumber; }
    public void setBillNumber(String billNumber) { this.billNumber = billNumber; }

    public String getBillDate() { return billDate; }
    public void setBillDate(String billDate) { this.billDate = billDate; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public BigDecimal getBillAmount() { return billAmount; }
    public void setBillAmount(BigDecimal billAmount) { this.billAmount = billAmount; }

    public BigDecimal getCharges() { return charges; }
    public void setCharges(BigDecimal charges) { this.charges = charges; }

    public BigDecimal getTotalDeducted() { return totalDeducted; }
    public void setTotalDeducted(BigDecimal totalDeducted) { this.totalDeducted = totalDeducted; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getCustomerPan() { return customerPan; }
    public void setCustomerPan(String customerPan) { this.customerPan = customerPan; }

    public String getVendorTxnId() { return vendorTxnId; }
    public void setVendorTxnId(String vendorTxnId) { this.vendorTxnId = vendorTxnId; }

    public String getWalletTxnId() { return walletTxnId; }
    public void setWalletTxnId(String walletTxnId) { this.walletTxnId = walletTxnId; }

    public String getApiRefNumber() { return apiRefNumber; }
    public void setApiRefNumber(String apiRefNumber) { this.apiRefNumber = apiRefNumber; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }

    public String getRawPayResponse() { return rawPayResponse; }
    public void setRawPayResponse(String rawPayResponse) { this.rawPayResponse = rawPayResponse; }

    public Long getLedgerMerchantTxnId() { return ledgerMerchantTxnId; }
    public void setLedgerMerchantTxnId(Long ledgerMerchantTxnId) { this.ledgerMerchantTxnId = ledgerMerchantTxnId; }

    public String getComplaintTicketId() { return complaintTicketId; }
    public void setComplaintTicketId(String complaintTicketId) { this.complaintTicketId = complaintTicketId; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
