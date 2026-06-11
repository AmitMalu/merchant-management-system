package com.project2.ism.DTO.PaymentDTO;

public class VendorBankResponseDTO {
    private String bankName;
    private String bankCode;

    public VendorBankResponseDTO() {
    }

    public VendorBankResponseDTO(String bankName, String bankCode) {
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
