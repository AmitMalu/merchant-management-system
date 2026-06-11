package com.project2.ism.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProductCategoryDTO {

    @JsonProperty("id") // Maps "id" from JSON to "productCategoryId" field
    private Long productCategoryId;

    @JsonProperty("categoryName") // Maps "categoryName" from JSON to "productCategoryName" field
    private String productCategoryName;

    public ProductCategoryDTO() {
    }

    public ProductCategoryDTO(Long productCategoryId, String productCategoryName) {
        this.productCategoryId = productCategoryId;
        this.productCategoryName = productCategoryName;
    }

    public Long getProductCategoryId() {
        return productCategoryId;
    }

    public void setProductCategoryId(Long productCategoryId) {
        this.productCategoryId = productCategoryId;
    }

    public String getProductCategoryName() {
        return productCategoryName;
    }

    public void setProductCategoryName(String productCategoryName) {
        this.productCategoryName = productCategoryName;
    }
}