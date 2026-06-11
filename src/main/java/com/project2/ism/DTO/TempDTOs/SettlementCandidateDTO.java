package com.project2.ism.DTO.TempDTOs;

import com.project2.ism.Model.VendorTransactions;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SettlementCandidateDTO {
    private Long internalId;
    private String transactionReferenceId;
    private LocalDateTime date;
    private BigDecimal amount;
    private String cardType;
    private String brandType;
    private String cardName;

    private Double appliedRate;   // nullable, merchant rate or franchise/merchant split in future
    private BigDecimal fee;       // charge we take for this transaction
    private BigDecimal netAmount; // credited to merchant wallet
    private String error;         // "NO_ACTIVE_SCHEME", "NO_CARD_RATE", etc.

    public SettlementCandidateDTO() {
    }

    public SettlementCandidateDTO(
            Long internalId,
            String transactionReferenceId,
            LocalDateTime date,
            BigDecimal amount,
            String cardType,
            String brandType,
            String cardName,
            Double appliedRate,
            BigDecimal fee,
            BigDecimal netAmount,
            String error
    ) {
        this.internalId = internalId;
        this.transactionReferenceId = transactionReferenceId;
        this.date = date;
        this.amount = amount;
        this.cardType = cardType;
        this.brandType = brandType;
        this.cardName = cardName;
        this.appliedRate = appliedRate;
        this.fee = fee;
        this.netAmount = netAmount;
        this.error = error;
    }

    // factory method for not found rows
    public static SettlementCandidateDTO notFound(VendorTransactions vt, String error) {
        return new SettlementCandidateDTO(
                vt.getInternalId(),
                vt.getTransactionReferenceId(),
                vt.getDate(),
                vt.getAmount(),
                vt.getCardType(),
                vt.getBrandType(),
                null,              // cardName missing
                null,              // rate missing
                BigDecimal.ZERO,   // no fee
                BigDecimal.ZERO,   // no net
                error
        );
    }

    // getters + setters (generate in IntelliJ)

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public String getCardName() {
        return cardName;
    }

    public void setCardName(String cardName) {
        this.cardName = cardName;
    }

    public Double getAppliedRate() {
        return appliedRate;
    }

    public void setAppliedRate(Double appliedRate) {
        this.appliedRate = appliedRate;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
