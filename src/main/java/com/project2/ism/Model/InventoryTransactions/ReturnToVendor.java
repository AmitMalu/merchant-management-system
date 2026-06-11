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
public class ReturnToVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Vendor is required")
    @ManyToOne
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @NotNull(message = "Return date is required")
    @PastOrPresent(message = "Return date cannot be in the future")
    @Column(nullable = false)
    private LocalDate returnDate;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @NotNull(message = "Returned quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    @Column(nullable = false)
    private Integer quantity;

    @NotBlank(message = "Return reason is required")
    @Column(nullable = false)
    private String reasonForReturn;

    @NotBlank(message = "Condition is required")
    @Column(nullable = false)
    private String productCondition;

    @OneToMany(mappedBy = "returnToVendor", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    private List<ProductSerialNumbers> returnedSerialNumbers = new ArrayList<>();

    @Size(max = 1000)
    private String remark;

    // Helper methods
    public void addReturnedSerialNumber(ProductSerialNumbers psn) {
        returnedSerialNumbers.add(psn);
        psn.setReturnToVendor(this);
    }

    public void removeReturnedSerialNumber(ProductSerialNumbers psn) {
        returnedSerialNumbers.remove(psn);
        psn.setReturnToVendor(null);
    }

    // Getters & Setters
    public ReturnToVendor() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }

    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getReasonForReturn() { return reasonForReturn; }
    public void setReasonForReturn(String reasonForReturn) { this.reasonForReturn = reasonForReturn; }

    public String getProductCondition() { return productCondition; }
    public void setProductCondition(String productCondition) { this.productCondition = productCondition; }

    public List<ProductSerialNumbers> getReturnedSerialNumbers() { return returnedSerialNumbers; }
    public void setReturnedSerialNumbers(List<ProductSerialNumbers> returnedSerialNumbers) { this.returnedSerialNumbers = returnedSerialNumbers; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}