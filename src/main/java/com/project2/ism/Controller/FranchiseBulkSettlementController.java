package com.project2.ism.Controller;

import com.project2.ism.DTO.TempDTOs.*;
import com.project2.ism.Model.FranchiseSettlementBatch;
import com.project2.ism.Service.FranchiseBulkSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/franchises/{franchiseId}/bulk-settlement")
public class FranchiseBulkSettlementController {

    private final FranchiseBulkSettlementService franchiseBulkSettlementService;

    public FranchiseBulkSettlementController(FranchiseBulkSettlementService franchiseBulkSettlementService) {
        this.franchiseBulkSettlementService = franchiseBulkSettlementService;
    }

    /**
     * Get available merchants under franchise for given cycle and product
     */
    @GetMapping("/merchants/available")
    public ResponseEntity<List<FranchiseMerchantOption>> getAvailableMerchants(
            @PathVariable Long franchiseId,
            @RequestParam String cycleKey,
            @RequestParam Long productId) { // NEW: Required product_id parameter
        try {
            List<FranchiseMerchantOption> merchants = franchiseBulkSettlementService
                    .getAvailableMerchantsForFranchise(franchiseId, cycleKey, productId);
            return ResponseEntity.ok(merchants);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create batch with selective merchants
     */
    @PostMapping("/batches/selective")
    public ResponseEntity<FranchiseBatchResponse> createSelectiveBatch(
            @PathVariable Long franchiseId,
            @RequestParam String cycleKey,
            @RequestParam Long productId, // NEW: Required product_id parameter
            @RequestBody List<Long> merchantIds) {
        try {
            // ✅ Extract current user (email/username) from Spring Security
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String createdBy = (authentication != null) ? authentication.getName() : "SYSTEM";
            FranchiseSettlementBatch batch = franchiseBulkSettlementService
                    .createSelectiveFranchiseBatch(franchiseId,productId, cycleKey, merchantIds, createdBy);
            return ResponseEntity.ok(new FranchiseBatchResponse(batch));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorFranchiseResponse(e.getMessage()));
        }
    }

    /**
     * Create batch with all merchants
     */
    @PostMapping("/batches/full")
    public ResponseEntity<FranchiseBatchResponse> createFullBatch(
            @PathVariable Long franchiseId,
            @RequestParam String cycleKey,
            @RequestParam Long productId) { // NEW: Required product_id parameter
        try {
            // ✅ Extract current user (email/username) from Spring Security
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String createdBy = (authentication != null) ? authentication.getName() : "SYSTEM";
            FranchiseSettlementBatch batch = franchiseBulkSettlementService
                    .createFullFranchiseBatch(franchiseId,productId, cycleKey, createdBy);
            return ResponseEntity.ok(new FranchiseBatchResponse(batch));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorFranchiseResponse(e.getMessage()));
        }
    }

    /**
     * Update merchant selection inside batch
     */
    @PutMapping("/batches/{batchId}/merchants")
    public ResponseEntity<FranchiseBatchResponse> updateMerchantSelection(
            @PathVariable Long franchiseId,
            @PathVariable Long batchId,
            @RequestBody List<Long> merchantIds) {
        try {
            FranchiseSettlementBatch batch = franchiseBulkSettlementService
                    .updateMerchantSelection(batchId, merchantIds);
            return ResponseEntity.ok(new FranchiseBatchResponse(batch));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorFranchiseResponse(e.getMessage()));
        }
    }

    /**
     * Get candidates (transactions) for a specific merchant inside franchise batch
     */
    @GetMapping("/batches/{batchId}/merchants/{merchantId}/candidates")
    public ResponseEntity<List<SettlementCandidateDTO>> getMerchantCandidates(
            @PathVariable Long franchiseId,
            @PathVariable Long batchId,
            @PathVariable Long merchantId,
            @RequestParam String cycleKey,
            @RequestParam Long productId) { // NEW: Required product_id parameter
        try {
            List<SettlementCandidateDTO> candidates = franchiseBulkSettlementService
                    .listSettlementCandidatesForMerchant(franchiseId, batchId, merchantId, cycleKey, productId);
            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }


    /**
     * NEW: Process franchise batch with merchant-specific transactions
     * Body format: { "merchantId1": ["tx1", "tx2"], "merchantId2": ["tx3", "tx4"] }
     */
    @PostMapping("/batches/{batchId}/process-with-transactions")
    public ResponseEntity<Map<String, Object>> processWithTransactions(
            @PathVariable Long franchiseId,
            @PathVariable Long batchId,
            @RequestBody Map<Long, List<String>> merchantTransactionsMap) {
        try {
            franchiseBulkSettlementService.processSelectedMerchantsWithTransactions(batchId, merchantTransactionsMap);

            return ResponseEntity.accepted().body(Map.of(
                    "batchId", batchId,
                    "status", "PROCESSING",
                    "merchants", merchantTransactionsMap.size(),
                    "totalTransactions", merchantTransactionsMap.values().stream()
                            .mapToInt(List::size).sum(),
                    "message", "Franchise batch processing started with custom transactions"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * Get batch progress
     */
    @GetMapping("/batches/{batchId}/progress")
    public ResponseEntity<BatchProgressDTO> getBatchProgress(
            @PathVariable Long franchiseId,
            @PathVariable Long batchId) {
        try {
            BatchProgressDTO progress = franchiseBulkSettlementService.getBatchProgress(batchId);
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get batch details
     */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<FranchiseBatchResponse> getBatchDetails(
            @PathVariable Long franchiseId,
            @PathVariable Long batchId) {
        try {
            FranchiseSettlementBatch batch = franchiseBulkSettlementService.getBatchById(batchId);
            return ResponseEntity.ok(new FranchiseBatchResponse(batch));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorFranchiseResponse(e.getMessage()));
        }
    }

    /**
     * Get all batches for franchise
     */
    @GetMapping("/batches")
    public ResponseEntity<List<FranchiseBatchResponse>> getAllBatches(
            @PathVariable Long franchiseId) {
        try {
            List<FranchiseSettlementBatch> batches = franchiseBulkSettlementService.getAllBatches(franchiseId);
            List<FranchiseBatchResponse> responses = batches.stream()
                    .map(FranchiseBatchResponse::new)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private FranchiseBatchResponse createErrorFranchiseResponse(String errorMessage) {
        FranchiseBatchResponse errorResponse = new FranchiseBatchResponse();
        errorResponse.setErrorMessage(errorMessage);
        return errorResponse;
    }
}