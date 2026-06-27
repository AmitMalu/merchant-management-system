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

    public Boolean getPayout() {
        return isPayout;
    }

    public void setPayout(Boolean payout) {
        isPayout = payout;
    }

    public Boolean getCreditCardBillPayment() {
        return isCreditCardBillPayment;
    }

    public void setCreditCardBillPayment(Boolean creditCardBillPayment) {
        isCreditCardBillPayment = creditCardBillPayment;
    }
}
