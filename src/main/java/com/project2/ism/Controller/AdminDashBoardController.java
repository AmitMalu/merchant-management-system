package com.project2.ism.Controller;

import com.project2.ism.Service.AdminDashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin-dashboard")
public class AdminDashBoardController {
    private static final Logger logger = LoggerFactory.getLogger(AdminDashBoardController.class);

    private final AdminDashboardService adminDashboardService;

//     Optional: Keep individual services if you need them for other operations
//    private final FranchiseService franchiseService;
//    private final PricingSchemeService pricingSchemeService;
//    private final VendorService vendorService;
//    private final StatsService statsService;
//    private final ProductService productService;

    public AdminDashBoardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;

    }

    /**
     * Get all dashboard statistics in a single consolidated response
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getAllDashboardStats() {
        try {
            logger.debug("REST request to get all dashboard statistics");

            Map<String, Object> allStats = adminDashboardService.getAllDashboardStats();

            return ResponseEntity.ok(allStats);
        } catch (Exception e) {
            logger.error("Error fetching all dashboard statistics: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch dashboard statistics");
            error.put("message", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Keep individual endpoints if you still need them for specific use cases
//    @GetMapping("/franchises-merchants")
//    public ResponseEntity<FranchiseMerchantStatsDTO> getStats() {
//        return ResponseEntity.ok(franchiseService.getStats());
//    }
//
//    @GetMapping("/transactions-stats")
//    public ResponseEntity<InventoryTransactionStatsDTO> getTransactionStats() {
//        return ResponseEntity.ok(statsService.getTransactionStats());
//    }
//
//    @GetMapping("/pricing-scheme-stats")
//    public ResponseEntity<?> getPricingSchemeStats() {
//        try {
//            logger.debug("REST request to get pricing scheme statistics");
//
//            Map<String, Object> stats = new HashMap<>();
//            stats.put("totalSchemes", pricingSchemeService.getTotalSchemesCount());
//            stats.put("totalFranchiseSchemes", pricingSchemeService.getAllSchemesForCustomerType("franchise").size());
//            stats.put("totalDirectMerchantSchemes", pricingSchemeService.getAllSchemesForCustomerType("direct_merchant").size());
//
//            return ResponseEntity.ok(stats);
//        } catch (Exception e) {
//            logger.error("Error fetching pricing scheme statistics: {}", e.getMessage());
//            Map<String, String> error = new HashMap<>();
//            error.put("error", "Failed to fetch statistics");
//            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }
//
//    @GetMapping("/vendor-stats")
//    public ResponseEntity<VendorStatsDTO> getVendorStats() {
//        return ResponseEntity.ok(vendorService.getVendorStats());
//    }
//
//    @GetMapping("/product-stats")
//    public ResponseEntity<Map<String, Object>> getProductStats() {
//        Map<String, Object> stats = productService.getProductStats();
//        return ResponseEntity.ok(stats);
//    }
}