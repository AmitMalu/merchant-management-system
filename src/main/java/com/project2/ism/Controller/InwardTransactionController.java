package com.project2.ism.Controller;


import com.project2.ism.DTO.InwardTransactionDTO;
import com.project2.ism.DTO.ProductSerialDTO;
import com.project2.ism.Model.InventoryTransactions.InwardTransactions;
import com.project2.ism.Service.InwardTransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inward-transactions")
public class InwardTransactionController {

    private final InwardTransactionService inwardService;



    public InwardTransactionController(InwardTransactionService inwardService) {
        this.inwardService = inwardService;
    }

    @PostMapping
    public ResponseEntity<InwardTransactionDTO> create(@RequestBody InwardTransactionDTO dto) {
        InwardTransactionDTO saved = inwardService.createTransaction(dto);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<InwardTransactionDTO>> getAll() {
        return ResponseEntity.ok(inwardService.getAllTransactions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InwardTransactionDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(inwardService.getTransaction(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InwardTransactionDTO> update(@PathVariable Long id,
                                                       @RequestBody InwardTransactionDTO dto) {
        return ResponseEntity.ok(inwardService.updateTransaction(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        inwardService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/serial-number/{id}")
    public ResponseEntity<List<ProductSerialDTO>> getByProductId(@PathVariable Long id){
        return ResponseEntity.ok(inwardService.getSerialNumbersByProductId(id));
    }
}