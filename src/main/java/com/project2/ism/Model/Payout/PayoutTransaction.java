package com.project2.ism.Model.Payout;


import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity to track payout transactions across their lifecycle
 */
@Entity
@Table(name = "payout_transactions",
        indexes = {
                @Index(name = "idx_payout_ref", columnList = "merchant_ref_id"),
                @Index(name = "idx_payout_vendor_txn", columnList = "vendor_txn_id"),
                @Index(name = "idx_payout_initiator", columnList = "initiator_type,initiator_id,created_at DESC")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_merchant_ref", columnNames = {"merchant_ref_id"})
        })
public class PayoutTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_ref_id", nullable = true, unique = true)
    private String merchantRefId;

    @Column(name = "vendor_txn_id")
    private String vendorTxnId; // populated after vendor response

    @Column(name = "initiator_type", nullable = false)
    private String initiatorType; // "MERCHANT" or "FRANCHISE"

    @Column(name = "initiator_id", nullable = false)
    private Long initiatorId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "charges", nullable = false, precision = 10, scale = 2)
    private BigDecimal charges;

    @Column(name = "total_deducted", precision = 15, scale = 2)
    private BigDecimal totalDeducted; // amount + charges

    @Column(name = "payment_mode", nullable = false)
    private String paymentMode; // IMPS, NEFT, RTGS

    @Column(name = "payment_purpose", nullable = false)
    private String paymentPurpose;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "beneficiary_account_number", nullable = false)
    private String beneficiaryAccountNumber;

    @Column(name = "beneficiary_ifsc", nullable = false)
    private String beneficiaryIFSC;

    @Column(name = "beneficiary_bank", nullable = false)
    private String beneficiaryBank;

    @Column(name = "beneficiary_mobile")
    private String beneficiaryMobile;

    @Column(name = "beneficiary_location")
    private String beneficiaryLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayoutStatus status; // PENDING, SUCCESS, FAILED

    @Column(name = "status_code")
    private String statusCode; // vendor status code

    @Column(name = "rrn")
    private String rrn; // bank reference number

    @Column(name = "response_message")
    private String responseMessage;

    @Column(name = "latitude")
    private String latitude;

    @Column(name = "longitude")
    private String longitude;

    @Column(name = "udf1")
    private String udf1;

    @Column(name = "udf2")
    private String udf2;

    @Column(name = "udf3")
    private String udf3;

    @Column(name = "ledger_merchant_txn_id")
    private Long ledgerMerchantTxnId; // reference to MerchantTransactionDetails

    @Column(name = "ledger_franchise_txn_id")
    private Long ledgerFranchiseTxnId; // reference to FranchiseTransactionDetails

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId; // reference to PaymentVendor

    @Column(name = "vendor_charges", precision=10, scale=2)
    private BigDecimal vendorCharges;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt; // when status became SUCCESS or FAILED

    public enum PayoutStatus {
        PENDING,
        SUCCESS,
        FAILED
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMerchantRefId() { return merchantRefId; }
    public void setMerchantRefId(String merchantRefId) { this.merchantRefId = merchantRefId; }

    public String getVendorTxnId() { return vendorTxnId; }
    public void setVendorTxnId(String vendorTxnId) { this.vendorTxnId = vendorTxnId; }

    public String getInitiatorType() { return initiatorType; }
    public void setInitiatorType(String initiatorType) { this.initiatorType = initiatorType; }

    public Long getInitiatorId() { return initiatorId; }
    public void setInitiatorId(Long initiatorId) { this.initiatorId = initiatorId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getCharges() { return charges; }
    public void setCharges(BigDecimal charges) { this.charges = charges; }

    public BigDecimal getTotalDeducted() { return totalDeducted; }
    public void setTotalDeducted(BigDecimal totalDeducted) { this.totalDeducted = totalDeducted; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getPaymentPurpose() { return paymentPurpose; }
    public void setPaymentPurpose(String paymentPurpose) { this.paymentPurpose = paymentPurpose; }

    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }

    public String getBeneficiaryAccountNumber() { return beneficiaryAccountNumber; }
    public void setBeneficiaryAccountNumber(String beneficiaryAccountNumber) {
        this.beneficiaryAccountNumber = beneficiaryAccountNumber;
    }

    public String getBeneficiaryIFSC() { return beneficiaryIFSC; }
    public void setBeneficiaryIFSC(String beneficiaryIFSC) { this.beneficiaryIFSC = beneficiaryIFSC; }

    public String getBeneficiaryBank() { return beneficiaryBank; }
    public void setBeneficiaryBank(String beneficiaryBank) { this.beneficiaryBank = beneficiaryBank; }

    public String getBeneficiaryMobile() { return beneficiaryMobile; }
    public void setBeneficiaryMobile(String beneficiaryMobile) {
        this.beneficiaryMobile = beneficiaryMobile;
    }

    public String getBeneficiaryLocation() { return beneficiaryLocation; }
    public void setBeneficiaryLocation(String beneficiaryLocation) {
        this.beneficiaryLocation = beneficiaryLocation;
    }

    public PayoutStatus getStatus() { return status; }
    public void setStatus(PayoutStatus status) { this.status = status; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }

    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }

    public String getLatitude() { return latitude; }
    public void setLatitude(String latitude) { this.latitude = latitude; }

    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }

    public String getUdf1() { return udf1; }
    public void setUdf1(String udf1) { this.udf1 = udf1; }

    public String getUdf2() { return udf2; }
    public void setUdf2(String udf2) { this.udf2 = udf2; }

    public String getUdf3() { return udf3; }
    public void setUdf3(String udf3) { this.udf3 = udf3; }

    public Long getLedgerMerchantTxnId() { return ledgerMerchantTxnId; }
    public void setLedgerMerchantTxnId(Long ledgerMerchantTxnId) {
        this.ledgerMerchantTxnId = ledgerMerchantTxnId;
    }

    public Long getLedgerFranchiseTxnId() { return ledgerFranchiseTxnId; }
    public void setLedgerFranchiseTxnId(Long ledgerFranchiseTxnId) {
        this.ledgerFranchiseTxnId = ledgerFranchiseTxnId;
    }

    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }

    public BigDecimal getVendorCharges() {
        return vendorCharges;
    }

    public void setVendorCharges(BigDecimal vendorCharges) {
        this.vendorCharges = vendorCharges;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
