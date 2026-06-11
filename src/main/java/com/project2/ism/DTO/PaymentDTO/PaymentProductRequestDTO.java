package com.project2.ism.DTO.PaymentDTO;


import java.util.Set;

public class PaymentProductRequestDTO {

    private String productName;
    private String productCode;
    private Boolean status;
    private Set<Long> modeIds; // NEW


    // getters/setters


    public PaymentProductRequestDTO(String productName, String productCode, Boolean status, Set<Long> modeIds) {
        this.productName = productName;
        this.productCode = productCode;
        this.status = status;
        this.modeIds = modeIds;
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

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public Set<Long> getModeIds() {
        return modeIds;
    }

    public void setModeIds(Set<Long> modeIds) {
        this.modeIds = modeIds;
    }
}
