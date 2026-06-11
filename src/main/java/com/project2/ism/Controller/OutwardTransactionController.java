package com.project2.ism.Controller;



import com.project2.ism.DTO.FranchiseInwardDTO;
import com.project2.ism.DTO.MerchantInwardDTO;
import com.project2.ism.DTO.OutwardTransactionDTO;
import com.project2.ism.Model.InventoryTransactions.OutwardTransactions;
import com.project2.ism.Service.OutwardTransactionService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/outward-transactions")
public class OutwardTransactionController {

    private final OutwardTransactionService service;

    public OutwardTransactionController(OutwardTransactionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<OutwardTransactionDTO>> getAll() {
        List<OutwardTransactions> entities = service.getAll();
        List<OutwardTransactionDTO> dtoList = entities.stream()
                .map(OutwardTransactionDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }


    @GetMapping("/ad")
    public ResponseEntity<Page<OutwardTransactionDTO>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort,

            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate
    ) {

        Sort sortSpec = Sort.by(
                Sort.Order.by(sort[0]).with(sort[1].equalsIgnoreCase("desc")
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC)
        );

        Pageable pageable = PageRequest.of(page, size, sortSpec);

        Page<OutwardTransactions> entityPage =
                service.getFilteredPaginated(pageable, startDate, endDate);

        Page<OutwardTransactionDTO> dtoPage =
                entityPage.map(OutwardTransactionDTO::fromEntity);

        return ResponseEntity.ok(dtoPage);
    }


    @GetMapping("/generate-delivery-number")
    public ResponseEntity<String> generateDeliveryNumberForForm() {
        String deliveryNumber = service.generateDeliveryNumber();
        return ResponseEntity.ok(deliveryNumber);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OutwardTransactionDTO> getById(@PathVariable Long id) {
        OutwardTransactions entity = service.getById(id);
        return ResponseEntity.ok(OutwardTransactionDTO.fromEntity(entity));
    }



    @PostMapping
    public ResponseEntity<OutwardTransactionDTO> create(@Valid @RequestBody OutwardTransactionDTO outwardDTO) {
        OutwardTransactions savedEntity = service.createFromDTO(outwardDTO);
        return ResponseEntity.ok(OutwardTransactionDTO.fromEntity(savedEntity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OutwardTransactionDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody OutwardTransactionDTO outwardDTO
    ) {
        OutwardTransactions updatedEntity = service.updateFromDTO(id, outwardDTO);
        return ResponseEntity.ok(OutwardTransactionDTO.fromEntity(updatedEntity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{outwardId}/received")
    public ResponseEntity<?> updateReceivedDate(@PathVariable Long outwardId){
        service.receivedDateService(outwardId);
        return ResponseEntity.ok("Outward Transaction "+ outwardId + " marked as received.");
    }

    @GetMapping("/franchise/{franchiseId}")
    public ResponseEntity<List<FranchiseInwardDTO>> getFranchiseInward(@PathVariable Long franchiseId) {
        return ResponseEntity.ok(service.getFranchiseInward(franchiseId));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<List<MerchantInwardDTO>> getMerchantInward(@PathVariable Long merchantId) {
        return ResponseEntity.ok(service.getMerchantInward(merchantId));
    }

    @PostMapping("/backfill-received-date/{franchiseId}")
    public ResponseEntity<String> backfillReceivedDate(@PathVariable Long franchiseId) {
        int updated = service.backfillReceivedDateByFranchise(franchiseId);
        return ResponseEntity.ok("Updated receivedDateByFranchise for " + updated + " product serial numbers.");
    }

    /**
     * Export all outward transactions to Excel
     * Streams data from database to avoid memory issues
     */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportToExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            ByteArrayInputStream excelStream = service.exportAllOutwardTransactionsToExcel(
                    startDate, endDate);

            String filename = String.format("outward_transactions_%s_to_%s.xlsx",
                    startDate != null ? startDate : "all",
                    endDate != null ? endDate : "all");

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=" + filename);
            headers.add("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(excelStream));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}