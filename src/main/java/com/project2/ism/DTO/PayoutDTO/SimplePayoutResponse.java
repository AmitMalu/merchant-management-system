package com.project2.ism.DTO.PayoutDTO;

public class SimplePayoutResponse {
    private String status;          // OK / FAILED / PENDING
    private String message;         // user-friendly message
    private String txnId;           // vendor txn id (optional)
    private String merchantRefId;   // useful for tracking later

    public SimplePayoutResponse() {
    }

    public SimplePayoutResponse(String status, String message, String txnId, String merchantRefId) {
        this.status = status;
        this.message = message;
        this.txnId = txnId;
        this.merchantRefId = merchantRefId;
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

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getMerchantRefId() {
        return merchantRefId;
    }

    public void setMerchantRefId(String merchantRefId) {
        this.merchantRefId = merchantRefId;
    }
}
