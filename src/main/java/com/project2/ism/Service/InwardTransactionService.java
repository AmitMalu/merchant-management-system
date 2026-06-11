package com.project2.ism.Service;

import com.project2.ism.DTO.InwardTransactionDTO;
import com.project2.ism.DTO.ProductSerialDTO;
import com.project2.ism.Exception.DuplicateResourceException;
import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.InventoryTransactions.InwardTransactions;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.ProductCategory;
import com.project2.ism.Model.Vendor.Vendor;
import com.project2.ism.Repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class InwardTransactionService {

    private final InwardTransactionRepository inwardRepo;
    private final ProductSerialsRepository serialRepo;

    private final VendorRepository vendorRepo;

    private final ProductRepository productRepo;



    public InwardTransactionService(InwardTransactionRepository inwardRepo, ProductSerialsRepository serialRepo, VendorRepository vendorRepo, ProductRepository productRepo) {
        this.inwardRepo = inwardRepo;
        this.serialRepo = serialRepo;
        this.vendorRepo = vendorRepo;
        this.productRepo = productRepo;
    }

    @Transactional
    public InwardTransactionDTO createTransaction(InwardTransactionDTO dto) {
        Vendor vendor = vendorRepo.findById(dto.vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));
        Product product = productRepo.findById(dto.productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        InwardTransactions inward = dto.toEntity(vendor, product);

        // Validate serials before save
        validateAndAttachSerials(inward);

        InwardTransactions saved = inwardRepo.save(inward);
        return InwardTransactionDTO.fromEntity(saved);
    }

    public List<InwardTransactionDTO> getAllTransactions() {
        return inwardRepo.findAll().stream()
                .map(InwardTransactionDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public InwardTransactionDTO getTransaction(Long id) {
        InwardTransactions entity = inwardRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inward transaction not found with id: " + id));
        return InwardTransactionDTO.fromEntity(entity);
    }

    @Transactional
    public InwardTransactionDTO updateTransaction(Long id, InwardTransactionDTO dto) {
        InwardTransactions existing = inwardRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inward transaction not found with id: " + id));

        Vendor vendor = vendorRepo.findById(dto.vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));
        Product product = productRepo.findById(dto.productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        // update fields
        existing.setInvoiceNumber(dto.invoiceNumber);
        existing.setVendor(vendor);
        existing.setReceivedDate(dto.receivedDate);
        existing.setReceivedBy(dto.receivedBy);
        existing.setProduct(product);
        existing.setQuantity(dto.quantity);
        existing.setBatchNumber(dto.batchNumber);
        existing.setWarrantyPeriod(dto.warrantyPeriod);
        existing.setProductCondition(dto.productCondition);
        existing.setRemark(dto.remark);

        if (dto.serialNumbers != null) {
            List<ProductSerialNumbers> serials = dto.serialNumbers.stream()
                    .map(sn -> sn.toInwardEntity(existing, product))
                    .collect(Collectors.toList());
            existing.setProductSerialNumbers(serials);
            validateAndAttachSerials(existing);
        }

        InwardTransactions saved = inwardRepo.save(existing);
        return InwardTransactionDTO.fromEntity(saved);
    }

    @Transactional
    public void deleteTransaction(Long id) {
        InwardTransactions inward = inwardRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inward transaction not found with id: " + id));
        inwardRepo.delete(inward);
    }

    private void validateAndAttachSerials(InwardTransactions inward) {
        if (inward.getProductSerialNumbers() == null) return;
        for (ProductSerialNumbers sn : inward.getProductSerialNumbers()) {
            if (sn.getSid() != null && serialRepo.existsBySid(sn.getSid())) {
                throw new DuplicateResourceException("SID already exists: " + sn.getSid());
            }
            if (sn.getMid() != null && serialRepo.existsByMid(sn.getMid())) {
                throw new DuplicateResourceException("MID already exists: " + sn.getMid());
            }
            if (sn.getTid() != null && serialRepo.existsByTid(sn.getTid())) {
                throw new DuplicateResourceException("TID already exists: " + sn.getTid());
            }
            if (sn.getVpaid() != null && serialRepo.existsByVpaid(sn.getVpaid())) {
                throw new DuplicateResourceException("VPAID already exists: " + sn.getVpaid());
            }

            sn.setInwardTransaction(inward);
            sn.setProduct(inward.getProduct());
        }
    }


    public List<ProductSerialDTO> getSerialNumbersByProductId(Long productId) {
        return serialRepo.findByProduct_IdAndMerchantIsNullAndOutwardTransactionIsNull(productId).stream()
                .map(ProductSerialDTO::fromEntity) // ✅ map to ProductSerialDTO
                .collect(Collectors.toList());
    }

}

