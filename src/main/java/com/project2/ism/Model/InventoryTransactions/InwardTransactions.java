package com.project2.ism.Model.InventoryTransactions;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Vendor.Vendor;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class InwardTransactions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Invoice number required")
    private String invoiceNumber;

    @NotNull(message = "Vendor is required")
    @ManyToOne
    @JoinColumn(name = "vendor_id",nullable = false)
    private Vendor vendor;

    @NotNull(message = "Received date required")
    @PastOrPresent(message = "Received date cannot be in the future")
    @Column(nullable = false)
    private LocalDate receivedDate;

    @NotBlank
    @Size(max = 100, message = "Receiver name must not exceed 100 characters")
    @Column(nullable = false, length = 100)
    private String receivedBy;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @NotNull(message = "Product Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    @Column(nullable = false)
    private Integer quantity;

    private String batchNumber;

    @PositiveOrZero(message = "Warranty period cannot be negative")
    private Integer warrantyPeriod;

    @NotBlank(message = "Condition is required")
    @Column(nullable = false)
    private String productCondition;

    @OneToMany(mappedBy = "inwardTransaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    private List<ProductSerialNumbers> productSerialNumbers = new ArrayList<>();

    @Size(max = 1000)
    private String remark;

    // Helper method
    public void addProductSerialNumbers(ProductSerialNumbers psn) {
        productSerialNumbers.add(psn);
        psn.setInwardTransaction(this);
    }

    // Helper method
    public void removeProductSerialNumbers(ProductSerialNumbers psn) {
        productSerialNumbers.remove(psn);
        psn.setInwardTransaction(null);
    }

    public InwardTransactions() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDate receivedDate) {
        this.receivedDate = receivedDate;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public Integer getWarrantyPeriod() {
        return warrantyPeriod;
    }

    public void setWarrantyPeriod(Integer warrantyPeriod) {
        this.warrantyPeriod = warrantyPeriod;
    }

    public String getProductCondition() {
        return productCondition;
    }

    public void setProductCondition(String productCondition) {
        this.productCondition = productCondition;
    }

    public List<ProductSerialNumbers> getSerialNumbers() {
        return productSerialNumbers;
    }

    public void setSerialNumbers(List<ProductSerialNumbers> productSerialNumbers) {
        this.productSerialNumbers = productSerialNumbers;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }


    public List<ProductSerialNumbers> getProductSerialNumbers() {
        return productSerialNumbers;
    }

    public void setProductSerialNumbers(List<ProductSerialNumbers> productSerialNumbers) {
        this.productSerialNumbers = productSerialNumbers;
    }
}
