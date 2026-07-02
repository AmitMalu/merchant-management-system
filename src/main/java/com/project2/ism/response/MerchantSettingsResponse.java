package com.project2.ism.response;


import java.math.BigDecimal;


public class MerchantSettingsResponse {

    private Long merchantId;

    private BigDecimal lienAmount;

    private Boolean isPayout;

    private Boolean isCreditCardBillPayment;

    private String message;

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public BigDecimal getLienAmount() {
        return lienAmount;
    }

    public void setLienAmount(BigDecimal lienAmount) {
        this.lienAmount = lienAmount;
    }

    public Boolean getIsPayout() {
        return isPayout;
    }

    public void setIsPayout(Boolean isPayout) {
        this.isPayout = isPayout;
    }

    public Boolean getIsCreditCardBillPayment() {
        return isCreditCardBillPayment;
    }

    public void setIsCreditCardBillPayment(Boolean isCreditCardBillPayment) {
        this.isCreditCardBillPayment = isCreditCardBillPayment;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public MerchantSettingsResponse(Long merchantId, BigDecimal lienAmount, Boolean isPayout, Boolean isCreditCardBillPayment, String message) {
        this.merchantId = merchantId;
        this.lienAmount = lienAmount;
        this.isPayout = isPayout;
        this.isCreditCardBillPayment = isCreditCardBillPayment;
        this.message = message;
    }

    public MerchantSettingsResponse() {
    }
}
