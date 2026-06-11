package com.project2.ism.DTO.PaymentDTO;


import java.util.List;

public class PaymentVendorRoutingRequest {

    private Long payoutProductId;

    private Long vendor1Id;
    private Long vendor2Id;
    private Long vendor3Id;

    private List<PaymentVendorRuleRequest> vendorRules;

    public PaymentVendorRoutingRequest() {
    }

    public Long getPayoutProductId() {
        return payoutProductId;
    }

    public void setPayoutProductId(Long payoutProductId) {
        this.payoutProductId = payoutProductId;
    }

    public Long getVendor1Id() {
        return vendor1Id;
    }

    public void setVendor1Id(Long vendor1Id) {
        this.vendor1Id = vendor1Id;
    }

    public Long getVendor2Id() {
        return vendor2Id;
    }

    public void setVendor2Id(Long vendor2Id) {
        this.vendor2Id = vendor2Id;
    }

    public Long getVendor3Id() {
        return vendor3Id;
    }

    public void setVendor3Id(Long vendor3Id) {
        this.vendor3Id = vendor3Id;
    }

    public List<PaymentVendorRuleRequest> getVendorRules() {
        return vendorRules;
    }

    public void setVendorRules(List<PaymentVendorRuleRequest> vendorRules) {
        this.vendorRules = vendorRules;
    }
}

