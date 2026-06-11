package com.project2.ism.DTO.PaymentDTO;
import com.project2.ism.DTO.Vendor.VendorIDNameDTO;

import java.time.LocalDateTime;
import java.util.List;

public class PaymentVendorRoutingDTO {

    private Long id;

    private Long payoutProductId;
    private String payoutProductName;

    private VendorIDNameDTO vendor1;
    private VendorIDNameDTO vendor2;
    private VendorIDNameDTO vendor3;

    private List<PaymentVendorRuleDTO> vendorRules;

    private Boolean status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    public PaymentVendorRoutingDTO() {}

    public PaymentVendorRoutingDTO(Long id, Long payoutProductId, String payoutProductName, VendorIDNameDTO vendor1, VendorIDNameDTO vendor2, VendorIDNameDTO vendor3, List<PaymentVendorRuleDTO> vendorRules, Boolean status, LocalDateTime updatedAt, LocalDateTime createdAt) {
        this.id = id;
        this.payoutProductId = payoutProductId;
        this.payoutProductName = payoutProductName;
        this.vendor1 = vendor1;
        this.vendor2 = vendor2;
        this.vendor3 = vendor3;
        this.vendorRules = vendorRules;
        this.status = status;
        this.updatedAt = updatedAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPayoutProductId() {
        return payoutProductId;
    }

    public void setPayoutProductId(Long payoutProductId) {
        this.payoutProductId = payoutProductId;
    }

    public String getPayoutProductName() {
        return payoutProductName;
    }

    public void setPayoutProductName(String payoutProductName) {
        this.payoutProductName = payoutProductName;
    }

    public VendorIDNameDTO getVendor1() {
        return vendor1;
    }

    public void setVendor1(VendorIDNameDTO vendor1) {
        this.vendor1 = vendor1;
    }

    public VendorIDNameDTO getVendor2() {
        return vendor2;
    }

    public void setVendor2(VendorIDNameDTO vendor2) {
        this.vendor2 = vendor2;
    }

    public VendorIDNameDTO getVendor3() {
        return vendor3;
    }

    public void setVendor3(VendorIDNameDTO vendor3) {
        this.vendor3 = vendor3;
    }

    public List<PaymentVendorRuleDTO> getVendorRules() {
        return vendorRules;
    }

    public void setVendorRules(List<PaymentVendorRuleDTO> vendorRules) {
        this.vendorRules = vendorRules;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
