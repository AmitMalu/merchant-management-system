package com.project2.ism.DTO;

import java.math.BigDecimal;

public class TopMerchantDTO {
    private String merchantName;
    private String merchantId;
    private BigDecimal revenue;
    private Long transactionCount;

    public TopMerchantDTO(String merchantName, String merchantId, BigDecimal revenue, Long transactionCount) {
        this.merchantName = merchantName;
        this.merchantId = merchantId;
        this.revenue = revenue;
        this.transactionCount = transactionCount;
    }

    // Getters and Setters
    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    public Long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(Long transactionCount) {
        this.transactionCount = transactionCount;
    }
}
