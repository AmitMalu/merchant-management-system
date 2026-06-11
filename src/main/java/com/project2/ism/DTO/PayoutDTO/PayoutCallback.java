package com.project2.ism.DTO.PayoutDTO;



import java.math.BigDecimal;

/**
 * Callback DTO received from vendor
 */
public class PayoutCallback {

    private String txnStatus; // "Success", "Failed", "Pending"
    private String txnStatusCode; // "000" success, "001" failed, "002" in progress
    private String txnId; // vendor transaction ID
    private String rrn; // bank RRN number (conditional - only on success)
    private BigDecimal amount;
    private BigDecimal charges;
    private String paymentMode;
    private String paymentPurpose;
    private String merchantRefId;
    private String beneficiaryBank;
    private String beneficiaryAccountNumber;
    private String beneficiaryIFSC;
    private String beneficiaryMobileNumber;
    private String beneficiaryLocation;
    private String beneficiaryName;
    private String responseMessage;
    private String lat;
    private String longitude;
    private String udf1;
    private String udf2;
    private String udf3;

    // Getters and setters
    public String getTxnStatus() { return txnStatus; }
    public void setTxnStatus(String txnStatus) { this.txnStatus = txnStatus; }

    public String getTxnStatusCode() { return txnStatusCode; }
    public void setTxnStatusCode(String txnStatusCode) { this.txnStatusCode = txnStatusCode; }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getCharges() { return charges; }
    public void setCharges(BigDecimal charges) { this.charges = charges; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getPaymentPurpose() { return paymentPurpose; }
    public void setPaymentPurpose(String paymentPurpose) { this.paymentPurpose = paymentPurpose; }

    public String getMerchantRefId() { return merchantRefId; }
    public void setMerchantRefId(String merchantRefId) { this.merchantRefId = merchantRefId; }

    public String getBeneficiaryBank() { return beneficiaryBank; }
    public void setBeneficiaryBank(String beneficiaryBank) { this.beneficiaryBank = beneficiaryBank; }

    public String getBeneficiaryAccountNumber() { return beneficiaryAccountNumber; }
    public void setBeneficiaryAccountNumber(String beneficiaryAccountNumber) {
        this.beneficiaryAccountNumber = beneficiaryAccountNumber;
    }

    public String getBeneficiaryIFSC() { return beneficiaryIFSC; }
    public void setBeneficiaryIFSC(String beneficiaryIFSC) { this.beneficiaryIFSC = beneficiaryIFSC; }

    public String getBeneficiaryMobileNumber() { return beneficiaryMobileNumber; }
    public void setBeneficiaryMobileNumber(String beneficiaryMobileNumber) {
        this.beneficiaryMobileNumber = beneficiaryMobileNumber;
    }

    public String getBeneficiaryLocation() { return beneficiaryLocation; }
    public void setBeneficiaryLocation(String beneficiaryLocation) {
        this.beneficiaryLocation = beneficiaryLocation;
    }

    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }

    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }

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
