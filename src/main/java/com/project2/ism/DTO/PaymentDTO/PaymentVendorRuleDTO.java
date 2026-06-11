package com.project2.ism.DTO.PaymentDTO;


import java.math.BigDecimal;

public class PaymentVendorRuleDTO {

    private Long id;
    private Long vendorId;
    private String vendorName;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    private Integer dailyTransactionLimit;
    private BigDecimal dailyAmountLimit;

    public PaymentVendorRuleDTO() {
    }

    public PaymentVendorRuleDTO(Long id, Long vendorId, String vendorName, BigDecimal minAmount, BigDecimal maxAmount, Integer dailyTransactionLimit, BigDecimal dailyAmountLimit) {
        this.id = id;
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.dailyTransactionLimit = dailyTransactionLimit;
        this.dailyAmountLimit = dailyAmountLimit;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public Integer getDailyTransactionLimit() {
        return dailyTransactionLimit;
    }

    public void setDailyTransactionLimit(Integer dailyTransactionLimit) {
        this.dailyTransactionLimit = dailyTransactionLimit;
    }

    public BigDecimal getDailyAmountLimit() {
        return dailyAmountLimit;
    }

    public void setDailyAmountLimit(BigDecimal dailyAmountLimit) {
        this.dailyAmountLimit = dailyAmountLimit;
    }
}

