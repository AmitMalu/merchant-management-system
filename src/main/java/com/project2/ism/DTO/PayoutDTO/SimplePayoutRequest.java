package com.project2.ism.DTO.PayoutDTO;


import java.math.BigDecimal;

/**
 * Simplified payout request - user just enters amount and mode
 * All beneficiary details fetched from PayoutBanks
 */
public class SimplePayoutRequest {

    private Long payoutBankId; // ID from PayoutBanks table
    private BigDecimal amount;
    private String paymentMode; // "IMPS", "NEFT", "RTGS"
    private String paymentPurpose; // optional - can have default
    //private String merchantRefId; // unique reference for this transaction

    // Optional fields
    private String lat;
    private String longitude;
    private String udf1;
    private String udf2;
    private String udf3;

    // Getters and setters
    public Long getPayoutBankId() { return payoutBankId; }
    public void setPayoutBankId(Long payoutBankId) { this.payoutBankId = payoutBankId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getPaymentPurpose() { return paymentPurpose; }
    public void setPaymentPurpose(String paymentPurpose) { this.paymentPurpose = paymentPurpose; }

//    public String getMerchantRefId() { return merchantRefId; }
//    public void setMerchantRefId(String merchantRefId) { this.merchantRefId = merchantRefId; }

    public String getLat() { return lat; }
    public void setLat(String lat) { this.lat = lat; }

    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }

    public String getUdf1() { return udf1; }
    public void setUdf1(String udf1) { this.udf1 = udf1; }

    public String getUdf2() { return udf2; }
    public void setUdf2(String udf2) { this.udf2 = udf2; }

    public String getUdf3() { return udf3; }
    public void setUdf3(String udf3) { this.udf3 = udf3; }
}