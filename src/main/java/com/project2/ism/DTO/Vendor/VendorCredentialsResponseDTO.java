package com.project2.ism.DTO.Vendor;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

public class VendorCredentialsResponseDTO {

    private Long id;
    private Long vendorId;
    private String vendorName;
    private Long productId;
    private String productName;

    private String baseUrlUat;
    private String baseUrlProd;

    // Keys and user ids. Filled ONLY by the single-record call (GET /{id}) that the Edit form uses,
    // never by the list, and left out of the JSON when empty.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String secretKeyUat;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String saltKeyUat;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String encryptDecryptKeyUat;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String userIdUat;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String secretKeyProd;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String saltKeyProd;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String encryptDecryptKeyProd;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String userIdProd;

    private String activeEnvironment;
    private Boolean isActive;

    private LocalDateTime createdOn;
    private LocalDateTime updatedOn;

    public VendorCredentialsResponseDTO() {
    }

    public VendorCredentialsResponseDTO(Long id, Long vendorId, String vendorName, Long productId, String productName, String baseUrlUat, String baseUrlProd, String activeEnvironment, Boolean isActive, LocalDateTime createdOn, LocalDateTime updatedOn) {
        this.id = id;
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        this.productId = productId;
        this.productName = productName;
        this.baseUrlUat = baseUrlUat;
        this.baseUrlProd = baseUrlProd;
        this.activeEnvironment = activeEnvironment;
        this.isActive = isActive;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
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

    public String getBaseUrlProd() {
        return baseUrlProd;
    }

    public void setBaseUrlProd(String baseUrlProd) {
        this.baseUrlProd = baseUrlProd;
    }

    public String getActiveEnvironment() {
        return activeEnvironment;
    }

    public void setActiveEnvironment(String activeEnvironment) {
        this.activeEnvironment = activeEnvironment;
    }

    public String getSecretKeyUat() { return secretKeyUat; }
    public void setSecretKeyUat(String secretKeyUat) { this.secretKeyUat = secretKeyUat; }

    public String getSaltKeyUat() { return saltKeyUat; }
    public void setSaltKeyUat(String saltKeyUat) { this.saltKeyUat = saltKeyUat; }

    public String getEncryptDecryptKeyUat() { return encryptDecryptKeyUat; }
    public void setEncryptDecryptKeyUat(String encryptDecryptKeyUat) { this.encryptDecryptKeyUat = encryptDecryptKeyUat; }

    public String getUserIdUat() { return userIdUat; }
    public void setUserIdUat(String userIdUat) { this.userIdUat = userIdUat; }

    public String getSecretKeyProd() { return secretKeyProd; }
    public void setSecretKeyProd(String secretKeyProd) { this.secretKeyProd = secretKeyProd; }

    public String getSaltKeyProd() { return saltKeyProd; }
    public void setSaltKeyProd(String saltKeyProd) { this.saltKeyProd = saltKeyProd; }

    public String getEncryptDecryptKeyProd() { return encryptDecryptKeyProd; }
    public void setEncryptDecryptKeyProd(String encryptDecryptKeyProd) { this.encryptDecryptKeyProd = encryptDecryptKeyProd; }

    public String getUserIdProd() { return userIdProd; }
    public void setUserIdProd(String userIdProd) { this.userIdProd = userIdProd; }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(LocalDateTime createdOn) {
        this.createdOn = createdOn;
    }

    public LocalDateTime getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(LocalDateTime updatedOn) {
        this.updatedOn = updatedOn;
    }
}
