package com.project2.ism.DTO.ReportDTO;

import java.math.BigDecimal;

public class MerchantReportDTO {

    private String businessName;
    private String franchiseName;
    private BigDecimal walletBalance;
    private Long totalDevices;
    private Long totalProducts;
    private String gstNumber;
    private String panNumber;
    private String registrationNumber;
    private String contactPersonName;
    private String contactPersonPhoneNumber;
    private String contactPersonEmail;

    public MerchantReportDTO(String businessName, String franchiseName, BigDecimal walletBalance,
                             Long totalDevices, Long totalProducts, String gstNumber, String panNumber,
                             String registrationNumber, String contactPersonName, String contactPersonPhoneNumber,
                             String contactPersonEmail) {
        this.businessName = businessName;
        this.franchiseName = franchiseName;
        this.walletBalance = walletBalance != null ? walletBalance : BigDecimal.ZERO;
        this.totalDevices = totalDevices != null ? totalDevices : 0L;
        this.totalProducts = totalProducts != null ? totalProducts : 0L;
        this.gstNumber = gstNumber;
        this.panNumber = panNumber;
        this.registrationNumber = registrationNumber;
        this.contactPersonName = contactPersonName;
        this.contactPersonPhoneNumber = contactPersonPhoneNumber;
        this.contactPersonEmail = contactPersonEmail;
    }

    // getters and setters...

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getFranchiseName() {
        return franchiseName;
    }

    public void setFranchiseName(String franchiseName) {
        this.franchiseName = franchiseName;
    }

    public BigDecimal getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(BigDecimal walletBalance) {
        this.walletBalance = walletBalance;
    }

    public Long getTotalDevices() {
        return totalDevices;
    }

    public void setTotalDevices(Long totalDevices) {
        this.totalDevices = totalDevices;
    }

    public Long getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Long totalProducts) {
        this.totalProducts = totalProducts;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getContactPersonName() {
        return contactPersonName;
    }

    public void setContactPersonName(String contactPersonName) {
        this.contactPersonName = contactPersonName;
    }

    public String getContactPersonPhoneNumber() {
        return contactPersonPhoneNumber;
    }

    public void setContactPersonPhoneNumber(String contactPersonPhoneNumber) {
        this.contactPersonPhoneNumber = contactPersonPhoneNumber;
    }

    public String getContactPersonEmail() {
        return contactPersonEmail;
    }

    public void setContactPersonEmail(String contactPersonEmail) {
        this.contactPersonEmail = contactPersonEmail;
    }
}
