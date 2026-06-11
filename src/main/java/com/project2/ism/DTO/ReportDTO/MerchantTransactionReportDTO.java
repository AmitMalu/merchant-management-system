package com.project2.ism.DTO.ReportDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

// ============================================================================
// MERCHANT TRANSACTION REPORT DTO - IMPROVED
// ============================================================================

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MerchantTransactionReportDTO {
    private Long customTxnId;
    private String txnId;
    private String actionOnBalance;
    private LocalDateTime txnDate;
    private BigDecimal txnAmount;
    private LocalDateTime settleDate;
    private String authCode;
    private String tid;
    private BigDecimal settlementPercentage;
    private BigDecimal settleAmount;
    private BigDecimal systemFee;

    @JsonIgnore
    private BigDecimal grossCharge;

    private BigDecimal merchantRate;
    private BigDecimal franchiseRate;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal gstAmount;
    private BigDecimal systemFeeExGST;
    private String brandType;
    private String cardType;
    private String cardClassification;
    private String merchantName;
    private String franchiseName;
    private String state;
    private String service;

    /**
     * Enhanced constructor with explicit PAYOUT/REFUND handling
     * All parameters can be null - constructor will handle gracefully
     */
    public MerchantTransactionReportDTO(
            Long customTxnId,
            String txnId,
            String actionOnBalance,
            LocalDateTime txnDate,
            BigDecimal txnAmount,
            LocalDateTime settleDate,
            String authCode,
            String tid,
            BigDecimal merchantNetAmount,
            BigDecimal grossCharge,
            BigDecimal franchiseNetAmount,
            BigDecimal charge,
            String brandType,
            String cardType,
            String cardClassification,
            String merchantName,
            String franchiseName,
            String state,
            String service
    ) {
        // Basic fields - always populated
        this.customTxnId = customTxnId;
        this.txnId = txnId;
        this.actionOnBalance = actionOnBalance;
        this.txnDate = txnDate;
        this.txnAmount = txnAmount != null ? txnAmount : BigDecimal.ZERO;
        this.settleDate = settleDate;
        this.merchantName = merchantName;
        this.state = state;
        this.service = service;

        // Store raw values
        this.grossCharge = grossCharge;

        // ============== WALLET TRANSACTIONS (PAYOUT/REFUND) ==============
        if (isWalletTransaction(service)) {
            handleWalletTransaction(merchantNetAmount, charge);
            return; // Early exit - no rate calculations needed
        }

        // ============== SETTLEMENT TRANSACTIONS ==============
        handleSettlementTransaction(
                authCode, tid, merchantNetAmount, charge,
                franchiseNetAmount, brandType, cardType, cardClassification, franchiseName
        );
    }

    /**
     * Check if this is a wallet transaction (PAYOUT/REFUND)
     */
    private boolean isWalletTransaction(String service) {
        return service != null &&
                (service.equalsIgnoreCase("PAYOUT") ||
                        service.equalsIgnoreCase("PAYOUT_REFUND"));
    }

    /**
     * Handle PAYOUT and PAYOUT_REFUND transactions
     */
    private void handleWalletTransaction(BigDecimal merchantNetAmount, BigDecimal charge) {
        // For payouts, netAmount is the actual payout amount (negative for DEBIT)
        this.settleAmount = merchantNetAmount != null ? merchantNetAmount : BigDecimal.ZERO;
        this.systemFee = charge != null ? charge : BigDecimal.ZERO;

        // No rates, commissions, or card details for wallet transactions
        this.merchantRate = null;
        this.franchiseRate = null;
        this.commissionRate = null;
        this.commissionAmount = BigDecimal.ZERO;
        this.settlementPercentage = null;
        this.authCode = null;
        this.tid = null;
        this.brandType = null;
        this.cardType = null;
        this.cardClassification = null;
        this.franchiseName = null;
    }

    /**
     * Handle Settlement transactions with proper rate calculations
     */
    private void handleSettlementTransaction(
            String authCode, String tid, BigDecimal merchantNetAmount,
            BigDecimal charge, BigDecimal franchiseNetAmount,
            String brandType, String cardType, String cardClassification,
            String franchiseName) {

        // Vendor/card details
        this.authCode = authCode;
        this.tid = tid;
        this.brandType = brandType;
        this.cardType = cardType;
        this.cardClassification = cardClassification;

        // Settlement amounts
        this.settleAmount = merchantNetAmount != null ? merchantNetAmount : BigDecimal.ZERO;
        this.systemFee = charge != null ? charge : BigDecimal.ZERO;
        this.commissionAmount = franchiseNetAmount;
        this.franchiseName = franchiseName;

        // Calculate rates only if we have valid transaction amount
        if (txnAmount == null || txnAmount.compareTo(BigDecimal.ZERO) <= 0) {
            setDefaultRates();
            return;
        }

        // Calculate merchant rate (what merchant pays)
        if (merchantNetAmount != null) {
            BigDecimal merchantCharge = txnAmount.subtract(merchantNetAmount);
            this.merchantRate = merchantCharge
                    .divide(txnAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            this.settlementPercentage = this.merchantRate;
        } else {
            setDefaultRates();
            return;
        }

        // Calculate franchise commission rates (if applicable)
        if (franchiseNetAmount != null && settleAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.commissionRate = franchiseNetAmount
                    .divide(settleAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            this.franchiseRate = this.merchantRate.subtract(this.commissionRate);
        } else {
            // Direct merchant - no franchise
            this.franchiseRate = null;
            this.commissionRate = null;
        }
    }

    /**
     * Set default null rates when calculation is not possible
     */
    private void setDefaultRates() {
        this.merchantRate = null;
        this.franchiseRate = null;
        this.commissionRate = null;
        this.settlementPercentage = null;
    }


    private static BigDecimal nullSafe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private static BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public BigDecimal getSystemFeeExGST() {
        return systemFeeExGST;
    }

    public void setSystemFeeExGST(BigDecimal systemFeeExGST) {
        this.systemFeeExGST = systemFeeExGST;
    }

    public BigDecimal getGstAmount() {
        return gstAmount;
    }

    public void setGstAmount(BigDecimal gstAmount) {
        this.gstAmount = gstAmount;
    }

    public Long getCustomTxnId() {
        return customTxnId;
    }

    public void setCustomTxnId(Long customTxnId) {
        this.customTxnId = customTxnId;
    }

    public String getActionOnBalance() {
        return actionOnBalance;
    }

    public void setActionOnBalance(String actionOnBalance) {
        this.actionOnBalance = actionOnBalance;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public LocalDateTime getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDateTime txnDate) {
        this.txnDate = txnDate;
    }

    public BigDecimal getTxnAmount() {
        return txnAmount;
    }

    public void setTxnAmount(BigDecimal txnAmount) {
        this.txnAmount = txnAmount;
    }

    public LocalDateTime getSettleDate() {
        return settleDate;
    }

    public void setSettleDate(LocalDateTime settleDate) {
        this.settleDate = settleDate;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public BigDecimal getSettlementPercentage() {
        return settlementPercentage;
    }

    public void setSettlementPercentage(BigDecimal settlementPercentage) {
        this.settlementPercentage = settlementPercentage;
    }

    public BigDecimal getGrossCharge() {
        return grossCharge;
    }

    public void setGrossCharge(BigDecimal grossCharge) {
        this.grossCharge = grossCharge;
    }

    public BigDecimal getSettleAmount() {
        return settleAmount;
    }

    public void setSettleAmount(BigDecimal settleAmount) {
        this.settleAmount = settleAmount;
    }

    public BigDecimal getSystemFee() {
        return systemFee;
    }

    public void setSystemFee(BigDecimal systemFee) {
        this.systemFee = systemFee;
    }

    public BigDecimal getMerchantRate() {
        return merchantRate;
    }

    public void setMerchantRate(BigDecimal merchantRate) {
        this.merchantRate = merchantRate;
    }

    public BigDecimal getFranchiseRate() {
        return franchiseRate;
    }

    public void setFranchiseRate(BigDecimal franchiseRate) {
        this.franchiseRate = franchiseRate;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public void setCommissionAmount(BigDecimal commissionAmount) {
        this.commissionAmount = commissionAmount;
    }

    public String getBrandType() {
        return brandType;
    }

    public void setBrandType(String brandType) {
        this.brandType = brandType;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getCardClassification() {
        return cardClassification;
    }

    public void setCardClassification(String cardClassification) {
        this.cardClassification = cardClassification;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getFranchiseName() {
        return franchiseName;
    }

    public void setFranchiseName(String franchiseName) {
        this.franchiseName = franchiseName;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }
}
