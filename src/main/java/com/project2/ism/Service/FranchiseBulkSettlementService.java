package com.project2.ism.Service;

import com.project2.ism.DTO.TempDTOs.*;
import com.project2.ism.Model.*;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FranchiseBulkSettlementService {

    private static final Logger log = LoggerFactory.getLogger(FranchiseBulkSettlementService.class);

    private final FranchiseSettlementBatchRepository franchiseBatchRepo;
    private final FranchiseBatchMerchantRepository batchMerchantRepo;
    private final EnhancedSettlementService2 settlementService;
    private final MerchantRepository merchantRepo;
    private final FranchiseAsyncProcessor franchiseAsyncProcessor;

    public FranchiseBulkSettlementService(
            FranchiseSettlementBatchRepository franchiseBatchRepo,
            FranchiseBatchMerchantRepository batchMerchantRepo,
            EnhancedSettlementService2 settlementService,
            MerchantRepository merchantRepo,
            FranchiseAsyncProcessor franchiseAsyncProcessor) {
        this.franchiseBatchRepo = franchiseBatchRepo;
        this.batchMerchantRepo = batchMerchantRepo;
        this.settlementService = settlementService;
        this.merchantRepo = merchantRepo;
        this.franchiseAsyncProcessor = franchiseAsyncProcessor;
    }

    /**
     * Get available merchants for franchise with product-specific candidate counts
     */
    public List<FranchiseMerchantOption> getAvailableMerchantsForFranchise(Long franchiseId, String cycleKey, Long productId) {
        log.debug("Getting available merchants for franchise={}, cycle={}, product={}", franchiseId, cycleKey, productId);

        List<Merchant> allMerchants = merchantRepo.findByFranchiseId(franchiseId);
        log.debug("Found {} merchants for franchise {}", allMerchants.size(), franchiseId);

        return allMerchants.stream().map(merchant -> {
            try {
                // Re-validate merchant still belongs to franchise
                if (merchant.getFranchise() == null || !merchant.getFranchise().getId().equals(franchiseId)) {
                    log.warn("Merchant {} no longer belongs to franchise {}", merchant.getId(), franchiseId);
                    return createEmptyMerchantOption(merchant);
                }

                // Get candidates for this merchant and product
                List<SettlementCandidateDTO> candidates = settlementService
                        .listSettlementCandidatesForCycle(merchant.getId(), cycleKey, productId);

                int validCandidates = (int) candidates.stream()
                        .filter(c -> c.getError() == null)
                        .count();

                BigDecimal totalAmount = candidates.stream()
                        .filter(c -> c.getError() == null)
                        .map(SettlementCandidateDTO::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                String contactPersonName = merchant.getContactPerson() != null ?
                        merchant.getContactPerson().getName() : "N/A";

                log.debug("Merchant {} has {} valid candidates worth {}",
                        merchant.getId(), validCandidates, totalAmount);

                return new FranchiseMerchantOption(
                        merchant.getId(),
                        merchant.getBusinessName(),
                        contactPersonName,
                        validCandidates,
                        totalAmount
                );

            } catch (Exception e) {
                log.error("Error getting candidates for merchant {}", merchant.getId(), e);
                return createEmptyMerchantOption(merchant);
            }
        }).collect(Collectors.toList());
    }

    /**
     * Create selective franchise batch
     */
    @Transactional
    public FranchiseSettlementBatch createSelectiveFranchiseBatch(Long franchiseId,Long productId, String cycleKey,
                                                                  List<Long> selectedMerchantIds, String createdBy) {
        log.info("Creating selective franchise batch for franchise={}, cycle={}, merchants={}",
                franchiseId, cycleKey, selectedMerchantIds);

        // Validate all selected merchants belong to franchise
        validateMerchantsBelongToFranchise(franchiseId, selectedMerchantIds);

        // Create franchise batch
        FranchiseSettlementBatch batch = new FranchiseSettlementBatch(franchiseId,productId, cycleKey, createdBy);
        batch.setTotalMerchants(selectedMerchantIds.size());
        batch.setStatus(FranchiseSettlementBatch.BatchStatus.DRAFT);
        batch = franchiseBatchRepo.save(batch);

        // Add selected merchants to batch
        for (Long merchantId : selectedMerchantIds) {
            FranchiseBatchMerchant batchMerchant = new FranchiseBatchMerchant(batch.getId(), merchantId);
            batchMerchant.setStatus(FranchiseBatchMerchant.MerchantProcessingStatus.SELECTED);
            batchMerchantRepo.save(batchMerchant);
        }

        log.info("Created franchise batch {} with {} merchants", batch.getId(), selectedMerchantIds.size());
        return batch;
    }

    /**
     * Create full franchise batch (all merchants)
     */
    @Transactional
    public FranchiseSettlementBatch createFullFranchiseBatch(Long franchiseId,Long productId, String cycleKey, String createdBy) {
        log.info("Creating full franchise batch for franchise={}, cycle={}", franchiseId, cycleKey);

        List<Merchant> allMerchants = merchantRepo.findByFranchiseId(franchiseId);
        if (allMerchants.isEmpty()) {
            throw new IllegalStateException("No merchants found for franchise: " + franchiseId);
        }

        List<Long> allMerchantIds = allMerchants.stream()
                .map(Merchant::getId)
                .collect(Collectors.toList());

        return createSelectiveFranchiseBatch(franchiseId,productId, cycleKey, allMerchantIds, createdBy);
    }

    /**
     * Update merchant selection in draft batch
     */
    @Transactional
    public FranchiseSettlementBatch updateMerchantSelection(Long franchiseBatchId, List<Long> newSelectedMerchantIds) {
        log.info("Updating merchant selection for batch {} with {} merchants",
                franchiseBatchId, newSelectedMerchantIds.size());

        FranchiseSettlementBatch batch = getBatchById(franchiseBatchId);

        if (batch.getStatus() != FranchiseSettlementBatch.BatchStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify merchant selection after batch is finalized. Current status: " + batch.getStatus());
        }

        // Validate new merchants belong to franchise
        validateMerchantsBelongToFranchise(batch.getFranchiseId(), newSelectedMerchantIds);

        // Remove existing selections
        batchMerchantRepo.deleteByFranchiseBatchId(franchiseBatchId);

        // Add new selections
        for (Long merchantId : newSelectedMerchantIds) {
            FranchiseBatchMerchant batchMerchant = new FranchiseBatchMerchant(franchiseBatchId, merchantId);
            batchMerchant.setStatus(FranchiseBatchMerchant.MerchantProcessingStatus.SELECTED);
            batchMerchantRepo.save(batchMerchant);
        }

        batch.setTotalMerchants(newSelectedMerchantIds.size());
        FranchiseSettlementBatch savedBatch = franchiseBatchRepo.save(batch);

        log.info("Updated batch {} merchant selection to {} merchants", franchiseBatchId, newSelectedMerchantIds.size());
        return savedBatch;
    }

    /**
     * Get settlement candidates for specific merchant in franchise batch
     */
    @Transactional(readOnly = true)
    public List<SettlementCandidateDTO> listSettlementCandidatesForMerchant(Long franchiseId,
                                                                            Long batchId,
                                                                            Long merchantId,
                                                                            String cycleKey,
                                                                            Long productId) {
        log.debug("Getting candidates for franchise={}, batch={}, merchant={}, product={}",
                franchiseId, batchId, merchantId, productId);

        // Verify batch exists and belongs to franchise
        FranchiseSettlementBatch batch = getBatchById(batchId);
        if (!batch.getFranchiseId().equals(franchiseId)) {
            throw new IllegalArgumentException("Batch " + batchId + " does not belong to franchise " + franchiseId);
        }

        // Verify merchant belongs to this batch
        FranchiseBatchMerchant batchMerchant = batchMerchantRepo
                .findByFranchiseBatchIdAndMerchantId(batchId, merchantId);
        if (batchMerchant == null) {
            throw new IllegalArgumentException("Merchant " + merchantId + " not part of franchise batch " + batchId);
        }

        // Verify merchant still belongs to the franchise
        validateMerchantBelongsToFranchise(franchiseId, merchantId);

        // Get candidates using settlement service with product filtering
        return settlementService.listSettlementCandidatesForCycle(merchantId, cycleKey, productId);
    }

    /**
     * Process franchise batch with merchant-specific transactions
     * NEW APPROACH: Frontend sends Map<MerchantId, List<TransactionId>>
     */
    public void processSelectedMerchantsWithTransactions(Long franchiseBatchId,
                                                         Map<Long, List<String>> merchantTransactionsMap) {
        log.info("Processing franchise batch {} with {} merchants and custom transactions",
                franchiseBatchId, merchantTransactionsMap.size());

        FranchiseSettlementBatch batch = getBatchById(franchiseBatchId);

        if (batch.getStatus() != FranchiseSettlementBatch.BatchStatus.DRAFT) {
            throw new IllegalStateException("Batch must be in DRAFT status for processing. Current: " + batch.getStatus());
        }

        // Validate all merchants in request belong to this batch
        validateMerchantsInBatch(franchiseBatchId, merchantTransactionsMap.keySet());

        // Update batch status and trigger async processing
        batch.setStatus(FranchiseSettlementBatch.BatchStatus.PROCESSING);
        batch.setProcessingStartedAt(LocalDateTime.now());
        franchiseBatchRepo.save(batch);

        // Trigger async processing with merchant-specific transactions
        franchiseAsyncProcessor.processWithCustomTransactions(franchiseBatchId, merchantTransactionsMap);

        log.info("Started async processing for franchise batch {}", franchiseBatchId);
    }


    // ==================== READ-ONLY METHODS ====================

    @Transactional(readOnly = true)
    public FranchiseSettlementBatch getBatchById(Long batchId) {
        return franchiseBatchRepo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Franchise batch not found: " + batchId));
    }

    @Transactional(readOnly = true)
    public List<FranchiseSettlementBatch> getAllBatches(Long franchiseId) {
        return franchiseBatchRepo.findByFranchiseIdOrderByCreatedAtDesc(franchiseId);
    }

    @Transactional(readOnly = true)
    public BatchProgressDTO getBatchProgress(Long batchId) {
        FranchiseSettlementBatch batch = getBatchById(batchId);

        BatchProgressDTO progress = new BatchProgressDTO();
        progress.setBatchId(batchId);
        progress.setStatus(batch.getStatus().toString());
        progress.setTotalTransactions(batch.getTotalTransactions());
        progress.setProcessedTransactions(batch.getProcessedTransactions());
        progress.setFailedTransactions(batch.getFailedTransactions());
        progress.setTotalAmount(batch.getTotalAmount());
        progress.setTotalFees(batch.getTotalFees());
        progress.setTotalNetAmount(batch.getTotalNetAmount());
        progress.setProcessingStartedAt(batch.getProcessingStartedAt());
        progress.setProcessingCompletedAt(batch.getProcessingCompletedAt());
        progress.setErrorMessage(batch.getErrorMessage());

        if (batch.getTotalTransactions() != null && batch.getTotalTransactions() > 0) {
            int totalProcessed = (batch.getProcessedTransactions() != null ? batch.getProcessedTransactions() : 0) +
                    (batch.getFailedTransactions() != null ? batch.getFailedTransactions() : 0);
            progress.setProgressPercentage((totalProcessed * 100.0) / batch.getTotalTransactions());
        } else {
            progress.setProgressPercentage(0.0);
        }

        return progress;
    }

    // ==================== VALIDATION METHODS ====================

    private void validateMerchantsBelongToFranchise(Long franchiseId, List<Long> merchantIds) {
        List<Merchant> merchants = merchantRepo.findAllById(merchantIds);

        // Check all merchants were found
        if (merchants.size() != merchantIds.size()) {
            Set<Long> foundIds = merchants.stream().map(Merchant::getId).collect(Collectors.toSet());
            Set<Long> missingIds = merchantIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toSet());
            throw new IllegalArgumentException("Merchants not found: " + missingIds);
        }

        // Check all merchants belong to franchise
        for (Merchant merchant : merchants) {
            if (merchant.getFranchise() == null || !merchant.getFranchise().getId().equals(franchiseId)) {
                throw new IllegalArgumentException("Merchant " + merchant.getId() +
                        " does not belong to franchise " + franchiseId);
            }
        }
    }

    private void validateMerchantBelongsToFranchise(Long franchiseId, Long merchantId) {
        Merchant merchant = merchantRepo.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        if (merchant.getFranchise() == null || !merchant.getFranchise().getId().equals(franchiseId)) {
            throw new IllegalArgumentException("Merchant " + merchantId +
                    " does not belong to franchise " + franchiseId);
        }
    }

    private void validateMerchantsInBatch(Long batchId, Set<Long> merchantIds) {
        List<FranchiseBatchMerchant> batchMerchants = batchMerchantRepo.findByFranchiseBatchId(batchId);
        Set<Long> validMerchantIds = batchMerchants.stream()
                .map(FranchiseBatchMerchant::getMerchantId)
                .collect(Collectors.toSet());

        Set<Long> invalidMerchants = merchantIds.stream()
                .filter(id -> !validMerchantIds.contains(id))
                .collect(Collectors.toSet());

        if (!invalidMerchants.isEmpty()) {
            throw new IllegalArgumentException("Merchants not in batch " + batchId + ": " + invalidMerchants);
        }
    }


    // ==================== UTILITY METHODS ====================

    private FranchiseMerchantOption createEmptyMerchantOption(Merchant merchant) {
        String contactPersonName = merchant.getContactPerson() != null ?
                merchant.getContactPerson().getName() : "N/A";

        return new FranchiseMerchantOption(
                merchant.getId(),
                merchant.getBusinessName(),
                contactPersonName,
                0,
                BigDecimal.ZERO
        );
    }
}