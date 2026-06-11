package com.project2.ism.Controller;


import com.project2.ism.DTO.PayoutDTO.TransferRequest;
import com.project2.ism.DTO.PayoutDTO.VerifyAndAddBankRequest;
import com.project2.ism.Exception.BankVerificationException;
import com.project2.ism.Exception.InsufficientBalanceException;
import com.project2.ism.Model.Payout.PayoutBanks;
import com.project2.ism.Service.PayoutBankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payout")
public class PayoutBankController {


    private final PayoutBankService payoutBankService;

    public PayoutBankController(PayoutBankService payoutBankService) {
        this.payoutBankService = payoutBankService;
    }

    @GetMapping("/banks/{customerId}") // working
    public ResponseEntity<?> getBanks(@PathVariable Long customerId,
                                      @RequestParam String customerType) {
        try {
            List<PayoutBanks> banks = payoutBankService.getBanksByCustomer(customerType, customerId);
            return ResponseEntity.ok(banks);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/verify-and-add-bank/{customerId}")
    public ResponseEntity<?> verifyAndAddBank(@PathVariable Long customerId,
                                              @RequestBody VerifyAndAddBankRequest request) {
        try {
            // Call service method that handles both verification and adding
            PayoutBanks savedBank = payoutBankService.verifyAndAddBank(customerId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", savedBank);
            response.put("message", "Bank details verified and added successfully. ₹1 debited for verification.");

            return ResponseEntity.ok(response);
        } catch (InsufficientBalanceException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response); // 402
        } catch (BankVerificationException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response); // 422
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @DeleteMapping("/delete-bank/{bankId}") //- working
    public ResponseEntity<?> deleteBank(@PathVariable Long bankId) {
        try {
            payoutBankService.deleteBank(bankId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Bank deleted successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/transfer") // not checked
    public ResponseEntity<?> initiateTransfer(@RequestBody TransferRequest request) {
        try {
            // Get bank details
            PayoutBanks bank = payoutBankService.getBankById(request.getBankId());

            if (!bank.isVerified()) {
                throw new Exception("Bank is not verified. Please verify before transfer.");
            }

            // Here you would integrate with payment gateway for actual transfer
            // For now, simulate transfer
            Map<String, Object> transferResponse = simulateTransfer(request, bank);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", transferResponse);
            response.put("message", "Transfer initiated successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    private Map<String, Object> simulateTransfer(TransferRequest request, PayoutBanks bank) {
        Map<String, Object> transferResponse = new HashMap<>();
        transferResponse.put("transactionId", "TXN" + System.currentTimeMillis());
        transferResponse.put("amount", request.getAmount());
        transferResponse.put("transferType", request.getTransferType());
        transferResponse.put("bankName", bank.getBankName());
        transferResponse.put("accountNumber", bank.getAccountNumber());
        transferResponse.put("status", "PENDING");

        // Set estimated time based on transfer type
        switch (request.getTransferType()) {
            case "IMPS":
                transferResponse.put("estimatedTime", "Instant");
                break;
            case "NEFT":
                transferResponse.put("estimatedTime", "2-3 hours");
                break;
            case "RTGS":
                transferResponse.put("estimatedTime", "30 minutes");
                break;
        }

        return transferResponse;
    }
}
