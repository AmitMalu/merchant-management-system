package com.project2.ism.DTO;

public class MerchantProductSummaryDTO {

    private Long outwardId;
    private Long productId;
    private String productName;
    private String productCode;
    private String productCategory;
    private Integer totalQuantity;

    public MerchantProductSummaryDTO(Long outwardId, Long productId, String productName, String productCode,
                                     String productCategory, Integer totalQuantity) {
        this.outwardId = outwardId;
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.productCategory = productCategory;
        this.totalQuantity = totalQuantity;
    }
    public MerchantProductSummaryDTO(Long productId, String productName, String productCode,
                                     String productCategory, Integer totalQuantity) {
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.productCategory = productCategory;
        this.totalQuantity = totalQuantity;
    }
    // getters + setters

    public Long getOutwardId() {
        return outwardId;
    }

    public void setOutwardId(Long outwardId) {
        this.outwardId = outwardId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
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

    public String getProductCategory() {
        return productCategory;
    }

    public void setProductCategory(String productCategory) {
        this.productCategory = productCategory;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }
}
