package com.project2.ism.DTO;

import com.project2.ism.Model.InventoryTransactions.OutwardTransactions;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.ProductSerialsRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class OutwardTransactionDTO {

    public Long id;
    public String deliveryNumber;
    public Long franchiseId;
    public String franchiseName;
    public Long merchantId;
    public String merchantName;
    public Long productId;
    public String productCode;
    public String productName;

    public LocalDate dispatchDate;
    public String dispatchedBy;
    public Integer quantity;
    public String deliveryAddress;
    public String contactPerson;
    public String contactPersonNumber;
    public String deliveryMethod;
    public String trackingNumber;
    public LocalDate expectedDelivery;
    public List<ProductSerialDTO> serialNumbers;
    public String remarks;

    // --- Mapper ---
    public static OutwardTransactionDTO fromEntity(OutwardTransactions entity) {
        OutwardTransactionDTO dto = new OutwardTransactionDTO();
        dto.id = entity.getId();
        dto.deliveryNumber = entity.getDeliveryNumber();
        dto.franchiseId = entity.getFranchise() != null ? entity.getFranchise().getId() : null;
        dto.franchiseName = entity.getFranchise() != null ? entity.getFranchise().getFranchiseName() : null;
        dto.merchantId = entity.getMerchant() != null ? entity.getMerchant().getId() : null;
        dto.merchantName = entity.getMerchant() != null ? entity.getMerchant().getBusinessName() : null;
        dto.productId = entity.getProduct() != null ? entity.getProduct().getId() : null;
        dto.productCode = entity.getProduct() != null ? entity.getProduct().getProductCode() : null;
        dto.productName = entity.getProduct() != null ? entity.getProduct().getProductName() : null;
        dto.dispatchDate = entity.getDispatchDate();
        dto.dispatchedBy = entity.getDispatchedBy();
        dto.quantity = entity.getQuantity();
        dto.deliveryAddress = entity.getDeliveryAddress();
        dto.contactPerson = entity.getContactPerson();
        dto.contactPersonNumber = entity.getContactPersonNumber();
        dto.deliveryMethod = entity.getDeliveryMethod();
        dto.trackingNumber = entity.getTrackingNumber();
        dto.expectedDelivery = entity.getExpectedDeliveryDate();
        dto.remarks = entity.getRemarks();

        if (entity.getProductSerialNumbers() != null) {
            dto.serialNumbers = entity.getProductSerialNumbers().stream()
                    .map(ProductSerialDTO::fromEntity)
                    .collect(Collectors.toList());
        }
        return dto;
    }

    public OutwardTransactions toEntity(Franchise franchise, Merchant merchant, Product product, ProductSerialsRepository serialRepo) {
        OutwardTransactions outward = new OutwardTransactions();
        outward.setId(this.id);
        outward.setDeliveryNumber(this.deliveryNumber);
        outward.setFranchise(franchise);
        outward.setMerchant(merchant);
        outward.setProduct(product);
        outward.setDispatchDate(this.dispatchDate);
        outward.setDispatchedBy(this.dispatchedBy);
        outward.setQuantity(this.quantity);
        outward.setDeliveryAddress(this.deliveryAddress);
        outward.setContactPerson(this.contactPerson);
        outward.setContactPersonNumber(this.contactPersonNumber);
        outward.setDeliveryMethod(this.deliveryMethod);
        outward.setTrackingNumber(this.trackingNumber);
        outward.setExpectedDeliveryDate(this.expectedDelivery);
        outward.setRemarks(this.remarks);

        // ✅ UPDATED - Now updates existing serials
        if (this.serialNumbers != null) {
            outward.setProductSerialNumbers(
                    this.serialNumbers.stream()
                            .map(sn -> sn.toOutwardEntity(outward, serialRepo))
                            .collect(Collectors.toList())
            );
        }
        return outward;
    }
}
