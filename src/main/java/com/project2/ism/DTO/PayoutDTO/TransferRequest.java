package com.project2.ism.DTO.PayoutDTO;

public class TransferRequest {
    private Long bankId;
    private Double amount;
    private String transferType;
    private Long customerId;

    // Getters and Setters
    public Long getBankId() { return bankId; }
    public void setBankId(Long bankId) { this.bankId = bankId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
}
