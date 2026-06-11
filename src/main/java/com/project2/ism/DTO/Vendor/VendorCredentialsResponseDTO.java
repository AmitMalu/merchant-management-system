package com.project2.ism.DTO.Vendor;

import java.time.LocalDateTime;

public class VendorCredentialsResponseDTO {

    private Long id;
    private Long vendorId;
    private String vendorName;
    private Long productId;
    private String productName;

    private String baseUrlUat;
    private String baseUrlProd;

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
