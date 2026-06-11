package com.project2.ism.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.jmx.export.annotation.ManagedNotifications;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vendor_transactions",
        indexes = {
                @Index(name = "idx_vt_mid", columnList = "mid"),
                @Index(name = "idx_vt_tid", columnList = "tid"),
                @Index(name = "idx_vendor_settled", columnList = "settled, date"),
                @Index(name = "idx_vendor_mid_tid", columnList = "mid, tid, settled"),
                @Index(name = "idx_vendor_batch", columnList = "settlement_batch_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_vendor_txn",
                        columnNames = {"transactionReferenceId", "date","amount","mid","tid","txnType"})
        }
)

public class VendorTransactions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long internalId;

    @NotBlank(message = "Transaction ID is required")
    @Column(name = "transaction_ref_id", nullable = false, unique = true)
    private String transactionReferenceId;

    @NotNull(message = "Date is required")
    @Column(name = "Date")
    private LocalDateTime date;

    //@NotBlank(message = "Mobile number is required")
    //@Pattern(regexp = "\\d{10,15}", message = "Mobile must be numeric and 10-15 digits")
    @Column(name = "Mobile")
    private String mobile;

//    @Email(message = "Email should be valid")
    @Column(name = "Email")
    private String email;

    //@NotBlank(message = "Consumer is required")
    @Column(name = "Consumer")
    private String consumer;

    //@NotBlank(message = "Username is required")
    @Column(name = "Username")
    private String username;

    //@NotBlank(message = "Type is required")
    @Column(name = "Type")
    private String type;

    //@NotBlank(message = "Mode is required")
    @Column(name = "Mode")
    private String mode;

    @NotNull(message = "Amount is required")
    @PositiveOrZero(message = "Amount must be zero or positive")
    @Column(name = "Amount")
    private BigDecimal amount;

    //@PositiveOrZero(message = "Tip must be zero or positive")
    @Column(name = "Tip")
    private BigDecimal tip;

    //@PositiveOrZero(message = "Cash at POS must be zero or positive")
    @Column(name = "Cash_at_POS")
    private BigDecimal cashAtPos;

    @NotBlank(message = "Txn_Type required")
    @Column(name = "Txn_Type")
    private String txnType;

    @Column(name = "Auth_Code")
    private String authCode;

    //@NotBlank(message = "card required")
    @Column(name = "Card")
    private String card;

