package com.project2.ism.Model;

import com.project2.ism.Helper.TransactionIdGenerator;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.ValueGenerationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@MappedSuperclass
public abstract class TransactionDetailsBase {

    @Id
    @GeneratedValue(generator = "transaction-id-generator")
    @GenericGenerator(name = "transaction-id-generator",
            type = TransactionIdGenerator.class)
    @Column(name = "transaction_id", unique = true, nullable = false)
    private Long transactionId;

    @Column(name = "action_on_balance")
    private String actionOnBalance;

    @Column(name = "amount", precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(name = "bal_after_tran", precision = 38, scale = 2)
    private BigDecimal balAfterTran;

    @Column(name = "bal_before_tran", precision = 38, scale = 2)
    private BigDecimal balBeforeTran;

    @Column(name = "bank_ref_id")
    private String bankRefId;

    @Column(name = "card_holder_bin_no")
    private String cardHolderBinNo;

    @Column(name = "card_holder_mobile")
    private String cardHolderMobile;

    @Column(name = "card_holder_name")
    private String cardHolderName;

    @Column(name = "card_type")
    private String cardType;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(name = "failure_remarks")
    private String failureRemarks;

    @Column(name = "final_balance", precision = 38, scale = 2)
    private BigDecimal finalBalance;

    @Column(name = "medium")
    private String medium;

    @Column(name = "mobile_no")
    private String mobileNo;

    @Column(name = "narration")
    private String narration;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "request_domain_name")
    private String requestDomainName;

    @Column(name = "scheme_hierarchy")
    private String schemeHierarchy;

    @Column(name = "scheme_name")
    private String schemeName;

    @Column(name = "service")
    private String service;

    @Column(name = "sub_operator_name")
    private String subOperatorName;

    @Column(name = "sub_product_name")
    private String subProductName;

    @Column(name = "tran_status")
    private String tranStatus;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "updated_time_of_transaction")
    private LocalDateTime updatedDateAndTimeOfTransaction;

    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "vendor_transaction_id")
    private String vendorTransactionId;

    @Column(name = "net_amount", precision = 38, scale = 2)
    private BigDecimal netAmount;



    private BigDecimal charge;

    private BigDecimal grossCharge;

    public TransactionDetailsBase() {
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public String getActionOnBalance() {
        return actionOnBalance;
    }

    public void setActionOnBalance(String actionOnBalance) {
        this.actionOnBalance = actionOnBalance;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalAfterTran() {
        return balAfterTran;
    }

    public void setBalAfterTran(BigDecimal balAfterTran) {
        this.balAfterTran = balAfterTran;
    }

    public BigDecimal getBalBeforeTran() {
        return balBeforeTran;
    }

    public void setBalBeforeTran(BigDecimal balBeforeTran) {
        this.balBeforeTran = balBeforeTran;
    }

    public String getBankRefId() {
        return bankRefId;
    }

    public void setBankRefId(String bankRefId) {
        this.bankRefId = bankRefId;
    }

    public String getCardHolderBinNo() {
        return cardHolderBinNo;
    }

    public void setCardHolderBinNo(String cardHolderBinNo) {
        this.cardHolderBinNo = cardHolderBinNo;
    }

    public String getCardHolderMobile() {
        return cardHolderMobile;
    }

    public void setCardHolderMobile(String cardHolderMobile) {
        this.cardHolderMobile = cardHolderMobile;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getFailureRemarks() {
        return failureRemarks;
    }

    public void setFailureRemarks(String failureRemarks) {
        this.failureRemarks = failureRemarks;
    }

    public String getMedium() {
        return medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public BigDecimal getFinalBalance() {
        return finalBalance;
    }

    public void setFinalBalance(BigDecimal finalBalance) {
        this.finalBalance = finalBalance;
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public void setMobileNo(String mobileNo) {
        this.mobileNo = mobileNo;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getRequestDomainName() {
        return requestDomainName;
    }

    public void setRequestDomainName(String requestDomainName) {
        this.requestDomainName = requestDomainName;
    }

    public String getSchemeHierarchy() {
        return schemeHierarchy;
    }

    public void setSchemeHierarchy(String schemeHierarchy) {
        this.schemeHierarchy = schemeHierarchy;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getSubOperatorName() {
        return subOperatorName;
    }

    public void setSubOperatorName(String subOperatorName) {
        this.subOperatorName = subOperatorName;
    }

    public String getSubProductName() {
        return subProductName;
    }

    public void setSubProductName(String subProductName) {
        this.subProductName = subProductName;
    }

    public String getTranStatus() {
        return tranStatus;
    }

    public void setTranStatus(String tranStatus) {
        this.tranStatus = tranStatus;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public LocalDateTime getUpdatedDateAndTimeOfTransaction() {
        return updatedDateAndTimeOfTransaction;
    }

    public void setUpdatedDateAndTimeOfTransaction(LocalDateTime updatedDateAndTimeOfTransaction) {
        this.updatedDateAndTimeOfTransaction = updatedDateAndTimeOfTransaction;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getVendorTransactionId() {
        return vendorTransactionId;
    }

    public void setVendorTransactionId(String vendorTransactionId) {
        this.vendorTransactionId = vendorTransactionId;
    }

    public BigDecimal getCharge() {
        return charge;
    }

    public void setCharge(BigDecimal charge) {
        this.charge = charge;
    }

    public BigDecimal getGrossCharge() {
        return grossCharge;
    }

    public void setGrossCharge(BigDecimal grossCharge) {
        this.grossCharge = grossCharge;
    }
}
