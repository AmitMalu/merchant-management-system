package com.project2.ism.DTO.PaymentDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VendorBankDTO {

    @JsonProperty("description")
    private String bankName;

    @JsonProperty("code")
    private String bankCode;
    // getters/setters

    public VendorBankDTO() {
    }

    public VendorBankDTO(String bankName, String bankCode) {
        this.bankName = bankName;
        this.bankCode = bankCode;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }
}

