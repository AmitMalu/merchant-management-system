package com.project2.ism.DTO.PaymentDTO;

import com.project2.ism.Model.Payment.PaymentMode;

import java.time.LocalDateTime;
import java.util.Set;

public class PaymentVendorResponseDTO {

    private Long id;
    private String vendorName;
    private Set<PaymentMode> supportedModes;
    private Boolean status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // getters/setters

    public PaymentVendorResponseDTO() {
    }

    public PaymentVendorResponseDTO(Long id, String vendorName, Set<PaymentMode> supportedModes, Boolean status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.vendorName = vendorName;
        this.supportedModes = supportedModes;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public Set<PaymentMode> getSupportedModes() {
        return supportedModes;
    }

    public void setSupportedModes(Set<PaymentMode> supportedModes) {
        this.supportedModes = supportedModes;
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
