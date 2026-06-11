package com.project2.ism.Controller;

import com.project2.ism.DTO.ReportDTO.ApiResponse;
import com.project2.ism.Model.Payment.PaymentMode;
import com.project2.ism.Service.PaymentModeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/payment-modes")
public class PaymentModeController {

    private final PaymentModeService paymentModeService;

    public PaymentModeController(PaymentModeService paymentModeService) {
        this.paymentModeService = paymentModeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentMode>> createMode(@RequestBody PaymentMode mode) {
        PaymentMode created = paymentModeService.createMode(mode);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Mode created", created, null, LocalDateTime.now())
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentMode>> updateMode(
            @PathVariable Long id,
            @RequestBody PaymentMode mode) {

        PaymentMode updated = paymentModeService.updateMode(id, mode);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Mode updated", updated, null, LocalDateTime.now())
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentMode>>> getAllModes() {
        List<PaymentMode> modes = paymentModeService.getAllModes();
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Modes fetched", modes, null, LocalDateTime.now())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentMode>> getModeById(@PathVariable Long id) {
        PaymentMode mode = paymentModeService.getModeById(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Mode fetched", mode, null, LocalDateTime.now())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMode(@PathVariable Long id) {
        paymentModeService.deleteMode(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Mode deleted", null, null, LocalDateTime.now())
        );
    }
}
