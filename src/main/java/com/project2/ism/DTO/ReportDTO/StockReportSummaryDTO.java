package com.project2.ism.DTO.ReportDTO;

import java.util.Map;

public class StockReportSummaryDTO {
    private Long totalStock;
    private Long availableStock;
    private Long allocatedStock;
    private Long returnedStock;

    private Long allocatedToMerchants;
    private Long allocatedToFranchises;

    private Map<String, Long> productWiseCount;
    private Map<String, Long> statusWiseCount;

    public StockReportSummaryDTO() {}

    // Getters and Setters
    public Long getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(Long totalStock) {
        this.totalStock = totalStock;
    }

    public Long getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(Long availableStock) {
        this.availableStock = availableStock;
    }

    public Long getAllocatedStock() {
        return allocatedStock;
    }

    public void setAllocatedStock(Long allocatedStock) {
        this.allocatedStock = allocatedStock;
    }

    public Long getReturnedStock() {
        return returnedStock;
    }

    public void setReturnedStock(Long returnedStock) {
        this.returnedStock = returnedStock;
    }

    public Long getAllocatedToMerchants() {
        return allocatedToMerchants;
    }

    public void setAllocatedToMerchants(Long allocatedToMerchants) {
        this.allocatedToMerchants = allocatedToMerchants;
    }

    public Long getAllocatedToFranchises() {
        return allocatedToFranchises;
    }

    public void setAllocatedToFranchises(Long allocatedToFranchises) {
        this.allocatedToFranchises = allocatedToFranchises;
    }

    public Map<String, Long> getProductWiseCount() {
        return productWiseCount;
    }

    public void setProductWiseCount(Map<String, Long> productWiseCount) {
        this.productWiseCount = productWiseCount;
    }

    public Map<String, Long> getStatusWiseCount() {
        return statusWiseCount;
    }

    public void setStatusWiseCount(Map<String, Long> statusWiseCount) {
        this.statusWiseCount = statusWiseCount;
    }
}