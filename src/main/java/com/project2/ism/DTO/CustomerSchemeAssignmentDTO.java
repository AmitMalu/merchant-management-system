package com.project2.ism.DTO;

import java.time.LocalDate;

public class CustomerSchemeAssignmentDTO {

    private Long id;
    private Long schemeId;
    private String schemeCode;       // Optional: for API readability
    private Long productId;
    private String productName;      // Optional
    private String customerType;     // FRANCHISE / MERCHANT
    private Long franchiseId;
    private Long merchantId;
    private String customerName;     // Optional: franchise/merchant name
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String remarks;
    private Boolean isRateChanged;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }

    public String getSchemeCode() { return schemeCode; }
    public void setSchemeCode(String schemeCode) { this.schemeCode = schemeCode; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public Long getFranchiseId() { return franchiseId; }
    public void setFranchiseId(Long franchiseId) { this.franchiseId = franchiseId; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Boolean getIsRateChanged() { return isRateChanged; }
    public void setIsRateChanged(Boolean isRateChanged) { this.isRateChanged = isRateChanged; }

}
