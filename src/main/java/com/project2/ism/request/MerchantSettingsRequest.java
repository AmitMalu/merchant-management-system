package com.project2.ism.request;

import java.math.BigDecimal;

public class MerchantSettingsRequest {

    private BigDecimal lienAmount;
    private Boolean isPayout;
    private Boolean isCreditCardBillPayment;

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
}
