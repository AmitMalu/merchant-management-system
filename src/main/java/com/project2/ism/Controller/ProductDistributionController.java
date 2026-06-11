package com.project2.ism.Controller;


import com.project2.ism.DTO.AssignMerchantRequest;
import com.project2.ism.DTO.ProductDistributionDTO;
import com.project2.ism.Service.ProductDistributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product-distribution")
public class ProductDistributionController {


    public final ProductDistributionService productDistributionService;

    public ProductDistributionController(ProductDistributionService productDistributionService) {
        this.productDistributionService = productDistributionService;
    }


    @PostMapping("/assign-merchant")
    public ResponseEntity<?> assignDevicesToMerchant(@RequestBody AssignMerchantRequest request) {
        productDistributionService.assignSerialsToMerchant(request);
        return ResponseEntity.ok("Assigned " + request.getSelectedDeviceIds().size() + " devices to merchant " + request.getMerchantId());
    }

    /**
     * Patch only receivedDate
     */
    @PatchMapping("/{distributionId}/mark-received")
    public ResponseEntity<ProductDistributionDTO> markAsReceived(@PathVariable Long distributionId) {
        ProductDistributionDTO updated = productDistributionService.markAsReceived(distributionId);
        return ResponseEntity.ok(updated);
    }

    /**
     * Get all distributions
     */
    @GetMapping
    public ResponseEntity<List<ProductDistributionDTO>> getAll() {
        return ResponseEntity.ok(productDistributionService.getAll());
    }

    /**
     * Get distributions by franchise
     */
    @GetMapping("/franchise/{franchiseId}")
    public ResponseEntity<List<ProductDistributionDTO>> getByFranchise(@PathVariable Long franchiseId) {
        return ResponseEntity.ok(productDistributionService.getByFranchise(franchiseId));
    }

    /**
     * Get distributions by merchant
     */
    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<List<ProductDistributionDTO>> getByMerchant(@PathVariable Long merchantId) {
        return ResponseEntity.ok(productDistributionService.getByMerchant(merchantId));
    }
    /**
     * Delete a distribution (and unlink serials)
     */
    @DeleteMapping("/{distributionId}")
    public ResponseEntity<?> delete(@PathVariable Long distributionId) {
        productDistributionService.delete(distributionId);
        return ResponseEntity.ok("Distribution " + distributionId + " deleted successfully");
    }


}
