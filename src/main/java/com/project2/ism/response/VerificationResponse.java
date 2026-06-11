package com.project2.ism.response;

public class VerificationResponse {

    private String status;
    private String message;
    private String accountHolderName;

    public VerificationResponse() {
    }

    public VerificationResponse(
            String status,
            String message,
            String accountHolderName) {

        this.status = status;
        this.message = message;
        this.accountHolderName = accountHolderName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }
}
