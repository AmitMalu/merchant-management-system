package com.project2.ism.DTO;

public class InventoryDTO {
    private String productCode;
    private String productName;
    private String vendorName;
    private int totalQuantity;
    private int returned;
    private int available;
    private String status;

    public InventoryDTO(String productCode, String productName, String vendorName,
                        int totalQuantity, int returned, int available) {
        this.productCode = productCode;
        this.productName = productName;
        this.vendorName = vendorName;
        this.totalQuantity = totalQuantity;
        this.returned = returned;
        this.available = available;
        this.status = available > 0 ? "In Stock" : "Out of Stock";
    }

    // getters & setters

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public int getReturned() {
        return returned;
    }

    public void setReturned(int returned) {
        this.returned = returned;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }
}
