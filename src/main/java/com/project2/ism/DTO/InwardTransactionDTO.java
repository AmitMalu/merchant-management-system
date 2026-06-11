package com.project2.ism.DTO;

import com.project2.ism.Model.InventoryTransactions.InwardTransactions;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Vendor.Vendor;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class InwardTransactionDTO {

    public Long id;
    public String invoiceNumber;
    public Long vendorId;
    public String vendorName;
    public LocalDate receivedDate;
    public String receivedBy;
    public Long productId;

    public String productCode;
    public String productName;

    public Integer quantity;
    public String batchNumber;
    public Integer warrantyPeriod;
    public String productCondition;
    public List<ProductSerialDTO> serialNumbers;
    public String remark;

    // --- Mapper Methods ---
    public static InwardTransactionDTO fromEntity(InwardTransactions entity) {
        InwardTransactionDTO dto = new InwardTransactionDTO();
        dto.id = entity.getId();
        dto.invoiceNumber = entity.getInvoiceNumber();
        dto.vendorId = entity.getVendor() != null ? entity.getVendor().getId() : null;
        dto.vendorName = entity.getVendor() != null ? entity.getVendor().getName() : null;
        dto.receivedDate = entity.getReceivedDate();
        dto.receivedBy = entity.getReceivedBy();
        dto.productId = entity.getProduct() != null ? entity.getProduct().getId() : null;
        dto.productCode = entity.getProduct() != null ? entity.getProduct().getProductCode() : null;
        dto.productName = entity.getProduct() != null ? entity.getProduct().getProductName() : null;
        dto.quantity = entity.getQuantity();
        dto.batchNumber = entity.getBatchNumber();
        dto.warrantyPeriod = entity.getWarrantyPeriod();
        dto.productCondition = entity.getProductCondition();
        dto.remark = entity.getRemark();

        if (entity.getProductSerialNumbers() != null) {
            dto.serialNumbers = entity.getProductSerialNumbers().stream()
                    .map(ProductSerialDTO::fromEntity)
                    .collect(Collectors.toList());
        }
        return dto;
    }

    public InwardTransactions toEntity(Vendor vendor, Product product) {
        InwardTransactions inward = new InwardTransactions();
        inward.setId(this.id);
        inward.setInvoiceNumber(this.invoiceNumber);
        inward.setVendor(vendor);
        inward.setReceivedDate(this.receivedDate);
        inward.setReceivedBy(this.receivedBy);
        inward.setProduct(product);
        inward.setQuantity(this.quantity);
        inward.setBatchNumber(this.batchNumber);
        inward.setWarrantyPeriod(this.warrantyPeriod);
        inward.setProductCondition(this.productCondition);
        inward.setRemark(this.remark);

        // ✅ Validation: serialNumbers should not be null/empty
        if (this.serialNumbers == null || this.serialNumbers.isEmpty()) {
            throw new IllegalArgumentException("Serial numbers are required for inward transactions");
        }

        // ✅ Validation: length must match quantity
        if (this.quantity != null && this.serialNumbers.size() != this.quantity) {
            throw new IllegalArgumentException(
                    String.format("Quantity (%d) does not match number of serial numbers provided (%d)",
                            this.quantity, this.serialNumbers.size())
            );
        }

        inward.setProductSerialNumbers(
                this.serialNumbers.stream()
                        .map(sn -> sn.toInwardEntity(inward, product))
                        .collect(Collectors.toList())
        );
        return inward;
    }
}
