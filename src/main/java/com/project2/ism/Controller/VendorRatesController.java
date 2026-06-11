package com.project2.ism.Controller;

import com.project2.ism.DTO.Vendor.VendorRatesDTO;
import com.project2.ism.Model.Vendor.VendorRates;
import com.project2.ism.Service.VendorRatesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vendor-rates")
public class VendorRatesController {

    private final VendorRatesService vendorRatesService;

    public VendorRatesController(VendorRatesService vendorRatesService) {
        this.vendorRatesService = vendorRatesService;
    }

    @PostMapping
    public ResponseEntity<VendorRatesDTO> createVendorRate(@Valid @RequestBody VendorRates vendorRates) {
        VendorRates saved = vendorRatesService.createVendorRates(vendorRates);
        return ResponseEntity.ok(VendorRatesDTO.fromEntity(saved));
    }

    @GetMapping
    public ResponseEntity<List<VendorRatesDTO>> getAllVendorRates() {
        List<VendorRatesDTO> result = vendorRatesService.getAllVendorRates()
                .stream()
                .map(VendorRatesDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendorRatesDTO> getVendorRateById(@PathVariable Long id) {
        VendorRates found = vendorRatesService.getVendorRatesById(id);
        return ResponseEntity.ok(VendorRatesDTO.fromEntity(found));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VendorRatesDTO> updateVendorRate(
            @PathVariable Long id,
            @Valid @RequestBody VendorRates vendorRates
    ) {
        VendorRates updated = vendorRatesService.updateVendorRates(id, vendorRates);
        return ResponseEntity.ok(VendorRatesDTO.fromEntity(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVendorRate(@PathVariable Long id) {
        vendorRatesService.deleteVendorRates(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<VendorRatesDTO> getByProductId(@PathVariable Long productId) {
        VendorRates found = vendorRatesService.getRatesByProductId(productId);
        return ResponseEntity.ok(VendorRatesDTO.fromEntity(found));
    }

}
