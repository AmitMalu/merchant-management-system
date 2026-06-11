package com.project2.ism.Controller;

import com.project2.ism.DTO.PaymentDTO.PaymentChargeRequestDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentChargeResponseDTO;
import com.project2.ism.DTO.ReportDTO.ApiResponse;
import com.project2.ism.Service.PaymentChargeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/payment-charges")
public class PaymentChargesController {

    private final PaymentChargeService paymentChargeService;

    public PaymentChargesController(PaymentChargeService paymentChargeService) {
        this.paymentChargeService = paymentChargeService;
    }

    // ================= CREATE =================

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentChargeResponseDTO>> create(
            @Valid @RequestBody PaymentChargeRequestDTO dto) {

        PaymentChargeResponseDTO response = paymentChargeService.createPaymentCharge(dto);

        ApiResponse<PaymentChargeResponseDTO> api = new ApiResponse<>(
                true,
                "Payout charge created successfully",
                response,
                null,
                LocalDateTime.now()
        );

        return ResponseEntity.status(201).body(api);
    }

    // ================= GET BY ID =================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentChargeResponseDTO>> getById(@PathVariable Long id) {

        PaymentChargeResponseDTO response = paymentChargeService.getPaymentChargeById(id);

        ApiResponse<PaymentChargeResponseDTO> api = new ApiResponse<>(
                true,
                "Payout charge retrieved",
                response,
                null,
                LocalDateTime.now()
        );

        return ResponseEntity.ok(api);
    }

    // ================= LIST / SEARCH =================

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String search) {

        Page<PaymentChargeResponseDTO> result =
                (search != null && !search.trim().isEmpty())
                        ? paymentChargeService.searchPayoutCharges(search, page, size, sortBy, sortDir)
                        : paymentChargeService.getAllPaymentCharges(page, size, sortBy, sortDir);

        // Pagination wrapper
        var pagination = new java.util.HashMap<String, Object>();
        pagination.put("content", result.getContent());
        pagination.put("totalItems", result.getTotalElements());
        pagination.put("totalPages", result.getTotalPages());
        pagination.put("currentPage", result.getNumber());
        pagination.put("pageSize", result.getSize());

        ApiResponse<Object> api = new ApiResponse<>(
                true,
                "Payout charges fetched",
                pagination,
                null,
                LocalDateTime.now()
        );

        return ResponseEntity.ok(api);
    }

    // ================= UPDATE =================

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentChargeResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody PaymentChargeRequestDTO dto) {

        PaymentChargeResponseDTO response = paymentChargeService.updatePaymentCharge(id, dto);

        ApiResponse<PaymentChargeResponseDTO> api = new ApiResponse<>(
                true,
                "Payout charge updated successfully",
                response,
                null,
                LocalDateTime.now()
        );

        return ResponseEntity.ok(api);
    }

    // ================= DELETE =================

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {

        paymentChargeService.deletePaymentCharge(id);

        ApiResponse<Void> api = new ApiResponse<>(
                true,
                "Payout charge deleted successfully",
                null,
                null,
                LocalDateTime.now()
        );

        return ResponseEntity.ok(api);
    }

    // ================= STATS =================

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> stats() {

        var stats = new java.util.HashMap<String, Object>();
        stats.put("totalModes", paymentChargeService.getTotalCount());
        stats.put("activeModes", paymentChargeService.getActiveCount());

        ApiResponse<Object> api = new ApiResponse<>(
                true,
                "Statistics loaded",
                stats,
                null,
                LocalDateTime.now()
        );

        return ResponseEntity.ok(api);
    }
}
