package com.project2.ism.request;


public class BillerDetailsRequest {

    private String billerCode;
    private Long vendorId;

    public String getBillerCode() {
        return billerCode;
    }

    public void setBillerCode(String billerCode) {
        this.billerCode = billerCode;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public BillerDetailsRequest() {
    }

    public BillerDetailsRequest(String billerCode, Long vendorId) {
        this.billerCode = billerCode;
        this.vendorId = vendorId;
    }
}
