package com.project2.ism.Controller;

import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Service.FranchiseTransDetService;
import com.project2.ism.Service.MerchantTransDetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/merchant-transaction-details")
public class MerchantTransDetController {

    private final MerchantTransDetService merchantTransDetService;

    @Autowired
    public MerchantTransDetController(MerchantTransDetService merchantTransDetService) {
        this.merchantTransDetService = merchantTransDetService;
    }

    // 🔹 Get all transactions
    @GetMapping
    public ResponseEntity<List<MerchantTransactionDetails>> getAllTransactions() {
        return ResponseEntity.ok(merchantTransDetService.getAllFranchiseTransactionDetails());
    }

    // 🔹 Get transaction by DB id
    @GetMapping("/{id}")
    public ResponseEntity<MerchantTransactionDetails> getTransactionById(@PathVariable Long id) {
        return merchantTransDetService.getFranchiseTransactionDetailsById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 🔹 Get transaction by transactionId
    @GetMapping("/by-transactionId/{transactionId}")
    public ResponseEntity<MerchantTransactionDetails> getFranchTransDetById(@PathVariable Long transactionId) {
        return merchantTransDetService.getFranchiseTransactionDetailsByTransactionDetailsId(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 🔹 Create transaction
    @PostMapping
    public ResponseEntity<MerchantTransactionDetails> createTransaction(@RequestBody MerchantTransactionDetails transaction) {
        return ResponseEntity.ok(merchantTransDetService.saveTransaction(transaction));
    }

    // 🔹 Delete transaction
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        merchantTransDetService.deleteTransactionDetails(id);
        return ResponseEntity.noContent().build();
    }
}
