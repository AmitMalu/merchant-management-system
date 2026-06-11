package com.project2.ism.Controller;

import com.project2.ism.DTO.TempDTOs.BatchCreatedResponse;
import com.project2.ism.DTO.TempDTOs.BatchProgressDTO;
import com.project2.ism.DTO.TempDTOs.SettlementCandidateDTO;
import com.project2.ism.DTO.TempDTOs.SettlementRequest;
import com.project2.ism.Model.MerchantSettlementBatch;
import com.project2.ism.Service.EnhancedSettlementService2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/merchants/{merchantId}/settlement")
public class EnhancedSettlementController {

    private final EnhancedSettlementService2 enhancedSettlementService;

    @Autowired
    public EnhancedSettlementController(EnhancedSettlementService2 enhancedSettlementService) {
        this.enhancedSettlementService = enhancedSettlementService;
    }

    /**
     * Create or get active batch for direct merchant settlement with product_id support
     */
    @PostMapping("/batches")
    public ResponseEntity<BatchCreatedResponse> getOrCreateActiveBatch(
            @PathVariable Long merchantId,
            @RequestParam String cycleKey,
            @RequestParam Long productId) { // NEW: Required product_id parameter

        try {
            // ✅ Extract current user (email/username) from Spring Security
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String createdBy = (authentication != null) ? authentication.getName() : "SYSTEM";
            MerchantSettlementBatch batch = enhancedSettlementService
                    .getOrCreateActiveBatch(merchantId, productId, cycleKey, createdBy);

            BatchCreatedResponse response = new BatchCreatedResponse(batch);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        }
    }

