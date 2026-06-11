package com.project2.ism.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MerchantListDTO {
    private Long id;
    private String businessName;
    private String businessType;
    private String contactPersonName;
    private String contactPersonEmail;
    private String contactPersonPhone;
    private String address;
    private Long franchiseId;
    private String franchiseName;
    private Integer products;
    private BigDecimal walletBalance;
    private BigDecimal monthlyRevenue;
    private String status;
    private LocalDateTime createdAt;

    // Constructors
    public MerchantListDTO() {}

    public MerchantListDTO(Long id, String businessName, String businessType,
                           String contactPersonName, String contactPersonEmail,
                           String contactPersonPhone, String address, Long franchiseId,
                           String franchiseName, Integer products, BigDecimal walletBalance,
                           BigDecimal monthlyRevenue, String status, LocalDateTime createdAt) {
        this.id = id;
        this.businessName = businessName;
        this.businessType = businessType;
        this.contactPersonName = contactPersonName;
        this.contactPersonEmail = contactPersonEmail;
        this.contactPersonPhone = contactPersonPhone;
        this.address = address;
        this.franchiseId = franchiseId;
        this.franchiseName = franchiseName;
        this.products = products;
        this.walletBalance = walletBalance;
        this.monthlyRevenue = monthlyRevenue;
        this.status = status;
        this.createdAt = createdAt;
    }

    public MerchantListDTO(Long id,String businessName,String contactPersonEmail,BigDecimal walletBalance,Long franchiseId){
        this.id = id;
        this.businessName = businessName;
        this.contactPersonEmail = contactPersonEmail;
        this.walletBalance = walletBalance;
        this.franchiseId = franchiseId;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }

    public String getContactPersonName() { return contactPersonName; }
    public void setContactPersonName(String contactPersonName) { this.contactPersonName = contactPersonName; }

    public String getContactPersonEmail() { return contactPersonEmail; }
    public void setContactPersonEmail(String contactPersonEmail) { this.contactPersonEmail = contactPersonEmail; }

    public String getContactPersonPhone() { return contactPersonPhone; }
    public void setContactPersonPhone(String contactPersonPhone) { this.contactPersonPhone = contactPersonPhone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Long getFranchiseId() { return franchiseId; }
    public void setFranchiseId(Long franchiseId) { this.franchiseId = franchiseId; }

    public String getFranchiseName() { return franchiseName; }
    public void setFranchiseName(String franchiseName) { this.franchiseName = franchiseName; }

    public Integer getProducts() { return products; }
    public void setProducts(Integer products) { this.products = products; }

    public BigDecimal getWalletBalance() { return walletBalance; }
    public void setWalletBalance(BigDecimal walletBalance) { this.walletBalance = walletBalance; }

    public BigDecimal getMonthlyRevenue() { return monthlyRevenue; }
    public void setMonthlyRevenue(BigDecimal monthlyRevenue) { this.monthlyRevenue = monthlyRevenue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}