package com.project2.ism.Controller;

import com.project2.ism.Service.CallerIdentityService;
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
    private final CallerIdentityService callerIdentity;

    public PushWalletController(PushWalletService pushWalletService,
                                CallerIdentityService callerIdentity) {
        this.pushWalletService = pushWalletService;
        this.callerIdentity = callerIdentity;
    }

    @PostMapping("/to-merchant")
    public ResponseEntity<?> pushWallet(@RequestBody PushWalletRequest request) {
        // franchiseId comes from the request body, so confirm the caller really
        // is that franchise. Done outside the try so it surfaces as a 403.
        callerIdentity.requireOwner("FRANCHISE", request.getFranchiseId());

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
