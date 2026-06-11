package com.project2.ism.Controller;


import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Service.FranchiseTransDetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/franchise-transaction-details")
public class FranchiseTransDetController {


    private final FranchiseTransDetService franchiseTransDetService;

    public FranchiseTransDetController(FranchiseTransDetService franchiseTransDetService) {
        this.franchiseTransDetService = franchiseTransDetService;
    }

    // 🔹 Get all transactions
    @GetMapping
    public ResponseEntity<List<FranchiseTransactionDetails>> getAllTransactions() {
        return ResponseEntity.ok(franchiseTransDetService.getAllFranchiseTransactionDetails());
    }

    // 🔹 Get transaction by DB id
    @GetMapping("/{id}")
    public ResponseEntity<FranchiseTransactionDetails> getTransactionById(@PathVariable Long id) {
        return franchiseTransDetService.getFranchiseTransactionDetailsById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 🔹 Get transaction by transactionId
    @GetMapping("/by-transactionId/{transactionId}")
    public ResponseEntity<FranchiseTransactionDetails> getFranchTransDetById(@PathVariable Long transactionId) {
        return franchiseTransDetService.getFranchiseTransactionDetailsByTransactionDetailsId(transactionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 🔹 Create transaction
    @PostMapping
    public ResponseEntity<FranchiseTransactionDetails> createTransaction(@RequestBody FranchiseTransactionDetails transaction) {
        return ResponseEntity.ok(franchiseTransDetService.saveTransaction(transaction));
    }

    // 🔹 Delete transaction
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        franchiseTransDetService.deleteTransactionDetails(id);
        return ResponseEntity.noContent().build();
    }
}
