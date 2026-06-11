package com.project2.ism.Service;

import com.project2.ism.DTO.TempDTOs.SettlementResultDTO;
import com.project2.ism.Model.*;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Separate async processor for franchise bulk settlements to avoid proxy issues.
 * Handles multi-threaded processing of merchant settlements within franchise batches.
 */
@Service
public class FranchiseAsyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(FranchiseAsyncProcessor.class);

    private final FranchiseSettlementBatchRepository franchiseBatchRepo;
    private final FranchiseBatchMerchantRepository batchMerchantRepo;
    private final MerchantSettlementBatchRepository batchRepo;
    private final EnhancedSettlementService2 settlementService;
    private final MerchantRepository merchantRepo;

    public FranchiseAsyncProcessor(FranchiseSettlementBatchRepository franchiseBatchRepo,
                                   FranchiseBatchMerchantRepository batchMerchantRepo,
                                   MerchantSettlementBatchRepository batchRepo, EnhancedSettlementService2 settlementService,
                                   MerchantRepository merchantRepo) {
        this.franchiseBatchRepo = franchiseBatchRepo;
        this.batchMerchantRepo = batchMerchantRepo;
        this.batchRepo = batchRepo;
        this.settlementService = settlementService;
        this.merchantRepo = merchantRepo;
    }

    /**
     * Process franchise batch with custom merchant-specific transactions
     * This is the NEW preferred approach
     */
    @Async("franchiseSettlementExecutor")
    public void processWithCustomTransactions(Long franchiseBatchId, Map<Long, List<String>> merchantTransactionsMap) {
        log.info("Starting franchise batch {} processing with custom transactions for {} merchants",
                franchiseBatchId, merchantTransactionsMap.size());

        try {
            FranchiseSettlementBatch batch = getFranchiseBatch(franchiseBatchId);

            // Validate all merchants in the map belong to this batch and franchise
            validateMerchantsInBatch(batch, merchantTransactionsMap.keySet());

            // Launch parallel processing for each merchant
            List<CompletableFuture<MerchantBatchResult>> futures = merchantTransactionsMap.entrySet().stream()
                    .map(entry -> {
                        Long merchantId = entry.getKey();
                        List<String> transactionIds = entry.getValue();
                        return processOneMerchantWithTransactions(franchiseBatchId, merchantId, transactionIds);
                    })
                    .toList();

            // Wait for all merchant processing to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Collect all results
            List<MerchantBatchResult> results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            log.error("Error getting merchant processing result", e);
                            return MerchantBatchResult.failed("Future failed: " + e.getMessage());
                        }
                    })
                    .collect(Collectors.toList());

            // Update franchise batch with aggregated results
            updateFranchiseBatchResults(franchiseBatchId, results);

            log.info("Completed franchise batch {} processing", franchiseBatchId);

        } catch (Exception e) {
            log.error("Franchise batch processing failed for batch {}", franchiseBatchId, e);
            updateBatchStatusToFailed(franchiseBatchId, e.getMessage());
        }
    }



    /**
     * Process one merchant with specific transactions in separate thread and transaction
     */
    @Async("merchantSettlementExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<MerchantBatchResult> processOneMerchantWithTransactions(Long franchiseBatchId,
                                                                                     Long merchantId,
                                                                                     List<String> transactionIds) {
        log.debug("Processing merchant {} with {} custom transactions for franchise batch {}",
                merchantId, transactionIds.size(), franchiseBatchId);

        MerchantBatchResult result = new MerchantBatchResult(merchantId);
        MerchantSettlementBatch merchantBatch = null; // DECLARE HERE
        try {
            // Get batch merchant record
            FranchiseBatchMerchant batchMerchant = batchMerchantRepo
                    .findByFranchiseBatchIdAndMerchantId(franchiseBatchId, merchantId);
            if (batchMerchant == null) {
                throw new IllegalStateException("Merchant " + merchantId + " not found in batch " + franchiseBatchId);
            }

            // Update merchant status to PROCESSING
            updateMerchantStatus(batchMerchant, FranchiseBatchMerchant.MerchantProcessingStatus.PROCESSING,
                    LocalDateTime.now(), null, null,BigDecimal.ZERO);

            // Validate merchant still belongs to franchise
            validateMerchantInFranchise(franchiseBatchId, merchantId);

            // Get franchise batch info
            FranchiseSettlementBatch batch = getFranchiseBatch(franchiseBatchId);

//            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//            String createdBy = (authentication != null) ? authentication.getName() : "SYSTEM";
            // Create merchant-level settlement batch for tracking
            merchantBatch = settlementService.createBatch(
                    merchantId, batch.getProductId(), batch.getCycleKey(), batch.getCreatedBy(),franchiseBatchId);

            result.setTotalTransactions(transactionIds.size());

            if (transactionIds.isEmpty()) {
                log.info("No transactions provided for merchant {}", merchantId);
                updateMerchantStatus(batchMerchant, FranchiseBatchMerchant.MerchantProcessingStatus.COMPLETED,
                        null, LocalDateTime.now(), null,BigDecimal.ZERO);
                settlementService.markBatchClosed(merchantBatch.getId());
                return CompletableFuture.completedFuture(result);
            }

            // Process each transaction
            for (String transactionId : transactionIds) {
                try {
                    SettlementResultDTO settlementResult = settlementService.settleOneEnhanced(
                            merchantId, merchantBatch.getId(), transactionId);

                    if ("OK".equals(settlementResult.getStatus())) {
                        result.addSuccess(settlementResult.getAmount(), settlementResult.getFee(),settlementResult.getNet(), settlementResult.getFranchiseCommission() );
                        log.debug("Successfully settled transaction {} for merchant {} , amount {} , fee {}", transactionId, merchantId,settlementResult.getAmount(), settlementResult.getFee());
                    } else {
                        result.addFailure();
                        log.warn("Failed to settle transaction {} for merchant {}: {}",
                                transactionId, merchantId, settlementResult.getMessage());
                    }

                } catch (Exception e) {
                    log.error("Exception settling transaction {} for merchant {}", transactionId, merchantId, e);
                    result.addFailure();
                }
            }

            // Close merchant batch and update status
            settlementService.markBatchClosed(merchantBatch.getId());
            updateMerchantBatchStatistics(merchantBatch.getId(), result);
            updateMerchantStatus(batchMerchant, FranchiseBatchMerchant.MerchantProcessingStatus.COMPLETED,
                    null, LocalDateTime.now(), null, result.getTotalNetAmount());

            log.info("Completed processing merchant {} for franchise batch {}: {} succeeded, {} failed",
                    merchantId, franchiseBatchId, result.getProcessedTransactions(), result.getFailedTransactions());

        } catch (Exception e) {
            log.error("Merchant processing failed for merchant {} in franchise batch {}", merchantId, franchiseBatchId, e);
            result.setError(e.getMessage());

            // Update merchant status to FAILED
            // Update merchant batch and status to FAILED
            try {
                updateMerchantBatchStatistics(merchantBatch.getId(), result);

                FranchiseBatchMerchant batchMerchant = batchMerchantRepo
                        .findByFranchiseBatchIdAndMerchantId(franchiseBatchId, merchantId);
                if (batchMerchant != null) {
                    updateMerchantStatus(batchMerchant, FranchiseBatchMerchant.MerchantProcessingStatus.FAILED,
                            null, LocalDateTime.now(), e.getMessage(), BigDecimal.ZERO);
                }
            } catch (Exception ex) {
                log.error("Failed to update merchant status to FAILED", ex);
            }
        }

        return CompletableFuture.completedFuture(result);
    }


    // ==================== HELPER METHODS ====================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFranchiseBatchResults(Long franchiseBatchId, List<MerchantBatchResult> results) {
        try {
            for (MerchantBatchResult result : results) {
                // process each result
                System.out.println("MerchantId: " + result.getMerchantId());
                System.out.println("Amount: " + result.getTotalAmount());
                System.out.println("getFranchiseCommission: " + result.getTotalFranchiseCommission());

            }
            FranchiseSettlementBatch batch = getFranchiseBatch(franchiseBatchId);

            int completed = 0, failed = 0;
            int totalTransactions = 0, processedTransactions = 0, failedTransactions = 0;
            BigDecimal totalAmount = BigDecimal.ZERO, totalFees = BigDecimal.ZERO;
            BigDecimal totalFranchiseCommission = BigDecimal.ZERO;
            BigDecimal totalNetAmount = BigDecimal.ZERO;
            for (MerchantBatchResult result : results) {
                if (result.getError() == null) {
                    completed++;
                    //have to see the working
                    totalTransactions += result.getTotalTransactions();
                    processedTransactions += result.getProcessedTransactions();
                    failedTransactions += result.getFailedTransactions();
                    totalAmount = totalAmount.add(result.getTotalAmount());
                    totalFees = totalFees.add(result.getTotalFees());
                    totalNetAmount = totalNetAmount.add(result.getTotalNetAmount());
                    if (result.getTotalFranchiseCommission() != null) {
                        totalFranchiseCommission = totalFranchiseCommission.add(result.getTotalFranchiseCommission());
                    }
                } else {
                    failed++;
                }

//                totalTransactions += result.getTotalTransactions();
//                processedTransactions += result.getProcessedTransactions();
//                failedTransactions += result.getFailedTransactions();
//                totalAmount = totalAmount.add(result.getTotalAmount());
//                totalFees = totalFees.add(result.getTotalFees());
//
//                if (result.getFranchiseCommission() != null) {
//                    totalFranchiseCommission = totalFranchiseCommission.add(result.getFranchiseCommission());
//                }
            }

            // Update batch totals
            batch.setCompletedMerchants(completed);
            batch.setFailedMerchants(failed);
            batch.setTotalTransactions(totalTransactions);
            batch.setProcessedTransactions(processedTransactions);
            batch.setFailedTransactions(failedTransactions);
            batch.setTotalAmount(totalAmount);
            batch.setTotalFees(totalFees);
            batch.setTotalNetAmount(totalNetAmount);
            batch.setTotalFranchiseCommission(totalFranchiseCommission);
            batch.setProcessingCompletedAt(LocalDateTime.now());

            // Set final status
            FranchiseSettlementBatch.BatchStatus finalStatus;
            if (failed == 0 && completed > 0) {
                finalStatus = FranchiseSettlementBatch.BatchStatus.COMPLETED;
            } else if (completed > 0 && failed > 0) {
                finalStatus = FranchiseSettlementBatch.BatchStatus.PARTIALLY_COMPLETED;
            } else {
                finalStatus = FranchiseSettlementBatch.BatchStatus.FAILED;
                batch.setErrorMessage("All merchant processing failed");
            }

            batch.setStatus(finalStatus);
            franchiseBatchRepo.save(batch);

            log.info("Updated franchise batch {} results: status={}, completed={}, failed={}, totalTx={}",
                    franchiseBatchId, finalStatus, completed, failed, totalTransactions);

        } catch (Exception e) {
            log.error("Failed to update franchise batch {} results", franchiseBatchId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBatchStatusToFailed(Long franchiseBatchId, String errorMessage) {
        try {
            FranchiseSettlementBatch batch = getFranchiseBatch(franchiseBatchId);
            batch.setStatus(FranchiseSettlementBatch.BatchStatus.FAILED);
            batch.setErrorMessage(errorMessage);
            batch.setProcessingCompletedAt(LocalDateTime.now());
            franchiseBatchRepo.save(batch);

            log.info("Updated franchise batch {} status to FAILED: {}", franchiseBatchId, errorMessage);

        } catch (Exception e) {
            log.error("Failed to update franchise batch {} status to FAILED", franchiseBatchId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMerchantStatus(FranchiseBatchMerchant batchMerchant,
                                     FranchiseBatchMerchant.MerchantProcessingStatus status,
                                     LocalDateTime processingStarted,
                                     LocalDateTime processingCompleted,
                                     String errorMessage,
                                     BigDecimal merchantNetAmount) {
        try {
            batchMerchant.setStatus(status);
            if (processingStarted != null) {
                batchMerchant.setProcessingStartedAt(processingStarted);
            }
            if (processingCompleted != null) {
                batchMerchant.setProcessingCompletedAt(processingCompleted);
                batchMerchant.setTotalAmount(merchantNetAmount);
            }
            if (errorMessage != null) {
                batchMerchant.setErrorMessage(errorMessage);
            }

            batchMerchantRepo.save(batchMerchant);

            log.debug("Updated batch merchant {} status to {}", batchMerchant.getId(), status);

        } catch (Exception e) {
            log.error("Failed to update batch merchant {} status to {}", batchMerchant.getId(), status, e);
        }
    }


    // Add this method to FranchiseAsyncProcessor class

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMerchantBatchStatistics(Long merchantBatchId, MerchantBatchResult result) {
        try {
            MerchantSettlementBatch merchantBatch = batchRepo.findById(merchantBatchId)
                    .orElseThrow(() -> new IllegalStateException("Merchant batch not found: " + merchantBatchId));

            // Update statistics
            merchantBatch.setTotalTransactions(result.getTotalTransactions());
            merchantBatch.setProcessedTransactions(result.getProcessedTransactions());
            merchantBatch.setFailedTransactions(result.getFailedTransactions());
            merchantBatch.setTotalAmount(result.getTotalAmount());
            merchantBatch.setTotalFees(result.getTotalFees());
            merchantBatch.setTotalNetAmount(result.getTotalNetAmount());

            // Update timestamps
            if (merchantBatch.getProcessingStartedAt() == null) {
                merchantBatch.setProcessingStartedAt(LocalDateTime.now());
            }
            merchantBatch.setProcessingCompletedAt(LocalDateTime.now());

            // Update status
            String finalStatus;
            if (result.getError() != null) {
                finalStatus = "FAILED";
                merchantBatch.setErrorMessage(result.getError());
            } else if (result.getFailedTransactions() == 0 && result.getProcessedTransactions() > 0) {
                finalStatus = "COMPLETED";
            } else if (result.getProcessedTransactions() > 0 && result.getFailedTransactions() > 0) {
                finalStatus = "PARTIALLY_COMPLETED";
            } else if (result.getFailedTransactions() > 0 && result.getProcessedTransactions() == 0) {
                finalStatus = "FAILED";
                merchantBatch.setErrorMessage("All transactions failed to process");
            } else {
                finalStatus = "COMPLETED"; // No transactions case
            }

            merchantBatch.setStatus(finalStatus);

            // Use the existing repository from EnhancedSettlementService2
            MerchantSettlementBatch savedBatch = batchRepo.save(merchantBatch);

            log.info("Updated merchant batch {} statistics: status={}, processed={}, failed={}, totalAmount={}",
                    merchantBatchId, finalStatus, result.getProcessedTransactions(),
                    result.getFailedTransactions(), result.getTotalAmount());

        } catch (Exception e) {
            log.error("Failed to update merchant batch {} statistics", merchantBatchId, e);
        }
    }

    // ==================== VALIDATION METHODS ====================

    private FranchiseSettlementBatch getFranchiseBatch(Long batchId) {
        return franchiseBatchRepo.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Franchise batch not found: " + batchId));
    }

    private void validateMerchantsInBatch(FranchiseSettlementBatch batch, Set<Long> merchantIds) {
        List<FranchiseBatchMerchant> batchMerchants = batchMerchantRepo.findByFranchiseBatchId(batch.getId());
        Set<Long> validMerchantIds = batchMerchants.stream()
                .map(FranchiseBatchMerchant::getMerchantId)
                .collect(Collectors.toSet());

        Set<Long> invalidMerchants = merchantIds.stream()
                .filter(id -> !validMerchantIds.contains(id))
                .collect(Collectors.toSet());

        if (!invalidMerchants.isEmpty()) {
            throw new IllegalArgumentException("Merchants not in batch " + batch.getId() + ": " + invalidMerchants);
        }
    }

    private void validateMerchantInFranchise(Long franchiseBatchId, Long merchantId) {
        FranchiseSettlementBatch batch = getFranchiseBatch(franchiseBatchId);

        Merchant merchant = merchantRepo.findById(merchantId)
                .orElseThrow(() -> new IllegalStateException("Merchant not found: " + merchantId));

        if (merchant.getFranchise() == null || !merchant.getFranchise().getId().equals(batch.getFranchiseId())) {
            throw new IllegalStateException("Merchant " + merchantId +
                    " does not belong to franchise " + batch.getFranchiseId());
        }
    }



    // ==================== RESULT CLASS ====================

    public static class MerchantBatchResult {
        private Long merchantId;
        private int totalTransactions = 0;
        private int processedTransactions = 0;
        private int failedTransactions = 0;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private BigDecimal totalFees = BigDecimal.ZERO;

        private BigDecimal totalNetAmount = BigDecimal.ZERO;
        private BigDecimal totalFranchiseCommission = BigDecimal.ZERO;
        private String error;

        public MerchantBatchResult(Long merchantId) {
            this.merchantId = merchantId;
        }

        public static MerchantBatchResult failed(String error) {
            MerchantBatchResult result = new MerchantBatchResult(null);
            result.setError(error);
            return result;
        }

        public void addSuccess(BigDecimal amount, BigDecimal fee,BigDecimal netAmount,BigDecimal franchiseCommission) {
            processedTransactions++;
            totalAmount = totalAmount.add(amount);
            totalFees = totalFees.add(fee);
            totalNetAmount = totalNetAmount.add(netAmount);
            totalFranchiseCommission = totalFranchiseCommission.add(franchiseCommission);
        }

        public void addFailure() {
            failedTransactions++;
        }

        public BigDecimal getTotalNetAmount() {
            return totalNetAmount;
        }

        public void setTotalNetAmount(BigDecimal totalNetAmount) {
            this.totalNetAmount = totalNetAmount;
        }

        // Getters and setters
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

        public int getTotalTransactions() { return totalTransactions; }
        public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }

        public int getProcessedTransactions() { return processedTransactions; }
        public void setProcessedTransactions(int processedTransactions) { this.processedTransactions = processedTransactions; }

        public int getFailedTransactions() { return failedTransactions; }
        public void setFailedTransactions(int failedTransactions) { this.failedTransactions = failedTransactions; }

        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

        public BigDecimal getTotalFees() { return totalFees; }
        public void setTotalFees(BigDecimal totalFees) { this.totalFees = totalFees; }

        public BigDecimal getTotalFranchiseCommission() { return totalFranchiseCommission; }
        public void setTotalFranchiseCommission(BigDecimal franchiseCommission) { this.totalFranchiseCommission = franchiseCommission; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }



}