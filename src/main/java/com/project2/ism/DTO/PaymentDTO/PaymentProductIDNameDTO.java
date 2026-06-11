package com.project2.ism.DTO.PaymentDTO;

public class PaymentProductIDNameDTO {

    private Long id;
    private String productName;
    private String productCode;

    public PaymentProductIDNameDTO(Long id, String productName, String productCode) {
        this.id = id;
        this.productName = productName;
        this.productCode = productCode;
    }

    // getters + setters (you can generate)

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
}
