package com.project2.ism.DTO.PaymentDTO;

import java.time.LocalDateTime;
import java.util.List;

public class PaymentProductResponseDTO {

    private Long id;
    private String productName;
    private String productCode;
    private Boolean status;
    private List<PaymentModeDTO> allowedModes; // NEW

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // getters/setters

    public PaymentProductResponseDTO() {
    }

    public List<PaymentModeDTO> getAllowedModes() {
        return allowedModes;
    }

    public void setAllowedModes(List<PaymentModeDTO> allowedModes) {
        this.allowedModes = allowedModes;
    }

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

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