//  @NotBlank(message = "Issuing bank required")
    @Column(name = "Issuing_Bank")
    private String issuingBank;

    //@NotBlank(message = "card type required")
    @Column(name = "Card_Type")
    private String cardType;

   // @NotBlank(message = "Brand Type required")
    @Column(name = "Brand_Type")
    private String brandType;

    @Column(name = "Card_Classification")
    private String cardClassification;

    @Column(name = "Card_Txn_Type")
    private String cardTxnType;

    @NotBlank(message = "RRN required")
    @Column(name = "RRN")
    private String rrn;

    @Column(name = "Invoice#")
    private String invoiceNumber;

    @Column(name = "Device_Serial")
    private String deviceSerial;

    @Column(name = "Merchant")
    private String merchant;

    @Column(name = "Category")
    private String category;

    @Column(name = "Status")
    private String status;

    @Column(name = "Settled_On")
    private LocalDateTime settledOn;

    @Column(name = "Labels")
    private String labels;

    @NotBlank(message = "MID required")
    @Column(name = "MID")
    private String mid;

    @NotBlank(message = "TID required")
    @Column(name = "TID")
    private String tid;

    @Column(name = "Batch#")
    private String batchNumber;

    @Column(name = "Ref#")
    private String ref;

    @Column(name = "Ref#_1")
    private String ref1;

    @Column(name = "Ref#_2")
    private String ref2;

    @Column(name = "Ref#_3")
    private String ref3;

    @Column(name = "Ref#_4")
    private String ref4;

    @Column(name = "Ref#_5")
    private String ref5;

    @Column(name = "Ref#_6")
    private String ref6;

    @Column(name = "Ref#_7")
    private String ref7;

    @Column(name = "original_transaction_id")
    private String originalTransactionId;

    @Column(name = "Receipt_No")
    private String receiptNo;

    @Column(name = "Error_Code")
    private String errorCode;

    @Column(name = "Additional_Information")
    private String additionalInformation;

    @Column(name = "PG_Error_Code")
    private String pgErrorCode;

    @Column(name = "PG_Error_Message")
    private String pgErrorMessage;

    @Column(name = "Latitude")
    private String latitude;

    @Column(name = "Longitude")
    private String longitude;

    @Column(name = "Payer")
    private String payer;

    @Column(name = "TID_Location")
    private String tidLocation;

    @Column(name = "DX_Mode")
    private String dxMode;

    @Column(name = "Acquiring_Bank")
    private String acquiringBank;

    @Column(name = "Issuing_Bank.1")
    private String issuingBankAlt;

    @Column(name = "settled", nullable = false)
    private Boolean settled = Boolean.FALSE;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "settlement_batch_id")
    private Long settlementBatchId;



    //--------------new fields added as per razorpay
    @Column(name = "org_code")
    private String orgCode;

    @Column(name = "merchant_code")
    private String merchantCode;

    @Column(name = "card_last_four")
    private String cardLastFourDigit;

    @Column(name = "card_bin")
    private String paymentCardBin;

    @Column(name = "settlement_status")
    private String settlementStatus;

    @Column(name = "amount_original")
    private BigDecimal amountOriginal;

    @Column(name = "reference_transaction_id")
    private String referenceTransactionId;

    @Column(name = "payment_gateway")
    private String paymentGateway;


    public VendorTransactions() {
    }

    public Long getInternalId() {
        return internalId;
    }

    public void setInternalId(Long internalId) {
        this.internalId = internalId;
    }

    public String getTransactionReferenceId() {
        return transactionReferenceId;
    }

    public void setTransactionReferenceId(String transactionReferenceId) {
        this.transactionReferenceId = transactionReferenceId;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getTip() {
        return tip;
    }

    public void setTip(BigDecimal tip) {
        this.tip = tip;
    }

    public BigDecimal getCashAtPos() {
        return cashAtPos;
    }

    public void setCashAtPos(BigDecimal cashAtPos) {
        this.cashAtPos = cashAtPos;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getCard() {
        return card;
    }

    public void setCard(String card) {
        this.card = card;
    }

    public String getIssuingBank() {
        return issuingBank;
    }

    public void setIssuingBank(String issuingBank) {
        this.issuingBank = issuingBank;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getBrandType() {
        return brandType;
    }

    public void setBrandType(String brandType) {
        this.brandType = brandType;
    }

    public String getCardClassification() {
        return cardClassification;
    }

    public void setCardClassification(String cardClassification) {
        this.cardClassification = cardClassification;
    }

    public String getCardTxnType() {
        return cardTxnType;
    }

    public void setCardTxnType(String cardTxnType) {
        this.cardTxnType = cardTxnType;
    }

    public String getRrn() {
        return rrn;
    }

    public void setRrn(String rrn) {
        this.rrn = rrn;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getDeviceSerial() {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial) {
        this.deviceSerial = deviceSerial;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSettledOn() {
        return settledOn;
    }

    public void setSettledOn(LocalDateTime settledOn) {
        this.settledOn = settledOn;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getRef1() {
        return ref1;
    }

    public void setRef1(String ref1) {
        this.ref1 = ref1;
    }

    public String getRef2() {
        return ref2;
    }

    public void setRef2(String ref2) {
        this.ref2 = ref2;
    }

    public String getRef3() {
        return ref3;
    }

    public void setRef3(String ref3) {
        this.ref3 = ref3;
    }

    public String getRef4() {
        return ref4;
    }

    public void setRef4(String ref4) {
        this.ref4 = ref4;
    }

    public String getRef5() {
        return ref5;
    }

    public void setRef5(String ref5) {
        this.ref5 = ref5;
    }

    public String getRef6() {
        return ref6;
    }

    public void setRef6(String ref6) {
        this.ref6 = ref6;
    }

    public String getRef7() {
        return ref7;
    }

    public void setRef7(String ref7) {
        this.ref7 = ref7;
    }

    public String getOriginalTransactionId() {
        return originalTransactionId;
    }

    public void setOriginalTransactionId(String originalTransactionId) {
        this.originalTransactionId = originalTransactionId;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public String getAdditionalInformation() {
        return additionalInformation;
    }

    public void setAdditionalInformation(String additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getPgErrorCode() {
        return pgErrorCode;
    }

    public void setPgErrorCode(String pgErrorCode) {
        this.pgErrorCode = pgErrorCode;
    }

    public String getPgErrorMessage() {
        return pgErrorMessage;
    }

    public void setPgErrorMessage(String pgErrorMessage) {
        this.pgErrorMessage = pgErrorMessage;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getPayer() {
        return payer;
    }

    public void setPayer(String payer) {
        this.payer = payer;
    }

    public String getTidLocation() {
        return tidLocation;
    }

    public void setTidLocation(String tidLocation) {
        this.tidLocation = tidLocation;
    }

    public String getDxMode() {
        return dxMode;
    }

    public void setDxMode(String dxMode) {
        this.dxMode = dxMode;
    }

    public String getAcquiringBank() {
        return acquiringBank;
    }

    public void setAcquiringBank(String acquiringBank) {
        this.acquiringBank = acquiringBank;
    }

    public String getIssuingBankAlt() {
        return issuingBankAlt;
    }

    public void setIssuingBankAlt(String issuingBankAlt) {
        this.issuingBankAlt = issuingBankAlt;
    }

    public Boolean getSettled() {
        return settled;
    }

    public void setSettled(Boolean settled) {
        this.settled = settled;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(LocalDateTime settledAt) {
        this.settledAt = settledAt;
    }

    public Long getSettlementBatchId() {
        return settlementBatchId;
    }

    public void setSettlementBatchId(Long settlementBatchId) {
        this.settlementBatchId = settlementBatchId;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
    }

    public String getCardLastFourDigit() {
        return cardLastFourDigit;
    }

    public void setCardLastFourDigit(String cardLastFourDigit) {
        this.cardLastFourDigit = cardLastFourDigit;
    }

    public String getPaymentCardBin() {
        return paymentCardBin;
    }

    public void setPaymentCardBin(String paymentCardBin) {
        this.paymentCardBin = paymentCardBin;
    }

    public String getSettlementStatus() {
        return settlementStatus;
    }

    public void setSettlementStatus(String settlementStatus) {
        this.settlementStatus = settlementStatus;
    }

    public BigDecimal getAmountOriginal() {
        return amountOriginal;
    }

    public void setAmountOriginal(BigDecimal amountOriginal) {
        this.amountOriginal = amountOriginal;
    }

    public String getReferenceTransactionId() {
        return referenceTransactionId;
    }

    public void setReferenceTransactionId(String referenceTransactionId) {
        this.referenceTransactionId = referenceTransactionId;
    }

    public String getPaymentGateway() {
        return paymentGateway;
    }

    public void setPaymentGateway(String paymentGateway) {
        this.paymentGateway = paymentGateway;
    }
}