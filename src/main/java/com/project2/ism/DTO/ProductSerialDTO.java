package com.project2.ism.DTO;

import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.InventoryTransactions.InwardTransactions;
import com.project2.ism.Model.InventoryTransactions.OutwardTransactions;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.InventoryTransactions.ReturnTransactions;
import com.project2.ism.Model.Product;
import com.project2.ism.Repository.ProductSerialsRepository;

public class ProductSerialDTO {

    public Long id;
    public String sid;
    public String mid;
    public String tid;
    public String vpaid;
    public String mobNumber;

    // ✅ Mapping from entity → DTO
    public static ProductSerialDTO fromEntity(ProductSerialNumbers entity) {
        ProductSerialDTO dto = new ProductSerialDTO();
        dto.id = entity.getId();
        dto.sid = entity.getSid();
        dto.mid = entity.getMid();
        dto.tid = entity.getTid();
        dto.vpaid = entity.getVpaid();
        dto.mobNumber = entity.getMobNumber();
        return dto;
    }

    // ✅ Mapping from DTO → Entity (for InwardTransactions)
    public ProductSerialNumbers toInwardEntity(InwardTransactions inward, Product product) {
        ProductSerialNumbers sn = new ProductSerialNumbers();
        sn.setId(this.id);
        sn.setSid(this.sid);
        sn.setMid(this.mid);
        sn.setTid(this.tid);
        sn.setVpaid(this.vpaid);
        sn.setMobNumber(this.mobNumber);
        sn.setInwardTransaction(inward);
        sn.setProduct(product);
        return sn;
    }

    // ✅ Mapping from DTO → Entity (for OutwardTransactions)
    public ProductSerialNumbers toOutwardEntity(OutwardTransactions outward, ProductSerialsRepository serialRepo) {
        ProductSerialNumbers existingSerial = serialRepo.findById(this.id)
                .orElseThrow(() -> new ResourceNotFoundException("Serial number not found with id: " + this.id));

        // Validate it's not already assigned to another outward transaction
        if (existingSerial.getOutwardTransaction() != null) {
            throw new IllegalStateException("Serial number already assigned: " + existingSerial.getSid());
        }

        // Update only the outward transaction reference
        existingSerial.setOutwardTransaction(outward);
        existingSerial.setFranchise(outward.getFranchise());
        // Set merchant if this is a merchant transaction
        if (outward.getMerchant() != null) {
            existingSerial.setMerchant(outward.getMerchant());
        }

        // Keep all other fields unchanged (inward transaction, product, etc.)
        return existingSerial;
    }

    // ✅ Mapping from DTO → Entity (for ReturnTransactions)
    public ProductSerialNumbers toReturnEntity(ReturnTransactions ret, ProductSerialsRepository serialRepo) {
        ProductSerialNumbers existingSerial = serialRepo.findById(this.id)
                .orElseThrow(() -> new ResourceNotFoundException("Serial number not found with id: " + this.id));

        // Safety check: must already be part of an outward transaction before returning
        if (existingSerial.getOutwardTransaction() == null) {
            throw new IllegalStateException("Cannot return serial that was never outwarded: " + existingSerial.getSid());
        }

        // Link to return transaction
        existingSerial.setReturnTransaction(ret);

        existingSerial.setFranchise(null);
        // Clear merchant (product is back from field)
        existingSerial.setMerchant(null);

        // Clear outward transaction
        existingSerial.setOutwardTransaction(null);

        // Keep inward + product references unchanged
        return existingSerial;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getVpaid() {
        return vpaid;
    }

    public void setVpaid(String vpaid) {
        this.vpaid = vpaid;
    }

    public String getMobNumber() {
        return mobNumber;
    }

    public void setMobNumber(String mobNumber) {
        this.mobNumber = mobNumber;
    }
}
