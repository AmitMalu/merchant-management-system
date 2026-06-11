package com.project2.ism.Controller;

import com.project2.ism.DTO.ReturnTransactionDTO;
import com.project2.ism.Model.InventoryTransactions.ReturnTransactions;
import com.project2.ism.Service.ReturnTransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/return-transactions")
public class ReturnTransactionController {

    private final ReturnTransactionService service;

    public ReturnTransactionController(ReturnTransactionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ReturnTransactionDTO>> getAll() {
        List<ReturnTransactions> entities = service.getAll();
        List<ReturnTransactionDTO> dtoList = entities.stream()
                .map(ReturnTransactionDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReturnTransactionDTO> getById(@PathVariable Long id) {
        ReturnTransactions entity = service.getById(id);
        return ResponseEntity.ok(ReturnTransactionDTO.fromEntity(entity));
    }

    @PostMapping
    public ResponseEntity<ReturnTransactionDTO> create(@Valid @RequestBody ReturnTransactionDTO dto) {
        ReturnTransactions savedEntity = service.createFromDTO(dto);
        return ResponseEntity.ok(ReturnTransactionDTO.fromEntity(savedEntity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReturnTransactionDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody ReturnTransactionDTO dto
    ) {
        ReturnTransactions updatedEntity = service.updateFromDTO(id, dto);
        return ResponseEntity.ok(ReturnTransactionDTO.fromEntity(updatedEntity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}