package com.project2.ism.Controller;

import com.project2.ism.DTO.PaymentDTO.PaymentVendorRequestDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentVendorResponseDTO;
import com.project2.ism.DTO.ReportDTO.ApiResponse;
import com.project2.ism.DTO.Vendor.VendorIDNameDTO;
import com.project2.ism.Service.PaymentVendorService;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/payment-vendors")
public class PaymentVendorController {

    private final PaymentVendorService paymentVendorService;

    public PaymentVendorController(PaymentVendorService paymentVendorService) {
        this.paymentVendorService = paymentVendorService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentVendorResponseDTO>> create(
            @RequestBody PaymentVendorRequestDTO dto) {

        return ResponseEntity.ok(new ApiResponse<>(
                true, "Created", paymentVendorService.create(dto), null, LocalDateTime.now()
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentVendorResponseDTO>> update(
            @PathVariable Long id,
            @RequestBody PaymentVendorRequestDTO dto) {

        return ResponseEntity.ok(new ApiResponse<>(
                true, "Updated", paymentVendorService.update(id, dto), null, LocalDateTime.now()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentVendorResponseDTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(
                true, "Fetched", paymentVendorService.getById(id), null, LocalDateTime.now()
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentVendorResponseDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {

        Pageable pageable = PageRequest.of(
                page, size,
                sortDir.equalsIgnoreCase("asc") ?
                        Sort.by(sortBy).ascending() : Sort.by(sortBy).descending()
        );

        return ResponseEntity.ok(new ApiResponse<>(
                true, "Fetched all", paymentVendorService.getAll(pageable), null, LocalDateTime.now()
        ));
    }


    @GetMapping("/id-name")
    public ResponseEntity<List<VendorIDNameDTO>> getVendorsIdName() {
        return ResponseEntity.ok(paymentVendorService.getAllVendorsIdAndName());
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {

        paymentVendorService.deleteById(id);

        return ResponseEntity.ok(new ApiResponse<>(
                true, "Deleted", null, null, LocalDateTime.now()
        ));
    }
}
