package com.project2.ism.DTO.RazorPay;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RazorpayNotificationDTO {

    private String txnId;
    private String username;

    private Double amount;
    private Double amountOriginal;
    private Double amountCashBack;
    private Double amountAdditional;

    private String authCode;
    private String batchNumber;

    private String cardLastFourDigit;
    private String formattedPan;
    private String paymentCardBin;
    private String paymentCardBrand;
    private String paymentCardType;
    private String paymentMode;

    private String payerName;
    private String customerName;
    private String customerMobileNumber;
    private String userMobile;
    private String customerEmail;
    private String bankName;

    private String merchantName;
    private String orgCode;
    private String merchantCode;

    private String mid;
    private String tid;
    private String deviceSerial;
    private String rrNumber;

    // Reference numbers (1–7)
    private String externalRefNumber;
    private String externalRefNumber2;
    private String externalRefNumber3;
    private String externalRefNumber4;
    private String externalRefNumber5;
    private String externalRefNumber6;
    private String externalRefNumber7;

    // Card extra fields
    private String cardTxnType;        // NEW
    private String cardTxnTypeDesc;    // NEW
    private String cardClassification; // NEW

    private String status;
    private String settlementStatus;
    private String txnType;

    private String chargeSlipDate; // ISO format from Razorpay
    private String readableChargeSlipDate;

    private String paymentGateway;
    private String referenceTransactionId;

    // getters & setters

    public RazorpayNotificationDTO() {
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Double getAmount() {
        return amount;
    }

    public String getCustomerMobileNumber() {
        return customerMobileNumber;
    }

    public void setCustomerMobileNumber(String customerMobileNumber) {
        this.customerMobileNumber = customerMobileNumber;
    }

    public String getCardTxnType() {
        return cardTxnType;
    }

    public void setCardTxnType(String cardTxnType) {
        this.cardTxnType = cardTxnType;
    }

    public String getCardTxnTypeDesc() {
        return cardTxnTypeDesc;
    }

    public void setCardTxnTypeDesc(String cardTxnTypeDesc) {
        this.cardTxnTypeDesc = cardTxnTypeDesc;
    }

    public String getCardClassification() {
        return cardClassification;
    }

    public void setCardClassification(String cardClassification) {
        this.cardClassification = cardClassification;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getAmountOriginal() {
        return amountOriginal;
    }

    public String getExternalRefNumber() {
        return externalRefNumber;
    }

    public void setExternalRefNumber(String externalRefNumber) {
        this.externalRefNumber = externalRefNumber;
    }

    public String getExternalRefNumber2() {
        return externalRefNumber2;
    }

    public void setExternalRefNumber2(String externalRefNumber2) {
        this.externalRefNumber2 = externalRefNumber2;
    }

    public String getExternalRefNumber3() {
        return externalRefNumber3;
    }

    public void setExternalRefNumber3(String externalRefNumber3) {
        this.externalRefNumber3 = externalRefNumber3;
    }

    public String getExternalRefNumber4() {
        return externalRefNumber4;
    }

    public void setExternalRefNumber4(String externalRefNumber4) {
        this.externalRefNumber4 = externalRefNumber4;
    }

    public String getExternalRefNumber5() {
        return externalRefNumber5;
    }

    public void setExternalRefNumber5(String externalRefNumber5) {
        this.externalRefNumber5 = externalRefNumber5;
    }

    public String getExternalRefNumber6() {
        return externalRefNumber6;
    }

    public void setExternalRefNumber6(String externalRefNumber6) {
        this.externalRefNumber6 = externalRefNumber6;
    }

    public String getExternalRefNumber7() {
        return externalRefNumber7;
    }

    public void setExternalRefNumber7(String externalRefNumber7) {
        this.externalRefNumber7 = externalRefNumber7;
    }

    public void setAmountOriginal(Double amountOriginal) {
        this.amountOriginal = amountOriginal;
    }

    public Double getAmountCashBack() {
        return amountCashBack;
    }

    public void setAmountCashBack(Double amountCashBack) {
        this.amountCashBack = amountCashBack;
    }

    public Double getAmountAdditional() {
        return amountAdditional;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public void setAmountAdditional(Double amountAdditional) {
        this.amountAdditional = amountAdditional;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getCardLastFourDigit() {
        return cardLastFourDigit;
    }

    public void setCardLastFourDigit(String cardLastFourDigit) {
        this.cardLastFourDigit = cardLastFourDigit;
    }

    public String getFormattedPan() {
        return formattedPan;
    }

    public void setFormattedPan(String formattedPan) {
        this.formattedPan = formattedPan;
    }

    public String getPaymentCardBin() {
        return paymentCardBin;
    }

    public void setPaymentCardBin(String paymentCardBin) {
        this.paymentCardBin = paymentCardBin;
    }

    public String getPaymentCardBrand() {
        return paymentCardBrand;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public void setPaymentCardBrand(String paymentCardBrand) {
        this.paymentCardBrand = paymentCardBrand;
    }

    public String getPaymentCardType() {
        return paymentCardType;
    }

    public void setPaymentCardType(String paymentCardType) {
        this.paymentCardType = paymentCardType;
    }

    public String getPayerName() {
        return payerName;
    }

    public void setPayerName(String payerName) {
        this.payerName = payerName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getUserMobile() {
        return userMobile;
    }

    public void setUserMobile(String userMobile) {
        this.userMobile = userMobile;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
    }

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getDeviceSerial() {
        return deviceSerial;
    }

    public void setDeviceSerial(String deviceSerial) {
        this.deviceSerial = deviceSerial;
    }

    public String getRrNumber() {
        return rrNumber;
    }

    public void setRrNumber(String rrNumber) {
        this.rrNumber = rrNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSettlementStatus() {
        return settlementStatus;
    }

    public void setSettlementStatus(String settlementStatus) {
        this.settlementStatus = settlementStatus;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getChargeSlipDate() {
        return chargeSlipDate;
    }

    public void setChargeSlipDate(String chargeSlipDate) {
        this.chargeSlipDate = chargeSlipDate;
    }

    public String getReadableChargeSlipDate() {
        return readableChargeSlipDate;
    }

    public void setReadableChargeSlipDate(String readableChargeSlipDate) {
        this.readableChargeSlipDate = readableChargeSlipDate;
    }

    public String getPaymentGateway() {
        return paymentGateway;
    }

    public void setPaymentGateway(String paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    public String getReferenceTransactionId() {
        return referenceTransactionId;
    }

    public void setReferenceTransactionId(String referenceTransactionId) {
        this.referenceTransactionId = referenceTransactionId;
    }
}
