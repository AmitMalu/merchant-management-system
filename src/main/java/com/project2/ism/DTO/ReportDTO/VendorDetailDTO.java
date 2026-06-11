package com.project2.ism.DTO.ReportDTO;

import java.time.LocalDate;
import java.util.List;

public class VendorDetailDTO {
    private Long vendorId;

    private String vendorName;

    private Long totalProducts;

    private Long totalDevices;

    private LocalDate agreementStartDate;

    private LocalDate agreementEndDate;

    private Integer creditPeriodDays;


    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public Long getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Long totalProducts) {
        this.totalProducts = totalProducts;
    }

    public Long getTotalDevices() {
        return totalDevices;
    }

    public void setTotalDevices(Long totalDevices) {
        this.totalDevices = totalDevices;
    }

    public LocalDate getAgreementStartDate() {
        return agreementStartDate;
    }

    public void setAgreementStartDate(LocalDate agreementStartDate) {
        this.agreementStartDate = agreementStartDate;
    }

    public LocalDate getAgreementEndDate() {
        return agreementEndDate;
    }

    public void setAgreementEndDate(LocalDate agreementEndDate) {
        this.agreementEndDate = agreementEndDate;
    }

    public Integer getCreditPeriodDays() {
        return creditPeriodDays;
    }

    public void setCreditPeriodDays(Integer creditPeriodDays) {
        this.creditPeriodDays = creditPeriodDays;
    }
}
