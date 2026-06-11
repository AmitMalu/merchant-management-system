package com.project2.ism.DTO.ReportDTO;

import java.util.List;

public class VendorReportsDTO {
    private List<VendorDetailDTO> vendors;

    public List<VendorDetailDTO> getVendors() {
        return vendors;
    }

    public void setVendors(List<VendorDetailDTO> vendors) {
        this.vendors = vendors;
    }
}

