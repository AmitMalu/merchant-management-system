package com.project2.ism.Controller;

import com.project2.ism.DTO.PricingSchemeDTO;
import com.project2.ism.DTO.PricingSchemesDTOS.PricingSchemesResponseDTO;
import com.project2.ism.Service.PricingSchemeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.project2.ism.Model.PricingScheme.PricingScheme;



import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pricing-schemes")
public class PricingSchemeController {

    private static final Logger logger = LoggerFactory.getLogger(PricingSchemeController.class);

    private final PricingSchemeService pricingSchemeService;


    @Autowired
    public PricingSchemeController(PricingSchemeService pricingSchemeService) {
        this.pricingSchemeService = pricingSchemeService;
    }

    /**
     * Create a new pricing scheme
     */
    @PostMapping
    public ResponseEntity<?> createPricingScheme(@Valid @RequestBody PricingScheme pricingScheme) {
        try {
            logger.info("REST request to create Pricing Scheme: {}", pricingScheme.getSchemeCode());
            PricingScheme createdScheme = pricingSchemeService.createPricingScheme(pricingScheme);
            return new ResponseEntity<>(createdScheme, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            logger.error("Error creating pricing scheme: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Unexpected error creating pricing scheme: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get all pricing schemes with pagination and sorting
     */
    @GetMapping
    public ResponseEntity<?> getAllPricingSchemes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "schemeCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        try {
            logger.debug("REST request to get all Pricing Schemes - page: {}, size: {}, sortBy: {}, sortDir: {}",
                    page, size, sortBy, sortDir);

            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);
            Page<PricingScheme> schemes = pricingSchemeService.getAllPricingSchemes(pageable);

            return ResponseEntity.ok(schemes.map(PricingSchemeDTO::fromEntity));
        } catch (Exception e) {
            logger.error("Error fetching pricing schemes: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch pricing schemes");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get pricing scheme by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPricingSchemeById(@PathVariable Long id) {
        try {
            logger.debug("REST request to get Pricing Scheme by ID: {}", id);
            PricingScheme scheme = pricingSchemeService.getPricingSchemeById(id);
            return ResponseEntity.ok(PricingSchemeDTO.fromEntity(scheme));
        } catch (RuntimeException e) {
            logger.error("Pricing scheme not found: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Error fetching pricing scheme: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get pricing scheme by code
     */
    @GetMapping("/code/{schemeCode}")
    public ResponseEntity<?> getPricingSchemeByCode(@PathVariable String schemeCode) {
        try {
            logger.debug("REST request to get Pricing Scheme by code: {}", schemeCode);
            PricingScheme scheme = pricingSchemeService.getPricingSchemeByCode(schemeCode);
            return ResponseEntity.ok(PricingSchemeDTO.fromEntity(scheme));
        } catch (RuntimeException e) {
            logger.error("Pricing scheme not found: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Error fetching pricing scheme: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Update pricing scheme
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePricingScheme(@PathVariable Long id,
                                                 @Valid @RequestBody PricingScheme pricingSchemeDetails) {
        try {
            logger.info("REST request to update Pricing Scheme ID: {}", id);
            PricingScheme updatedScheme = pricingSchemeService.updatePricingScheme(id, pricingSchemeDetails);
            return ResponseEntity.ok(PricingSchemeDTO.fromEntity(updatedScheme));
        } catch (RuntimeException e) {
            logger.error("Error updating pricing scheme: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Unexpected error updating pricing scheme: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Delete pricing scheme
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePricingScheme(@PathVariable Long id) {
        try {
            logger.info("REST request to delete Pricing Scheme ID: {}", id);
            pricingSchemeService.deletePricingScheme(id);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Pricing scheme deleted successfully");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.error("Error deleting pricing scheme: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            logger.error("Unexpected error deleting pricing scheme: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Search pricing schemes
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchPricingSchemes(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "schemeCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        try {
            logger.debug("REST request to search Pricing Schemes with query: {}", q);

            Sort sort = sortDir.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);
            Page<PricingScheme> schemes = pricingSchemeService.searchPricingSchemes(q, pageable);

            return ResponseEntity.ok(schemes);
        } catch (Exception e) {
            logger.error("Error searching pricing schemes: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to search pricing schemes");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get schemes by customer type - no one using , so removed , changing return method of method getAllSchemesForCustomerType
     */
//    @GetMapping("/customer-type/{customerType}")
//    public ResponseEntity<?> getSchemesByCustomerType(@PathVariable String customerType) {
//        try {
//            logger.debug("REST request to get Pricing Schemes by customer type: {}", customerType);
//            List<PricingScheme> schemes = pricingSchemeService.getAllSchemesForCustomerType(customerType);
//            return ResponseEntity.ok(schemes);
//        } catch (Exception e) {
//            logger.error("Error fetching schemes by customer type: {}", e.getMessage());
//            Map<String, String> error = new HashMap<>();
//            error.put("error", "Failed to fetch schemes");
//            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }

    /**
     * Check if scheme code exists
     */
    @GetMapping("/exists/{schemeCode}")
    public ResponseEntity<Map<String, Boolean>> checkSchemeCodeExists(@PathVariable String schemeCode) {
        logger.debug("REST request to check if scheme code exists: {}", schemeCode);
        boolean exists = pricingSchemeService.schemeCodeExists(schemeCode);

        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return ResponseEntity.ok(response);
    }

    /**
     * Get dashboard statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getPricingSchemeStats() {
        try {
            logger.debug("REST request to get pricing scheme statistics");

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalSchemes", pricingSchemeService.getTotalSchemesCount());// Usage
            Map<String, Long> counts = pricingSchemeService.getSchemeCountsByCustomerType();
            stats.put("totalFranchiseSchemes", counts.getOrDefault("franchise", 0L));
            stats.put("totalDirectMerchantSchemes", counts.getOrDefault("direct_merchant", 0L));

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error fetching pricing scheme statistics: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch statistics");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generate next available scheme code
     */
    @GetMapping("/generate-code")
    public ResponseEntity<?> generateSchemeCode() {
        try {
            logger.debug("REST request to generate new scheme code");
            String newSchemeCode = pricingSchemeService.generateNextSchemeCode();

            Map<String, String> response = new HashMap<>();
            response.put("schemeCode", newSchemeCode);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error generating scheme code: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate scheme code");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }


    @GetMapping("/valid-pricing-scheme")
    public ResponseEntity<PricingSchemesResponseDTO> validPricingScheme(@RequestParam Long productId, String productCategory,String customerType){
        PricingSchemesResponseDTO validScheme = pricingSchemeService.getValidPricingScheme(productId,productCategory,customerType);
        return ResponseEntity.ok(validScheme);
    }
}