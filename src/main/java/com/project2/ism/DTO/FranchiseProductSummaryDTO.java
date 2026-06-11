package com.project2.ism.DTO;

public class FranchiseProductSummaryDTO {
    private Long productId;
    private String productName;
    private String productCode;
    private String productCategory;
    private Integer totalQuantity;

    private Integer valid;

    private Integer inTransit;
    public FranchiseProductSummaryDTO(Long productId, String productName, String productCode, String productCategory,
                                      Integer totalQuantity,Integer valid, Integer inTransit) {

        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.productCategory = productCategory;
        this.totalQuantity = totalQuantity;
        this.valid = valid;
        this.inTransit = inTransit;
    }

    // getters + setters

    public Integer getValid() {
        return valid;
    }

    public void setValid(Integer valid) {
        this.valid = valid;
    }

    public Integer getInTransit() {
        return inTransit;
    }

    public void setInTransit(Integer inTransit) {
        this.inTransit = inTransit;
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


