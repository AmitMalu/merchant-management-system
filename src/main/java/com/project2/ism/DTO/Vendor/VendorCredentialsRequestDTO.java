package com.project2.ism.DTO.Vendor;

public class VendorCredentialsRequestDTO {

    private Long vendorId;
    private Long productId; // optional

    // Sandbox/UAT
    private String baseUrlUat;
    private String secretKeyUat;
    private String saltKeyUat;
    private String encryptDecryptKeyUat;
    private String userIdUat;

    // Production
    private String baseUrlProd;
    private String secretKeyProd;
    private String saltKeyProd;
    private String encryptDecryptKeyProd;
    private String userIdProd;

    private String activeEnvironment;    // "UAT" or "PROD"
    private Boolean isActive;

    public VendorCredentialsRequestDTO() {
    }

    public VendorCredentialsRequestDTO(Long vendorId, Long productId, String baseUrlUat, String secretKeyUat, String saltKeyUat, String encryptDecryptKeyUat, String userIdUat, String baseUrlProd, String secretKeyProd, String saltKeyProd, String encryptDecryptKeyProd, String userIdProd, String activeEnvironment, Boolean isActive) {
        this.vendorId = vendorId;
        this.productId = productId;
        this.baseUrlUat = baseUrlUat;
        this.secretKeyUat = secretKeyUat;
        this.saltKeyUat = saltKeyUat;
        this.encryptDecryptKeyUat = encryptDecryptKeyUat;
        this.userIdUat = userIdUat;
        this.baseUrlProd = baseUrlProd;
        this.secretKeyProd = secretKeyProd;
        this.saltKeyProd = saltKeyProd;
        this.encryptDecryptKeyProd = encryptDecryptKeyProd;
        this.userIdProd = userIdProd;
        this.activeEnvironment = activeEnvironment;
        this.isActive = isActive;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getBaseUrlUat() {
        return baseUrlUat;
    }

    public void setBaseUrlUat(String baseUrlUat) {
        this.baseUrlUat = baseUrlUat;
    }

    public String getSecretKeyUat() {
        return secretKeyUat;
    }

    public void setSecretKeyUat(String secretKeyUat) {
        this.secretKeyUat = secretKeyUat;
    }

    public String getSaltKeyUat() {
        return saltKeyUat;
    }

    public void setSaltKeyUat(String saltKeyUat) {
        this.saltKeyUat = saltKeyUat;
    }

    public String getEncryptDecryptKeyUat() {
        return encryptDecryptKeyUat;
    }

    public void setEncryptDecryptKeyUat(String encryptDecryptKeyUat) {
        this.encryptDecryptKeyUat = encryptDecryptKeyUat;
    }

    public String getUserIdUat() {
        return userIdUat;
    }

    public void setUserIdUat(String userIdUat) {
        this.userIdUat = userIdUat;
    }

    public String getBaseUrlProd() {
        return baseUrlProd;
    }

    public void setBaseUrlProd(String baseUrlProd) {
        this.baseUrlProd = baseUrlProd;
    }

    public String getSecretKeyProd() {
        return secretKeyProd;
    }

    public void setSecretKeyProd(String secretKeyProd) {
        this.secretKeyProd = secretKeyProd;
    }

    public String getSaltKeyProd() {
        return saltKeyProd;
    }

    public void setSaltKeyProd(String saltKeyProd) {
        this.saltKeyProd = saltKeyProd;
    }

    public String getEncryptDecryptKeyProd() {
        return encryptDecryptKeyProd;
    }

    public void setEncryptDecryptKeyProd(String encryptDecryptKeyProd) {
        this.encryptDecryptKeyProd = encryptDecryptKeyProd;
    }

    public String getUserIdProd() {
        return userIdProd;
    }

    public void setUserIdProd(String userIdProd) {
        this.userIdProd = userIdProd;
    }

    public String getActiveEnvironment() {
        return activeEnvironment;
    }

    public void setActiveEnvironment(String activeEnvironment) {
        this.activeEnvironment = activeEnvironment;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
