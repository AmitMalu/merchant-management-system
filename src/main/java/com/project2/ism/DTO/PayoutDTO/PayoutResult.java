package com.project2.ism.DTO.PayoutDTO;


import java.math.BigDecimal;

/**
 * Result DTO for payout operations
 */
public class PayoutResult {

    private String status; // "OK", "FAILED", "PENDING"
    private String message;
    private String txnId; // vendor transaction ID
    private String merchantRefId;
    private BigDecimal amount;
    private BigDecimal charges;
    private BigDecimal vendorCharges;
    private BigDecimal totalDeducted; // amount + charges
    private BigDecimal remainingBalance;
    private String rrn; // only for successful transactions

    public static PayoutResult ok(String txnId, String merchantRefId, BigDecimal amount,
                                  BigDecimal charges, BigDecimal vendorCharges,BigDecimal remainingBalance) {
        PayoutResult result = new PayoutResult();
        result.setStatus("OK");
        result.setMessage("Payout initiated successfully");
        result.setTxnId(txnId);
        result.setMerchantRefId(merchantRefId);
        result.setAmount(amount);
        result.setCharges(charges);
        result.setVendorCharges(vendorCharges);
        result.setTotalDeducted(amount.add(charges));
        result.setRemainingBalance(remainingBalance);
        return result;
    }

    public static PayoutResult pending(String txnId, String merchantRefId, BigDecimal amount,
                                       BigDecimal charges,BigDecimal vendorCharges, BigDecimal remainingBalance) {
        PayoutResult result = new PayoutResult();
        result.setStatus("PENDING");
        result.setMessage("Payout is in progress");
        result.setTxnId(txnId);
        result.setMerchantRefId(merchantRefId);
        result.setAmount(amount);
        result.setCharges(charges);
        result.setVendorCharges(vendorCharges);
        result.setTotalDeducted(amount.add(charges));
        result.setRemainingBalance(remainingBalance);
        return result;
    }

    public static PayoutResult failed(String merchantRefId, String message) {
        PayoutResult result = new PayoutResult();
        result.setStatus("FAILED");
        result.setMessage(message);
        result.setMerchantRefId(merchantRefId);
        return result;
    }

    // Getters and setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getMerchantRefId() { return merchantRefId; }
    public void setMerchantRefId(String merchantRefId) { this.merchantRefId = merchantRefId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getCharges() { return charges; }
    public void setCharges(BigDecimal charges) { this.charges = charges; }

    public BigDecimal getVendorCharges() {
        return vendorCharges;
    }

    public void setVendorCharges(BigDecimal vendorCharges) {
        this.vendorCharges = vendorCharges;
    }

    public BigDecimal getTotalDeducted() { return totalDeducted; }
    public void setTotalDeducted(BigDecimal totalDeducted) { this.totalDeducted = totalDeducted; }

    public BigDecimal getRemainingBalance() { return remainingBalance; }
    public void setRemainingBalance(BigDecimal remainingBalance) {
        this.remainingBalance = remainingBalance;
    }

    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }
}