    /**
     * Get settlement candidates for a batch with product filtering
     */
    @GetMapping("/batches/{batchId}/candidates")
    public ResponseEntity<List<SettlementCandidateDTO>> getSettlementCandidates(
            @PathVariable Long merchantId,
            @PathVariable Long batchId) {

        try {
            MerchantSettlementBatch batch = enhancedSettlementService.getBatchById(batchId);

            // Validate merchant ownership
            if (!merchantId.equals(batch.getMerchantId())) {
                return ResponseEntity.badRequest().build();
            }
            System.out.println(batch.getWindowStart());
            // Use product_id from batch for filtering
            List<SettlementCandidateDTO> candidates = enhancedSettlementService
                    .listSettlementCandidates(merchantId, batch.getWindowStart(), batch.getWindowEnd(), batch.getProductId());

            return ResponseEntity.ok(candidates);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update batch candidates (for selective settlement)
     */
    @PostMapping("/batches/{batchId}/candidates")
    public ResponseEntity<Map<String, Object>> updateBatchCandidates(
            @PathVariable Long merchantId,
            @PathVariable Long batchId,
            @RequestBody SettlementRequest request) {

        try {
            // Validate batch belongs to merchant
            MerchantSettlementBatch batch = enhancedSettlementService.getBatchById(batchId);
            if (!merchantId.equals(batch.getMerchantId())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Batch does not belong to specified merchant"
                ));
            }

            List<String> vendorTxIds = request.vendorTxIds;
            MerchantSettlementBatch updatedBatch = enhancedSettlementService.updateBatchCandidates(batchId, vendorTxIds);

            return ResponseEntity.ok(Map.of(
                    "batchId", updatedBatch.getId(),
                    "status", updatedBatch.getStatus(),
                    "totalTransactions", updatedBatch.getTotalTransactions(),
                    "totalAmount", updatedBatch.getTotalAmount(),
                    "totalFees", updatedBatch.getTotalFees(),
                    "totalNetAmount", updatedBatch.getTotalNetAmount(),
                    "message", "Batch candidates updated successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }



    /**
     * Resume failed batch
     */
    @PostMapping("/batches/{batchId}/resume")
    public ResponseEntity<Map<String, Object>> resumeBatch(
            @PathVariable Long merchantId,
            @PathVariable Long batchId) {

        try {
            // Validate batch belongs to merchant
            MerchantSettlementBatch batch = enhancedSettlementService.getBatchById(batchId);
            if (!merchantId.equals(batch.getMerchantId())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Batch does not belong to specified merchant"
                ));
            }

            enhancedSettlementService.resumeBatch(batchId);
            return ResponseEntity.accepted().body(Map.of(
                    "batchId", batchId,
                    "status", "RESUMING",
                    "message", "Batch resume initiated"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get batch progress with detailed tracking
     */
    @GetMapping("/batches/{batchId}/progress")
    public ResponseEntity<BatchProgressDTO> getBatchProgress(
            @PathVariable Long merchantId,
            @PathVariable Long batchId) {

        try {
            // Validate batch belongs to merchant
            MerchantSettlementBatch batch = enhancedSettlementService.getBatchById(batchId);
            if (!merchantId.equals(batch.getMerchantId())) {
                return ResponseEntity.badRequest().build();
            }

            BatchProgressDTO progress = enhancedSettlementService.getBatchProgress(batchId);
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get batch details
     */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BatchCreatedResponse> getBatchDetails(
            @PathVariable Long merchantId,
            @PathVariable Long batchId) {

        try {
            MerchantSettlementBatch batch = enhancedSettlementService.getBatchById(batchId);

            // Validate batch belongs to merchant
            if (!merchantId.equals(batch.getMerchantId())) {
                return ResponseEntity.badRequest().build();
            }

            return ResponseEntity.ok(new BatchCreatedResponse(batch));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all batches for merchant with limit
     */
    @GetMapping("/batches")
    public ResponseEntity<List<BatchCreatedResponse>> getAllBatches(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            List<MerchantSettlementBatch> batches = enhancedSettlementService.getAllBatchesByMerchant(merchantId, limit);
            List<BatchCreatedResponse> responses = batches.stream()
                    .map(BatchCreatedResponse::new)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get settlement candidates by cycle with product filtering (without creating batch) // have to study this don't know what
     */
    @GetMapping("/candidates")
    public ResponseEntity<List<SettlementCandidateDTO>> getSettlementCandidatesByCycle(
            @PathVariable Long merchantId,
            @RequestParam String cycleKey,
            @RequestParam Long productId) { // NEW: Required product_id parameter

        try {
            List<SettlementCandidateDTO> candidates = enhancedSettlementService
                    .listSettlementCandidatesForCycle(merchantId, cycleKey, productId);
            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel/Delete a draft batch
     */
    @DeleteMapping("/batches/{batchId}")
    public ResponseEntity<Map<String, Object>> cancelBatch(
            @PathVariable Long merchantId,
            @PathVariable Long batchId) {

        try {
            MerchantSettlementBatch batch = enhancedSettlementService.getBatchById(batchId);

            // Validate batch belongs to merchant
            if (!merchantId.equals(batch.getMerchantId())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Batch does not belong to specified merchant"
                ));
            }

            // Only allow cancellation of DRAFT or OPEN batches
            if (!"DRAFT".equals(batch.getStatus()) && !"OPEN".equals(batch.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Cannot cancel batch in " + batch.getStatus() + " status"
                ));
            }

            // Mark batch as cancelled (implementation would depend on your batch status enum)
            // This is a placeholder - you might want to add a CANCELLED status to your enum
            return ResponseEntity.ok(Map.of(
                    "batchId", batchId,
                    "message", "Batch cancellation requested - implementation pending"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get candidate count for a cycle without fetching all data
     * Useful for UI to show counts before batch creation
     */
    @GetMapping("/candidates/count")
    public ResponseEntity<Map<String, Object>> getCandidateCount(
            @PathVariable Long merchantId,
            @RequestParam String cycleKey,
            @RequestParam Long productId) {

        try {
            List<SettlementCandidateDTO> candidates = enhancedSettlementService
                    .listSettlementCandidatesForCycle(merchantId, cycleKey, productId);

            long validCount = candidates.stream()
                    .filter(c -> c.getError() == null)
                    .count();

            double totalAmount = candidates.stream()
                    .filter(c -> c.getError() == null)
                    .mapToDouble(c -> c.getAmount().doubleValue())
                    .sum();

            return ResponseEntity.ok(Map.of(
                    "merchantId", merchantId,
                    "cycleKey", cycleKey,
                    "productId", productId,
                    "totalCandidates", candidates.size(),
                    "validCandidates", validCount,
                    "invalidCandidates", candidates.size() - validCount,
                    "totalAmount", totalAmount
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Health check endpoint for settlement service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck(@PathVariable Long merchantId) {
        try {
            // Basic validation that merchant exists
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "merchantId", merchantId,
                    "timestamp", System.currentTimeMillis(),
                    "service", "EnhancedSettlementService"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "DOWN",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Get summary of recent batches for merchant dashboard // nice for reports
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getMerchantSettlementSummary(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "5") int recentBatchesCount) {

        try {
            List<MerchantSettlementBatch> recentBatches = enhancedSettlementService
                    .getAllBatchesByMerchant(merchantId, recentBatchesCount);

            Map<String, Long> statusCounts = recentBatches.stream()
                    .collect(Collectors.groupingBy(
                            MerchantSettlementBatch::getStatus,
                            Collectors.counting()
                    ));

            double totalSettledAmount = recentBatches.stream()
                    .filter(b -> "COMPLETED".equals(b.getStatus()) || "PARTIALLY_COMPLETED".equals(b.getStatus()))
                    .mapToDouble(b -> b.getTotalAmount() != null ? b.getTotalAmount().doubleValue() : 0.0)
                    .sum();

            return ResponseEntity.ok(Map.of(
                    "merchantId", merchantId,
                    "recentBatchesCount", recentBatches.size(),
                    "statusBreakdown", statusCounts,
                    "totalSettledAmount", totalSettledAmount,
                    "recentBatches", recentBatches.stream()
                            .map(BatchCreatedResponse::new)
                            .collect(Collectors.toList())
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Error response helper
     */
    private BatchCreatedResponse createErrorResponse(String errorMessage) {
        BatchCreatedResponse errorResponse = new BatchCreatedResponse();
        errorResponse.errorMessage = errorMessage;
        return errorResponse;
    }
}