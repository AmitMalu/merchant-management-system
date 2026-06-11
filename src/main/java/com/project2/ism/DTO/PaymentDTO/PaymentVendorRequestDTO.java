package com.project2.ism.DTO.PaymentDTO;


import com.project2.ism.Model.Payment.PaymentMode;

import java.util.Set;

public class PaymentVendorRequestDTO {

    private String vendorName;
    private Set<PaymentMode> supportedModes;
    private Boolean status;

    // getters/setters

    public PaymentVendorRequestDTO() {
    }

    public PaymentVendorRequestDTO(String vendorName, Set<PaymentMode> supportedModes, Boolean status) {
        this.vendorName = vendorName;
        this.supportedModes = supportedModes;
        this.status = status;
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
}
