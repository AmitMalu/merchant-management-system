package com.project2.ism.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project2.ism.DTO.Vendor.VendorIDNameDTO;

public class ProductDTO {

    private Long id;
    private String productName;
    private String productCode;

    private VendorIDNameDTO vendor;

    @JsonProperty("productCategory") // Maps "productCategory" from JSON to "category" field
    private ProductCategoryDTO category;

    private String model;
    private String brand;
    private String description;
    private Integer warrantyPeriod;
    private String warrantyType;
    private String hsn;
    private boolean status;
    private Integer minOrderQuantity;
    private Integer maxOrderQuantity;
    private String remarks;

    public ProductDTO() {
    }

    public ProductDTO(Long id, String productName, String productCode, VendorIDNameDTO vendor, ProductCategoryDTO category, String model, String brand, String description, Integer warrantyPeriod, String warrantyType, String hsn, boolean status, Integer minOrderQuantity, Integer maxOrderQuantity, String remarks) {
        this.id = id;
        this.productName = productName;
        this.productCode = productCode;
        this.vendor = vendor;
        this.category = category;
        this.model = model;
        this.brand = brand;
        this.description = description;
        this.warrantyPeriod = warrantyPeriod;
        this.warrantyType = warrantyType;
        this.hsn = hsn;
        this.status = status;
        this.minOrderQuantity = minOrderQuantity;
        this.maxOrderQuantity = maxOrderQuantity;
        this.remarks = remarks;
    }
    public ProductDTO(Long id, String productCode, String productName) {
        this.id = id;
        this.productCode = productCode;
        this.productName = productName;
    }


    // All getters and setters remain the same
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public VendorIDNameDTO getVendor() {
        return vendor;
    }

    public void setVendor(VendorIDNameDTO vendor) {
        this.vendor = vendor;
    }

    public ProductCategoryDTO getCategory() {
        return category;
    }

    public void setCategory(ProductCategoryDTO category) {
        this.category = category;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getWarrantyPeriod() {
        return warrantyPeriod;
    }

    public void setWarrantyPeriod(Integer warrantyPeriod) {
        this.warrantyPeriod = warrantyPeriod;
    }

    public String getWarrantyType() {
        return warrantyType;
    }

    public void setWarrantyType(String warrantyType) {
        this.warrantyType = warrantyType;
    }

    public String getHsn() {
        return hsn;
    }

    public void setHsn(String hsn) {
        this.hsn = hsn;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public Integer getMinOrderQuantity() {
        return minOrderQuantity;
    }

    public void setMinOrderQuantity(Integer minOrderQuantity) {
        this.minOrderQuantity = minOrderQuantity;
    }

    public Integer getMaxOrderQuantity() {
        return maxOrderQuantity;
    }

    public void setMaxOrderQuantity(Integer maxOrderQuantity) {
        this.maxOrderQuantity = maxOrderQuantity;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}