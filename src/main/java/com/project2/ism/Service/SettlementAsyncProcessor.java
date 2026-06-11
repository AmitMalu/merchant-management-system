package com.project2.ism.Service;

import com.project2.ism.DTO.TempDTOs.SettlementResultDTO;
import com.project2.ism.Model.MerchantSettlementBatch;
import com.project2.ism.Model.SettlementBatchCandidate;
import com.project2.ism.Repository.MerchantSettlementBatchRepository;
import com.project2.ism.Repository.SettlementBatchCandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Separate service to handle async processing to avoid proxy issues.
 * This service runs async settlement processing in separate transactions.
 */
@Service
public class SettlementAsyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(SettlementAsyncProcessor.class);

    private final MerchantSettlementBatchRepository batchRepo;
    private final SettlementBatchCandidateRepository candidateRepo;
    private final EnhancedSettlementService2 settlementService;

    public SettlementAsyncProcessor(MerchantSettlementBatchRepository batchRepo,
                                    SettlementBatchCandidateRepository candidateRepo,
                                    @Lazy EnhancedSettlementService2 settlementService) {
        this.batchRepo = batchRepo;
        this.candidateRepo = candidateRepo;
        this.settlementService = settlementService;
    }

    /**
     * Process batch with detailed tracking in async manner.
     * This method runs in a separate thread pool to avoid blocking.
     */
    @Async("settlementExecutor")
    public void processBatchWithTracking(Long batchId) {
        log.info("Starting async processing for batch {}", batchId);

        try {
            // Update batch status to PROCESSING in separate transaction
            updateBatchStatus(batchId, "PROCESSING", LocalDateTime.now(), null, null);

            // Get all selected candidates
            List<SettlementBatchCandidate> candidates = candidateRepo.findByBatchIdAndStatus(
                    batchId, SettlementBatchCandidate.CandidateStatus.SELECTED);

            if (candidates.isEmpty()) {
                log.warn("No candidates found for batch {}", batchId);
                updateBatchStatus(batchId, "COMPLETED", null, LocalDateTime.now(), "No candidates to process");
                return;
            }

            log.info("Processing {} candidates for batch {}", candidates.size(), batchId);

            int processed = 0, failed = 0;
            BigDecimal amountTotal = BigDecimal.ZERO, feesTotal = BigDecimal.ZERO, netTotal = BigDecimal.ZERO;

            // Process each candidate in sequence (keeps DB connections bounded)
            for (SettlementBatchCandidate candidate : candidates) {
                try {
                    // Update candidate status to PROCESSING
                    updateCandidateStatus(candidate, SettlementBatchCandidate.CandidateStatus.PROCESSING);

                    // Get batch info for merchant ID
                    MerchantSettlementBatch batch = batchRepo.findById(batchId)
                            .orElseThrow(() -> new IllegalStateException("Batch not found during processing: " + batchId));

                    // Process the settlement in a new transaction
                    SettlementResultDTO result = settlementService.settleOneEnhanced(
                            batch.getMerchantId(), batchId, candidate.getVendorTxId());

                    if ("OK".equals(result.getStatus())) {
                        updateCandidateStatus(candidate, SettlementBatchCandidate.CandidateStatus.COMPLETED);
                        processed++;
                        amountTotal=amountTotal.add( result.getAmount() != null ? result.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
                        feesTotal = feesTotal.add( result.getFee() != null ?result.getFee().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
                        netTotal = netTotal.add( result.getNet() != null ?result.getNet().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
                        log.debug("Successfully processed transaction {} for batch {}",
                                candidate.getVendorTxId(), batchId);
                    } else {
                        updateCandidateStatus(candidate, SettlementBatchCandidate.CandidateStatus.FAILED);
                        failed++;
                        log.warn("Failed to process transaction {} for batch {}: {}",
                                candidate.getVendorTxId(), batchId, result.getMessage());
                    }

                } catch (Exception e) {
                    log.error("Exception processing candidate {} in batch {}",
                            candidate.getVendorTxId(), batchId, e);
                    updateCandidateStatus(candidate, SettlementBatchCandidate.CandidateStatus.FAILED);
                    failed++;
                }
            }

            // Update final batch results
            updateBatchResults(batchId, processed, failed,amountTotal,feesTotal,netTotal);

            log.info("Completed batch {} processing: {} succeeded, {} failed,{} amountTotal, {} feesTotal,{} netTotal", batchId, processed, failed,amountTotal,feesTotal,netTotal);

        } catch (Exception e) {
            log.error("Batch processing failed for batch {}", batchId, e);
            updateBatchStatus(batchId, "FAILED", null, LocalDateTime.now(), e.getMessage());
        }
    }

    /**
     * Update batch status in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBatchStatus(Long batchId, String status, LocalDateTime processingStarted,
                                  LocalDateTime processingCompleted, String errorMessage) {
        try {
            MerchantSettlementBatch batch = batchRepo.findById(batchId)
                    .orElseThrow(() -> new IllegalStateException("Batch not found: " + batchId));

            batch.setStatus(status);
            if (processingStarted != null) {
                batch.setProcessingStartedAt(processingStarted);
            }
            if (processingCompleted != null) {
                batch.setProcessingCompletedAt(processingCompleted);
            }
            if (errorMessage != null) {
                batch.setErrorMessage(errorMessage);
            }

            batchRepo.save(batch);
            log.debug("Updated batch {} status to {}", batchId, status);

        } catch (Exception e) {
            log.error("Failed to update batch {} status to {}", batchId, status, e);
        }
    }

    /**
     * Update batch final results in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBatchResults(Long batchId, int processed, int failed, BigDecimal amountTotal,BigDecimal feesTotal,BigDecimal netTotal) {
        try {
            MerchantSettlementBatch batch = batchRepo.findById(batchId)
                    .orElseThrow(() -> new IllegalStateException("Batch not found: " + batchId));

            batch.setProcessedTransactions(processed);
            batch.setFailedTransactions(failed);
            batch.setTotalAmount(amountTotal);
            batch.setTotalFees(feesTotal);
            batch.setTotalNetAmount(netTotal);
            batch.setProcessingCompletedAt(LocalDateTime.now());

            // Set final status based on results
            String finalStatus;
            if (failed == 0 && processed > 0) {
                finalStatus = "COMPLETED";
            } else if (processed > 0 && failed > 0) {
                finalStatus = "PARTIALLY_COMPLETED";
            } else if (failed > 0 && processed == 0) {
                finalStatus = "FAILED";
                batch.setErrorMessage("All transactions failed to process");
            } else {
                finalStatus = "COMPLETED"; // No transactions to process
            }

            batch.setStatus(finalStatus);
            batchRepo.save(batch);

            log.info("Updated batch {} final results: status={}, processed={}, failed={}",
                    batchId, finalStatus, processed, failed);

        } catch (Exception e) {
            log.error("Failed to update batch {} results", batchId, e);
        }
    }

    /**
     * Update candidate status in separate transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCandidateStatus(SettlementBatchCandidate candidate,
                                      SettlementBatchCandidate.CandidateStatus status) {
        try {
            candidate.setStatus(status);
            candidateRepo.save(candidate);

            log.debug("Updated candidate {} status to {}", candidate.getVendorTxId(), status);

        } catch (Exception e) {
            log.error("Failed to update candidate {} status to {}", candidate.getVendorTxId(), status, e);
        }
    }
}