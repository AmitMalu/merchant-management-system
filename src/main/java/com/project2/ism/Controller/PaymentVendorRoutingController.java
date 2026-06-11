package com.project2.ism.Controller;


import com.project2.ism.DTO.PaymentDTO.PaymentVendorRoutingDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentVendorRoutingRequest;
import com.project2.ism.DTO.ReportDTO.ApiResponse;
import com.project2.ism.Service.PaymentVendorRoutingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/payment-vendor-routing")
public class PaymentVendorRoutingController {

    private final PaymentVendorRoutingService paymentVendorRoutingService;

    public PaymentVendorRoutingController(PaymentVendorRoutingService paymentVendorRoutingService) {
        this.paymentVendorRoutingService = paymentVendorRoutingService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentVendorRoutingDTO>> createRouting(
            @Valid @RequestBody PaymentVendorRoutingRequest request) {

        PaymentVendorRoutingDTO created = paymentVendorRoutingService.createRouting(request);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Routing created successfully", created, null, LocalDateTime.now())
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentVendorRoutingDTO>> updateRouting(
            @PathVariable Long id,
            @Valid @RequestBody PaymentVendorRoutingRequest request) {

        PaymentVendorRoutingDTO updated = paymentVendorRoutingService.updateRouting(id, request);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Routing updated successfully", updated, null, LocalDateTime.now())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentVendorRoutingDTO>> getRoutingById(@PathVariable Long id) {

        PaymentVendorRoutingDTO dto = paymentVendorRoutingService.getRoutingById(id);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Routing fetched", dto, null, LocalDateTime.now())
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentVendorRoutingDTO>>> getAllRoutings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending()
        );

        Page<PaymentVendorRoutingDTO> routings = paymentVendorRoutingService.getAllRoutings(pageable);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Routings fetched", routings, null, LocalDateTime.now())
        );
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<PaymentVendorRoutingDTO>> getRoutingByProductId(@PathVariable Long productId) {

        PaymentVendorRoutingDTO dto = paymentVendorRoutingService.getRoutingByProductId(productId);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Routing fetched for payout product", dto, null, LocalDateTime.now())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRouting(@PathVariable Long id) {

        paymentVendorRoutingService.deleteRouting(id);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Routing deleted", null, null, LocalDateTime.now())
        );
    }
}



