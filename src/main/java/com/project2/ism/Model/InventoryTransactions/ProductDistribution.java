package com.project2.ism.Model.InventoryTransactions;

import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
public class ProductDistribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long quantity;

    private String distributedBy;

    private LocalDateTime distributedDate;
    private LocalDateTime receivedDate;

    @ManyToOne
    @JoinColumn(name = "franchise_id")
    private Franchise franchise;
    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @OneToMany(mappedBy = "productDistribution")
    private List<ProductSerialNumbers> productSerialNumbers;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }



    public String getDistributedBy() {
        return distributedBy;
    }

    public void setDistributedBy(String distributedBy) {
        this.distributedBy = distributedBy;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public LocalDateTime getDistributedDate() {
        return distributedDate;
    }

    public void setDistributedDate(LocalDateTime distributedDate) {
        this.distributedDate = distributedDate;
    }

    public LocalDateTime getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDateTime receivedDate) {
        this.receivedDate = receivedDate;
    }

    public Franchise getFranchise() {
        return franchise;
    }

    public void setFranchise(Franchise franchise) {
        this.franchise = franchise;
    }

    public List<ProductSerialNumbers> getProductSerialNumbers() {
        return productSerialNumbers;
    }

    public void setProductSerialNumbers(List<ProductSerialNumbers> productSerialNumbers) {
        this.productSerialNumbers = productSerialNumbers;
    }
}
