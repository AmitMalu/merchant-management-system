package com.project2.ism.DTO;

import com.project2.ism.Model.InventoryTransactions.ProductDistribution;

import java.time.LocalDateTime;
import java.util.List;

public class ProductDistributionDTO {
    private Long id;
    private Long quantity;
    private String distributedBy;
    private LocalDateTime distributedDate;
    private LocalDateTime receivedDate;

    private Long franchiseId;
    private String franchiseName;

    private Long merchantId;
    private String merchantName;

    private List<ProductSerialDTO> serialNumbers;
    // getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public String getDistributedBy() {
        return distributedBy;
    }

    public void setDistributedBy(String distributedBy) {
        this.distributedBy = distributedBy;
    }

    public LocalDateTime getDistributedDate() {
        return distributedDate;
    }

    public void setDistributedDate(LocalDateTime distributedDate) {
        this.distributedDate = distributedDate;
    }

    public LocalDateTime getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDateTime receivedDate) {
        this.receivedDate = receivedDate;
    }

    public Long getFranchiseId() {
        return franchiseId;
    }

    public void setFranchiseId(Long franchiseId) {
        this.franchiseId = franchiseId;
    }

    public String getFranchiseName() {
        return franchiseName;
    }

    public void setFranchiseName(String franchiseName) {
        this.franchiseName = franchiseName;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public List<ProductSerialDTO> getSerialNumbers() {
        return serialNumbers;
    }

    public void setSerialNumbers(List<ProductSerialDTO> serialNumbers) {
        this.serialNumbers = serialNumbers;
    }
}
