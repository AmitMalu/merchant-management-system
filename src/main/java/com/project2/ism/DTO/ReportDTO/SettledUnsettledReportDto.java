package com.project2.ism.DTO.ReportDTO;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SettledUnsettledReportDto {

    private BigDecimal amount;
    private String brandType;
    private String card;
    private String cardTxnType;
    private String cardType;
    private BigDecimal cashAtPos;
    private LocalDateTime date;
    private String merchant;
    private String mid;
    private String mobile;
    private String payer;
    private String pgErrorCode;
    private String pgErrorMessage;
    private String receiptNo;
    private Boolean settled;
    private LocalDateTime settledAt;
    private LocalDateTime settledOn;
    private Long settlementBatchId;
    private String status;
    private String tid;
    private String transactionReferenceId;
    private String settlementStatus;

    //REQUIRED constructor for JPQL
    public SettledUnsettledReportDto(
            BigDecimal amount,
            String brandType,
            String card,
            String cardTxnType,
            String cardType,
            BigDecimal cashAtPos,
            LocalDateTime date,
            String merchant,
            String mid,
            String mobile,
            String payer,
            String pgErrorCode,
            String pgErrorMessage,
            String receiptNo,
            Boolean settled,
            LocalDateTime settledAt,
            LocalDateTime settledOn,
            Long settlementBatchId,
            String status,
            String tid,
            String transactionReferenceId,
            String settlementStatus
    ) {
        this.amount = amount;
        this.brandType = brandType;
        this.card = card;
        this.cardTxnType = cardTxnType;
        this.cardType = cardType;
        this.cashAtPos = cashAtPos;
        this.date = date;
        this.merchant = merchant;
        this.mid = mid;
        this.mobile = mobile;
        this.payer = payer;
        this.pgErrorCode = pgErrorCode;
        this.pgErrorMessage = pgErrorMessage;
        this.receiptNo = receiptNo;
        this.settled = settled;
        this.settledAt = settledAt;
        this.settledOn = settledOn;
        this.settlementBatchId = settlementBatchId;
        this.status = status;
        this.tid = tid;
        this.transactionReferenceId = transactionReferenceId;
        this.settlementStatus = settlementStatus;
    }

    // getters (or Lombok @Getter)


    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getBrandType() {
        return brandType;
    }

    public void setBrandType(String brandType) {
        this.brandType = brandType;
    }

    public String getCard() {
        return card;
    }

    public void setCard(String card) {
        this.card = card;
    }

    public String getCardTxnType() {
        return cardTxnType;
    }

    public void setCardTxnType(String cardTxnType) {
        this.cardTxnType = cardTxnType;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public BigDecimal getCashAtPos() {
        return cashAtPos;
    }

    public void setCashAtPos(BigDecimal cashAtPos) {
        this.cashAtPos = cashAtPos;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getPayer() {
        return payer;
    }

    public void setPayer(String payer) {
        this.payer = payer;
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

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
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

    public LocalDateTime getSettledOn() {
        return settledOn;
    }

    public void setSettledOn(LocalDateTime settledOn) {
        this.settledOn = settledOn;
    }

    public Long getSettlementBatchId() {
        return settlementBatchId;
    }

    public void setSettlementBatchId(Long settlementBatchId) {
        this.settlementBatchId = settlementBatchId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getTransactionReferenceId() {
        return transactionReferenceId;
    }

    public void setTransactionRefId(String transactionRefId) {
        this.transactionReferenceId = transactionRefId;
    }

    public String getSettlementStatus() {
        return settlementStatus;
    }

    public void setSettlementStatus(String settlementStatus) {
        this.settlementStatus = settlementStatus;
    }
}

