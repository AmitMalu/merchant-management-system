package com.project2.ism.DTO.PaymentDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VendorPurposeDTO {

    @JsonProperty("description")
    private String purposeName;

    @JsonProperty("code")
    private String purposeCode;
    // getters/setters

    public VendorPurposeDTO() {
    }

    public VendorPurposeDTO(String purposeName, String purposeCode) {
        this.purposeName = purposeName;
        this.purposeCode = purposeCode;
    }

    public String getPurposeName() {
        return purposeName;
    }

    public void setPurposeName(String purposeName) {
        this.purposeName = purposeName;
    }

    public String getPurposeCode() {
        return purposeCode;
    }

    public void setPurposeCode(String purposeCode) {
        this.purposeCode = purposeCode;
    }
}
