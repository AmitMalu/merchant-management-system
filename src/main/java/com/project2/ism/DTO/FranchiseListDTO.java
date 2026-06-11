package com.project2.ism.DTO;


import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FranchiseListDTO {
    private Long id;
    private String franchiseName;
    private String contactPersonName;
    private String contactPersonEmail;
    private String contactPersonPhone;
    private String address;
    private Long merchantCount;
    private BigDecimal walletBalance;
    private String status;
    private LocalDateTime createdAt;

    // Constructors
    public FranchiseListDTO() {}

    public FranchiseListDTO(Long id, String franchiseName, String contactPersonName,
                            String contactPersonEmail, String contactPersonPhone,
                            String address, Long merchantCount, BigDecimal walletBalance,
                            String status, LocalDateTime createdAt) {
        this.id = id;
        this.franchiseName = franchiseName;
        this.contactPersonName = contactPersonName;
        this.contactPersonEmail = contactPersonEmail;
        this.contactPersonPhone = contactPersonPhone;
        this.address = address;
        this.merchantCount = merchantCount;
        this.walletBalance = walletBalance;
        this.status = status;
        this.createdAt = createdAt;
    }

    public FranchiseListDTO(Long id,String franchiseName,String contactPersonEmail,BigDecimal walletBalance){
        this.id = id;
        this.franchiseName = franchiseName;
        this.contactPersonEmail = contactPersonEmail;
        this.walletBalance = walletBalance;
    }
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFranchiseName() { return franchiseName; }
    public void setFranchiseName(String franchiseName) { this.franchiseName = franchiseName; }

    public String getContactPersonName() { return contactPersonName; }
    public void setContactPersonName(String contactPersonName) { this.contactPersonName = contactPersonName; }

    public String getContactPersonEmail() { return contactPersonEmail; }
    public void setContactPersonEmail(String contactPersonEmail) { this.contactPersonEmail = contactPersonEmail; }

    public String getContactPersonPhone() { return contactPersonPhone; }
    public void setContactPersonPhone(String contactPersonPhone) { this.contactPersonPhone = contactPersonPhone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Long getMerchantCount() { return merchantCount; }
    public void setMerchantCount(Long merchantCount) { this.merchantCount = merchantCount; }

    public BigDecimal getWalletBalance() { return walletBalance; }
    public void setWalletBalance(BigDecimal walletBalance) { this.walletBalance = walletBalance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}