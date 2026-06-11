package com.project2.ism.Model.InventoryTransactions;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class ReturnTransactions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String returnNumber;

    private String originalDeliveryNumber;

    @ManyToOne
    @JoinColumn(name = "franchise_id")
    private Franchise franchise;

    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private LocalDate returnDate;

    private String receivedBy;

    private Integer originalQuantity;

    private Integer returnedQuantity;

    @OneToMany(mappedBy = "returnTransaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    private List<ProductSerialNumbers> productSerialNumbers = new ArrayList<>();

    private String returnReason;

    private String returnCondition;

    private String actionTaken;

    private BigDecimal refundAmount;

    private String approvedBy;

    private Boolean isWarrantyReturn;

    private Boolean isReplacementRequired;

    @Size(max = 1000)
    private String inspectionNotes;

    @Size(max = 1000)
    private String remarks;

    public ReturnTransactions() {
    }

    public void addProductSerialNumbers(ProductSerialNumbers psn) {
        productSerialNumbers.add(psn);
        psn.setReturnTransaction(this);
    }

    // Helper method to remove card rate
    public void removeProductSerialNumbers(ProductSerialNumbers psn) {
        productSerialNumbers.remove(psn);
        psn.setReturnTransaction(null);
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Franchise getFranchise() {
        return franchise;
    }

    public void setFranchise(Franchise franchise) {
        this.franchise = franchise;
    }

    public String getOriginalDeliveryNumber() {
        return originalDeliveryNumber;
    }

    public void setOriginalDeliveryNumber(String originalDeliveryNumber) {
        this.originalDeliveryNumber = originalDeliveryNumber;
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public void setReturnNumber(String returnNumber) {
        this.returnNumber = returnNumber;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public Integer getOriginalQuantity() {
        return originalQuantity;
    }

    public void setOriginalQuantity(Integer originalQuantity) {
        this.originalQuantity = originalQuantity;
    }

    public Integer getReturnedQuantity() {
        return returnedQuantity;
    }

    public void setReturnedQuantity(Integer returnedQuantity) {
        this.returnedQuantity = returnedQuantity;
    }

    public List<ProductSerialNumbers> getProductSerialNumbers() {
        return productSerialNumbers;
    }

    public void setProductSerialNumbers(List<ProductSerialNumbers> productSerialNumbers) {
        this.productSerialNumbers = productSerialNumbers;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public String getReturnCondition() {
        return returnCondition;
    }

    public void setReturnCondition(String returnCondition) {
        this.returnCondition = returnCondition;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public Boolean getWarrantyReturn() {
        return isWarrantyReturn;
    }

    public void setWarrantyReturn(Boolean warrantyReturn) {
        isWarrantyReturn = warrantyReturn;
    }

    public Boolean getReplacementRequired() {
        return isReplacementRequired;
    }

    public void setReplacementRequired(Boolean replacementRequired) {
        isReplacementRequired = replacementRequired;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getInspectionNotes() {
        return inspectionNotes;
    }

    public void setInspectionNotes(String inspectionNotes) {
        this.inspectionNotes = inspectionNotes;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
