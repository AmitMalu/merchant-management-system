package com.project2.ism.Controller;

import com.project2.ism.Service.PushWalletService;
import com.project2.ism.request.PushWalletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/push-wallet")
public class PushWalletController {

    private final PushWalletService pushWalletService;

    public PushWalletController(PushWalletService pushWalletService) {
        this.pushWalletService = pushWalletService;
    }

    @PostMapping("/to-merchant")
    public ResponseEntity<?> pushWallet(@RequestBody PushWalletRequest request) {
        try {
            pushWalletService.pushWalletFromFranchiseToMerchant(request.getFranchiseId(), request.getMerchantId(), request.getAmount());
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Amount transferred successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage()
            ));
        }
    }
}
