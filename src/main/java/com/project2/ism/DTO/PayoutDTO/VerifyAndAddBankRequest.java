package com.project2.ism.DTO.PayoutDTO;

public class VerifyAndAddBankRequest {
    private String customerType;
    private String ifscCode;
    private String bankName;
    private String accountNumber;
    private String bankHolderName;
    private String stateName;

    // Getters and setters
    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public String getIfscCode() { return ifscCode; }
    public void setIfscCode(String ifscCode) { this.ifscCode = ifscCode; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getBankHolderName() { return bankHolderName; }
    public void setBankHolderName(String bankHolderName) { this.bankHolderName = bankHolderName; }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }
}
