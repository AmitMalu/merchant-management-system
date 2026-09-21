package com.project2.ism.request;

import java.math.BigDecimal;

public class FranchiseWalletTransferRequest {

    // "CREDIT" (franchise -> merchant) or "DEBIT" (merchant -> franchise)
    private String action;
    private BigDecimal amount;
    private String remark;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
