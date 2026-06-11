package com.project2.ism.Service;

import com.project2.ism.DTO.AssignMerchantRequest;
import com.project2.ism.DTO.ProductDistributionDTO;
import com.project2.ism.DTO.ProductSerialDTO;
import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.InventoryTransactions.ProductDistribution;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.*;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductDistributionService {

    private final ProductDistributionRepository productDistributionRepository;
    private final ProductSerialsRepository serialRepo;
    private final MerchantRepository merchantRepo;
    private final FranchiseRepository franchiseRepository;

    private final InwardTransactionRepository inwardTransactionRepository;

    private final OutwardTransactionRepository outwardTransactionRepository;



    public ProductDistributionService(ProductDistributionRepository productDistributionRepository, ProductSerialsRepository serialRepo, MerchantRepository merchantRepo, FranchiseRepository franchiseRepository, InwardTransactionRepository inwardTransactionRepository, OutwardTransactionRepository outwardTransactionRepository) {
        this.productDistributionRepository = productDistributionRepository;
        this.serialRepo = serialRepo;
        this.merchantRepo = merchantRepo;
        this.franchiseRepository = franchiseRepository;
        this.inwardTransactionRepository = inwardTransactionRepository;
        this.outwardTransactionRepository = outwardTransactionRepository;
    }

    @Transactional
    public void assignSerialsToMerchant(AssignMerchantRequest request) {
        Long franchiseId = request.getFranchiseId();
        Long merchantId = request.getMerchantId();
        List<Long> serialIds = request.getSelectedDeviceIds();

        // ✅ validate merchant
        Merchant merchant = merchantRepo.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found with id: " + merchantId));

        Franchise franchise = franchiseRepository.findById(request.getFranchiseId())
                .orElseThrow(() -> new RuntimeException("Franchise not found"));
        // ✅ create distribution record
        ProductDistribution distribution = new ProductDistribution();
        distribution.setMerchant(merchant);
        distribution.setFranchise(franchise);
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        distribution.setDistributedBy(email);

        distribution.setQuantity((long) serialIds.size());
        distribution.setDistributedDate(LocalDateTime.now());

        // ✅ save first so we have distributionId for mapping
        ProductDistribution saved = productDistributionRepository.save(distribution);

        // ✅ bulk update serials with merchant + distribution
        int updated = serialRepo.assignMerchantToSerials(merchantId, serialIds, saved.getId());

        if (updated != serialIds.size()) {
            throw new IllegalStateException("Some serial IDs were not updated. Check if IDs exist.");
        }

    }

    /**
     * Patch only the receivedDate
     */
    @Transactional
    public ProductDistributionDTO markAsReceived(Long distributionId) {
        ProductDistribution dist = productDistributionRepository.findById(distributionId)
                .orElseThrow(() -> new ResourceNotFoundException("Distribution not found: " + distributionId));

        LocalDateTime time  = LocalDateTime.now();
        dist.setReceivedDate(time);
        List<ProductSerialNumbers> serials = dist.getProductSerialNumbers();
        serials.forEach(psn -> psn.setReceivedDate(time));
        serialRepo.saveAll(serials);
        return toDto(productDistributionRepository.save(dist));
    }

    /**
     * Get all product distributions
     */
    public List<ProductDistributionDTO> getAll() {
        return productDistributionRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Get distributions by franchise
     */
    public List<ProductDistributionDTO> getByFranchise(Long franchiseId) {
        return productDistributionRepository.findByFranchiseId(franchiseId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Delete distribution and unlink product_serial_numbers
     */
    @Transactional
    public void delete(Long distributionId) {
        ProductDistribution dist = productDistributionRepository.findById(distributionId)
                .orElseThrow(() -> new ResourceNotFoundException("Distribution not found: " + distributionId));

        // unlink serials before deleting distribution
        serialRepo.clearDistributionFromSerials(distributionId);

        productDistributionRepository.delete(dist);
    }
    /**
     * Get distributions by merchant
     */
    public List<ProductDistributionDTO> getByMerchant(Long merchantId) {
        return productDistributionRepository.findByMerchantId(merchantId)
                .stream()
                .map(this::toDto)
                .toList();
    }
    private ProductDistributionDTO toDto(ProductDistribution entity) {
        ProductDistributionDTO dto = new ProductDistributionDTO();
        dto.setId(entity.getId());
        dto.setQuantity(entity.getQuantity());
        dto.setDistributedBy(entity.getDistributedBy());
        dto.setDistributedDate(entity.getDistributedDate());
        dto.setReceivedDate(entity.getReceivedDate());

        if (entity.getFranchise() != null) {
            dto.setFranchiseId(entity.getFranchise().getId());
            dto.setFranchiseName(entity.getFranchise().getFranchiseName());
        }
        if (entity.getMerchant() != null) {
            dto.setMerchantId(entity.getMerchant().getId());
            dto.setMerchantName(entity.getMerchant().getBusinessName());
        }
        if (entity.getProductSerialNumbers() != null) {
            dto.setSerialNumbers( entity.getProductSerialNumbers().stream()
                    .map(ProductSerialDTO::fromEntity)
                    .collect(Collectors.toList())
        );
        }
        return dto;
    }


}
