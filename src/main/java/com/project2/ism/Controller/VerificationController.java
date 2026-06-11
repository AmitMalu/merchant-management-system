package com.project2.ism.Controller;

import com.project2.ism.Service.VerificationService;
import com.project2.ism.request.BankVerificationRequest;
import com.project2.ism.request.PanVerificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    // PAN Verification
    @PostMapping("/pan")
    public ResponseEntity<?> verifyPan(@RequestBody PanVerificationRequest request) {

        return ResponseEntity.ok(
                verificationService.verifyPan(request)
        );
    }

    // Bank Verification
    @PostMapping("/bank")
    public ResponseEntity<?> verifyBank(@RequestBody BankVerificationRequest request) {

        return ResponseEntity.ok(
                verificationService.verifyBank(request)
        );
    }
}
