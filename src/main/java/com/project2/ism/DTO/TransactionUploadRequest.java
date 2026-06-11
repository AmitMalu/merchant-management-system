package com.project2.ism.DTO;

// dto/TransactionUploadRequest.java

import com.project2.ism.Model.VendorTransactions;
import java.util.List;

public class TransactionUploadRequest {
    private String vendorName;
    private String product;
    private List<VendorTransactions> vendorTransactions;

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public List<VendorTransactions> getTransactions() { return vendorTransactions; }
    public void setTransactions(List<VendorTransactions> vendorTransactions) { this.vendorTransactions = vendorTransactions; }
}
