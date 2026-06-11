package com.project2.ism.DTO.ReportDTO;

import java.math.BigDecimal;

public class FranchiseReportsDTO {
    private String franchiseName;
    private Long totalMerchants;
    private BigDecimal walletBalance;
    private Long totalDevices;
    private Long totalProducts;
    private String gstNumber;
    private String panNumber;
    private String registrationNumber;
    private String contactPersonName;
    private String contactPersonPhoneNumber;
    private String contactPersonEmail;


    public FranchiseReportsDTO(String franchiseName, Long totalMerchants, BigDecimal walletBalance, Long totalDevices,Long totalProducts, String gstNumber, String panNumber, String registrationNumber, String contactPersonName, String contactPersonPhoneNumber, String contactPersonEmail) {
        this.franchiseName = franchiseName;
        this.totalMerchants = totalMerchants;
        this.walletBalance = walletBalance;
        this.totalDevices = totalDevices;
        this.totalProducts = totalProducts;
        this.gstNumber = gstNumber;
        this.panNumber = panNumber;
        this.registrationNumber = registrationNumber;
        this.contactPersonName = contactPersonName;
        this.contactPersonPhoneNumber = contactPersonPhoneNumber;
        this.contactPersonEmail = contactPersonEmail;
    }
    // getters + setters

    public String getFranchiseName() {
        return franchiseName;
    }

    public void setFranchiseName(String franchiseName) {
        this.franchiseName = franchiseName;
    }

    public Long getTotalMerchants() {
        return totalMerchants;
    }

    public void setTotalMerchants(Long totalMerchants) {
        this.totalMerchants = totalMerchants;
    }

    public BigDecimal getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(BigDecimal walletBalance) {
        this.walletBalance = walletBalance;
    }

    public Long getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Long totalProducts) {
        this.totalProducts = totalProducts;
    }

    public Long getTotalDevices() {
        return totalDevices;
    }

    public void setTotalDevices(Long totalDevices) {
        this.totalDevices = totalDevices;
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

