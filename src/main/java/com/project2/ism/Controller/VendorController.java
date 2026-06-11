package com.project2.ism.Controller;

import com.project2.ism.DTO.Vendor.VendorIDNameDTO;
import com.project2.ism.DTO.Vendor.VendorStatsDTO;
import com.project2.ism.Model.Vendor.Vendor;
import com.project2.ism.Service.VendorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/vendors")
public class VendorController {


    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    // 🔹 Create Vendor
    @PostMapping
    public ResponseEntity<?> createVendor(@Valid @RequestBody Vendor vendor) {
            Vendor savedVendor = vendorService.createVendor(vendor);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedVendor);
    }

    // 🔹 Get All Vendors
    @GetMapping
    public ResponseEntity<List<Vendor>> getAllVendors() {
        return ResponseEntity.ok(vendorService.getAllVendors());
    }

    // 🔹 Get Vendor by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getVendorById(@PathVariable Long id) {
            Vendor vendor = vendorService.getVendorById(id);
            return ResponseEntity.ok(vendor);
    }

    // get all vendor with just id and name
    @GetMapping("/id_name")
    public ResponseEntity<List<VendorIDNameDTO>> getAllVendorsIdAndName(){
        return ResponseEntity.ok(vendorService.getAllVendorsIdAndName());
    }

    // 🔹 Update Vendor
    @PutMapping("/{id}")
    public ResponseEntity<?> updateVendor(@PathVariable Long id, @Valid @RequestBody Vendor vendor) {
            Vendor updatedVendor = vendorService.updateVendor(id, vendor);
            return ResponseEntity.ok(updatedVendor);
    }

    // 🔹 Delete Vendor
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVendor(@PathVariable Long id) {
            vendorService.deleteVendor(id);
            return ResponseEntity.ok("Vendor deleted successfully.");
    }

    @GetMapping("/stats")
    public ResponseEntity<VendorStatsDTO> getVendorStats() {
        return ResponseEntity.ok(vendorService.getVendorStats());
    }


    @GetMapping("/active")
    public ResponseEntity<List<Vendor>> getActiveVendors() {
        return ResponseEntity.ok(vendorService.getAllActiveVendors());
    }
}



