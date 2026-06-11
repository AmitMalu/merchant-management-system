package com.project2.ism.DTO;

import com.project2.ism.Model.InventoryTransactions.ReturnTransactions;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.ProductSerialsRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class ReturnTransactionDTO {

    public Long id;
    public String returnNumber;
    public String originalDeliveryNumber;
    public Long franchiseId;
    public String franchiseName;
    public Long merchantId;
    public String merchantName;
    public Long productId;
    public String productCode;
    public String productName;

    public LocalDate returnDate;
    public String receivedBy;
    public Integer originalQuantity;
    public Integer returnedQuantity;
    public String returnReason;
    public String returnCondition;
    public String actionTaken;
    public BigDecimal refundAmount;
    public String approvedBy;
    public Boolean isWarrantyReturn;
    public Boolean isReplacementRequired;
    public String inspectionNotes;
    public String remarks;

    public List<ProductSerialDTO> serialNumbers;

    // --- Mapper ---
    public static ReturnTransactionDTO fromEntity(ReturnTransactions entity) {
        ReturnTransactionDTO dto = new ReturnTransactionDTO();
        dto.id = entity.getId();
        dto.returnNumber = entity.getReturnNumber();
        dto.originalDeliveryNumber = entity.getOriginalDeliveryNumber();
        dto.franchiseId = entity.getFranchise() != null ? entity.getFranchise().getId() : null;
        dto.franchiseName = entity.getFranchise() != null ? entity.getFranchise().getFranchiseName() : null;
        dto.merchantId = entity.getMerchant() != null ? entity.getMerchant().getId() : null;
        dto.merchantName = entity.getMerchant() != null ? entity.getMerchant().getBusinessName() : null;
        dto.productId = entity.getProduct() != null ? entity.getProduct().getId() : null;
        dto.productCode = entity.getProduct() != null ? entity.getProduct().getProductCode() : null;
        dto.productName = entity.getProduct() != null ? entity.getProduct().getProductName() : null;

        dto.returnDate = entity.getReturnDate();
        dto.receivedBy = entity.getReceivedBy();
        dto.originalQuantity = entity.getOriginalQuantity();
        dto.returnedQuantity = entity.getReturnedQuantity();
        dto.returnReason = entity.getReturnReason();
        dto.returnCondition = entity.getReturnCondition();
        dto.actionTaken = entity.getActionTaken();
        dto.refundAmount = entity.getRefundAmount();
        dto.approvedBy = entity.getApprovedBy();
        dto.isWarrantyReturn = entity.getWarrantyReturn();
        dto.isReplacementRequired = entity.getReplacementRequired();
        dto.inspectionNotes = entity.getInspectionNotes();
        dto.remarks = entity.getRemarks();

        if (entity.getProductSerialNumbers() != null) {
            dto.serialNumbers = entity.getProductSerialNumbers().stream()
                    .map(ProductSerialDTO::fromEntity)
                    .collect(Collectors.toList());
        }
        return dto;
    }

    public ReturnTransactions toEntity(Franchise franchise, Merchant merchant, Product product, ProductSerialsRepository serialRepo) {
        ReturnTransactions ret = new ReturnTransactions();
        ret.setId(this.id);
        ret.setReturnNumber(this.returnNumber);
        ret.setOriginalDeliveryNumber(this.originalDeliveryNumber);
        ret.setFranchise(franchise);
        ret.setMerchant(merchant);
        ret.setProduct(product);
        ret.setReturnDate(this.returnDate);
        ret.setReceivedBy(this.receivedBy);
        ret.setOriginalQuantity(this.originalQuantity);
        ret.setReturnedQuantity(this.returnedQuantity);
        ret.setReturnReason(this.returnReason);
        ret.setReturnCondition(this.returnCondition);
        ret.setActionTaken(this.actionTaken);
        ret.setRefundAmount(this.refundAmount);
        ret.setApprovedBy(this.approvedBy);
        ret.setWarrantyReturn(this.isWarrantyReturn);
        ret.setReplacementRequired(this.isReplacementRequired);
        ret.setInspectionNotes(this.inspectionNotes);
        ret.setRemarks(this.remarks);

        if (this.serialNumbers != null) {
            ret.setProductSerialNumbers(
                    this.serialNumbers.stream()
                            .map(sn -> sn.toReturnEntity(ret, serialRepo)) // 👈 custom mapping for return
                            .collect(Collectors.toList())
            );
        }
        return ret;
    }
}
