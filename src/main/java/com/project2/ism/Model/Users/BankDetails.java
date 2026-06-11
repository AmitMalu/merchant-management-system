package com.project2.ism.Model.Users;


import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Embeddable
public class BankDetails {

    @NotBlank(message = "Bank name is required")
    @Size(max = 100, message = "Bank name must not exceed 100 characters")
    private String bankName;

    @NotBlank(message = "Account holder name is required")
    @Size(max = 100, message = "Account holder name must not exceed 100 characters")
    private String accountHolderName;

    @NotBlank(message = "Account number is required")
    @Pattern(
            regexp = "\\d{9,18}", // 9 to 18 digits
            message = "Account number must be between 9 and 18 digits"
    )
    private String accountNumber;

    @NotBlank(message = "IFSC code is required")
    @Pattern(
            regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", // RBI IFSC pattern
            message = "Invalid IFSC code format (e.g., SBIN0001234)"
    )
    private String ifsc;

    @NotBlank(message = "Branch name is required")
    @Size(max = 100, message = "Branch name must not exceed 100 characters")
    private String branchName;

    @NotBlank(message = "Account type is required")
    @Pattern(
            regexp = "(?i)SAVINGS|CURRENT|SALARY|NRI|OD|CC",
            message = "Account type must be one of: SAVINGS, CURRENT, SALARY, NRI, OD, CC"
    )
    private String accountType;

    @Column(name = "bank_verified")
    private Boolean bankVerified = false;

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getIfsc() {
        return ifsc;
    }

    public void setIfsc(String ifsc) {
        this.ifsc = ifsc;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public Boolean getBankVerified() { return bankVerified; }

    public void setBankVerified(Boolean bankVerified) { this.bankVerified = bankVerified; }
}
