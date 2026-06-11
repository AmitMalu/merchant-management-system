package com.project2.ism.Service;

import com.project2.ism.DTO.ReturnTransactionDTO;
import com.project2.ism.Exception.DuplicateResourceException;
import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.InventoryTransactions.ReturnTransactions;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReturnTransactionService {

    private final ReturnTransactionRepository repository;
    private final FranchiseRepository franchiseRepo;
    private final MerchantRepository merchantRepo;
    private final ProductRepository productRepo;
    private final ProductSerialsRepository serialRepo;

    public ReturnTransactionService(
            ReturnTransactionRepository repository,
            FranchiseRepository franchiseRepo,
            MerchantRepository merchantRepo,
            ProductRepository productRepo,
            ProductSerialsRepository serialRepo
    ) {
        this.repository = repository;
        this.franchiseRepo = franchiseRepo;
        this.merchantRepo = merchantRepo;
        this.productRepo = productRepo;
        this.serialRepo = serialRepo;
    }

    public List<ReturnTransactions> getAll() {
        return repository.findAll();
    }

    public ReturnTransactions getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return Transaction not found with id " + id));
    }

    @Transactional
    public ReturnTransactions createFromDTO(ReturnTransactionDTO dto) {
        if (repository.existsByReturnNumber(dto.returnNumber)) {
            throw new DuplicateResourceException("Return number already exists: " + dto.returnNumber);
        }

        Franchise franchise = dto.franchiseId != null
                ? franchiseRepo.findById(dto.franchiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Franchise not found"))
                : null;

        Merchant merchant = dto.merchantId != null
                ? merchantRepo.findById(dto.merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"))
                : null;

        Product product = dto.productId != null
                ? productRepo.findById(dto.productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"))
                : null;

        ReturnTransactions ret = dto.toEntity(franchise, merchant, product, serialRepo);
        return repository.save(ret);
    }

    @Transactional
    public ReturnTransactions updateFromDTO(Long id, ReturnTransactionDTO dto) {
        ReturnTransactions existing = getById(id);

        Franchise franchise = dto.franchiseId != null
                ? franchiseRepo.findById(dto.franchiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Franchise not found"))
                : null;

        Merchant merchant = dto.merchantId != null
                ? merchantRepo.findById(dto.merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"))
                : null;

        Product product = dto.productId != null
                ? productRepo.findById(dto.productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"))
                : null;

        existing.setReturnNumber(dto.returnNumber);
        existing.setOriginalDeliveryNumber(dto.originalDeliveryNumber);
        existing.setFranchise(franchise);
        existing.setMerchant(merchant);
        existing.setProduct(product);
        existing.setReturnDate(dto.returnDate);
        existing.setReceivedBy(dto.receivedBy);
        existing.setOriginalQuantity(dto.originalQuantity);
        existing.setReturnedQuantity(dto.returnedQuantity);

        if (dto.serialNumbers != null) {
            existing.setProductSerialNumbers(
                    dto.serialNumbers.stream()
                            .map(sn -> sn.toReturnEntity(existing, serialRepo))
                            .collect(Collectors.toList())
            );
        }

        existing.setReturnReason(dto.returnReason);
        existing.setReturnCondition(dto.returnCondition);
        existing.setActionTaken(dto.actionTaken);
        existing.setRefundAmount(dto.refundAmount);
        existing.setApprovedBy(dto.approvedBy);
        existing.setWarrantyReturn(dto.isWarrantyReturn);
        existing.setReplacementRequired(dto.isReplacementRequired);
        existing.setInspectionNotes(dto.inspectionNotes);
        existing.setRemarks(dto.remarks);

        return repository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        ReturnTransactions existing = getById(id);
        for (ProductSerialNumbers psn : existing.getProductSerialNumbers()) {
            psn.setReturnTransaction(null);
        }
        repository.delete(existing);
    }
}