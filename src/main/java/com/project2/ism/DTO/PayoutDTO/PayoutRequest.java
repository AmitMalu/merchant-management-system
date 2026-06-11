package com.project2.ism.DTO.PayoutDTO;



import java.math.BigDecimal;

/**
 * Request DTO for initiating payout
 */
public class PayoutRequest {

    private Long initiatorId; // merchant or franchise ID
    private String initiatorType; // "MERCHANT" or "FRANCHISE"
    private BigDecimal amount;
    private String paymentMode; // "IMPS", "NEFT", "RTGS"
    private String paymentPurpose; // code from purpose master
    private String beneficiaryBank; // code from bank master
    private String beneficiaryAccountNumber;
    private String beneficiaryIFSC;
    private String beneficiaryMobileNumber;
    private String beneficiaryLocation; // state code from state master
    private String beneficiaryName;
    private String merchantRefId; // unique reference from merchant/franchise
    private String lat;
    private String longitude;
    private String udf1;
    private String udf2;
    private String udf3;

    // Getters and setters
    public Long getInitiatorId() { return initiatorId; }
    public void setInitiatorId(Long initiatorId) { this.initiatorId = initiatorId; }

    public String getInitiatorType() { return initiatorType; }
    public void setInitiatorType(String initiatorType) { this.initiatorType = initiatorType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getPaymentPurpose() { return paymentPurpose; }
    public void setPaymentPurpose(String paymentPurpose) { this.paymentPurpose = paymentPurpose; }

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

    public String getMerchantRefId() { return merchantRefId; }
    public void setMerchantRefId(String merchantRefId) { this.merchantRefId = merchantRefId; }

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