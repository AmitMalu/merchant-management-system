package com.project2.ism.request;


import java.time.LocalDateTime;

public class ManualPayoutUpdateResult {

    private Long transactionId;

    private String merchantRefId;

    private String previousStatus;

    private String requestedStatus;

    private String finalStatus;

    /*
     * This means whether this particular request was processed
     * successfully, not whether the payout status is SUCCESS.
     */
    private boolean processed;

    private boolean walletRefunded;

    private String message;

    private LocalDateTime processedAt;

    public static ManualPayoutUpdateResult failed(
            Long transactionId,
            String requestedStatus,
            String message) {

        ManualPayoutUpdateResult result = new ManualPayoutUpdateResult();

        result.setTransactionId(transactionId);
        result.setRequestedStatus(requestedStatus);
        result.setProcessed(false);
        result.setWalletRefunded(false);
        result.setMessage(message);
        result.setProcessedAt(LocalDateTime.now());

        return result;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String getMerchantRefId() {
        return merchantRefId;
    }

    public void setMerchantRefId(String merchantRefId) {
        this.merchantRefId = merchantRefId;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getRequestedStatus() {
        return requestedStatus;
    }

    public void setRequestedStatus(String requestedStatus) {
        this.requestedStatus = requestedStatus;
    }

    public String getFinalStatus() {
        return finalStatus;
    }

    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public boolean isWalletRefunded() {
        return walletRefunded;
    }

    public void setWalletRefunded(boolean walletRefunded) {
        this.walletRefunded = walletRefunded;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }


}
