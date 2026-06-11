package com.project2.ism.DTO.TempDTOs;

import java.math.BigDecimal;

public class SettlementResultDTO {
    private String vendorTxId;
    private String status;   // OK, ALREADY_SETTLED, FAILED
    private String message;
    private BigDecimal amount;
    private BigDecimal fee;
    private BigDecimal net;

    private BigDecimal franchiseCommission;

    // ---- factory methods ----
    public static SettlementResultDTO ok(String vendorTxId, BigDecimal amount, BigDecimal fee, BigDecimal net, BigDecimal after) {
        SettlementResultDTO dto = new SettlementResultDTO();
        dto.vendorTxId = vendorTxId;
        dto.status = "OK";
        dto.message = "Settled successfully. Balance after: " + after;
        dto.amount = amount;
        dto.fee = fee;
        dto.net = net;
        return dto;
    }
    public static SettlementResultDTO ok(String vendorTxId, BigDecimal amount, BigDecimal fee, BigDecimal net, BigDecimal after,BigDecimal franchiseCommission) {
        SettlementResultDTO dto = new SettlementResultDTO();
        dto.vendorTxId = vendorTxId;
        dto.status = "OK";
        dto.message = "Settled successfully. Balance after: " + after;
        dto.amount = amount;
        dto.fee = fee;
        dto.net = net;
        dto.franchiseCommission = franchiseCommission;
        return dto;
    }

    public static SettlementResultDTO alreadySettled(String vendorTxId) {
        SettlementResultDTO dto = new SettlementResultDTO();
        dto.vendorTxId = vendorTxId;
        dto.status = "ALREADY_SETTLED";
        dto.message = "This transaction was already settled.";
        return dto;
    }

    public static SettlementResultDTO failed(String vendorTxId, String error) {
        SettlementResultDTO dto = new SettlementResultDTO();
        dto.vendorTxId = vendorTxId;
        dto.status = "FAILED";
        dto.message = error;
        return dto;
    }

    // ---- getters/setters ----
    public String getVendorTxId() { return vendorTxId; }
    public void setVendorTxId(String vendorTxId) { this.vendorTxId = vendorTxId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }

    public BigDecimal getNet() { return net; }
    public void setNet(BigDecimal net) { this.net = net; }

    public BigDecimal getFranchiseCommission() {
        return franchiseCommission;
    }

    public void setFranchiseCommission(BigDecimal franchiseCommission) {
        this.franchiseCommission = franchiseCommission;
    }
}
