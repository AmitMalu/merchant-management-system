package com.project2.ism.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Service.PaymentVendorCryptoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/tools/encrypt")
public class EncryptionTestController {

    private final PaymentVendorCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public EncryptionTestController(PaymentVendorCryptoService cryptoService,
                                    ObjectMapper objectMapper) {
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    /**
     * Utility endpoint:
     * Send any JSON → returns vendor-encrypted string.
     */
    @PostMapping("/{vendorId}")
    public ResponseEntity<?> encryptForVendor(
            @PathVariable Long vendorId,
            @RequestBody Map<String, Object> body) {

        try {
            // Convert Map -> JSON string
            String jsonString = objectMapper.writeValueAsString(body);

            // Encrypt
            String encrypted = cryptoService.encryptForVendor(vendorId, jsonString);

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "vendorId", vendorId,
                            "encrypted", encrypted
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of(
                            "success", false,
                            "error", e.getMessage()
                    )
            );
        }
    }
}

