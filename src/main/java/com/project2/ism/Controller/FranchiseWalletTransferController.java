package com.project2.ism.Controller;

import com.project2.ism.DTO.MerchantListDTO;
import com.project2.ism.Service.FranchiseWalletTransferService;
import com.project2.ism.request.FranchiseWalletTransferRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Franchise-only "Push/Pull Wallet" (Credit/Debit Wallet) feature — a
 * franchise crediting or debiting one of its own merchants' wallets. Every
 * endpoint here resolves which franchise is calling from the authenticated
 * session (see FranchiseWalletTransferService), never from a client-supplied
 * id, so a franchise can only ever act on its own wallet and its own
 * merchants.
 */
@RestController
@RequestMapping("/franchise-wallet")
@PreAuthorize("hasRole('FRANCHISE')")
public class FranchiseWalletTransferController {

    private final FranchiseWalletTransferService service;

    public FranchiseWalletTransferController(FranchiseWalletTransferService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            return ResponseEntity.ok(service.getProfile());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "message", e.getMessage()));
        }
    }

    @GetMapping("/merchants")
    public ResponseEntity<?> getMyMerchants() {
        try {
            List<MerchantListDTO> merchants = service.getOwnMerchants();
            return ResponseEntity.ok(merchants);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "message", e.getMessage()));
        }
    }

    @PostMapping("/merchant/{merchantId}/transfer")
    public ResponseEntity<?> transfer(@PathVariable Long merchantId, @RequestBody FranchiseWalletTransferRequest request) {
        try {
            Map<String, Object> result = service.transfer(merchantId, request.getAction(), request.getAmount(), request.getRemark());
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "message", e.getMessage()));
        }
    }
}
