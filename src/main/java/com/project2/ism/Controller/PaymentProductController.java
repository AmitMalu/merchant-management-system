package com.project2.ism.Controller;

import com.project2.ism.DTO.PaymentDTO.PaymentProductIDNameDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentProductRequestDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentProductResponseDTO;
import com.project2.ism.DTO.ReportDTO.ApiResponse;
import com.project2.ism.Service.PaymentProductService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/payment-products")
public class PaymentProductController {

    private final PaymentProductService paymentProductService;

    public PaymentProductController(PaymentProductService paymentProductService) {
        this.paymentProductService = paymentProductService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentProductResponseDTO>> create(
            @RequestBody PaymentProductRequestDTO dto) {

        var created = paymentProductService.create(dto);
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", created, null, LocalDateTime.now()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentProductResponseDTO>> update(
            @PathVariable Long id,
            @RequestBody PaymentProductRequestDTO dto) {

        var updated = paymentProductService.update(id, dto);
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", updated, null, LocalDateTime.now()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentProductResponseDTO>> get(@PathVariable Long id) {
        var prod = paymentProductService.getById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Fetched", prod, null, LocalDateTime.now()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentProductResponseDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Pageable pageable = PageRequest.of(
                page, size,
                sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending()
        );

        return ResponseEntity.ok(new ApiResponse<>(true, "Fetched all", paymentProductService.getAll(pageable), null, LocalDateTime.now()));
    }



    @GetMapping("/id-name")
    public ResponseEntity<List<PaymentProductIDNameDTO>> getProductsIdName() {
        return ResponseEntity.ok(paymentProductService.getAllProductsIdName());
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        paymentProductService.deleteById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null, null, LocalDateTime.now()));
    }
}
