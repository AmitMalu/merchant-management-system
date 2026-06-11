package com.project2.ism.Controller;

import com.project2.ism.DTO.CardRateDTO;
import com.project2.ism.DTO.CustomerSchemeAssignmentDTO;
import com.project2.ism.DTO.ProductSchemeReportDTO;
import com.project2.ism.DTO.SchemeGroupedResponseDTO;
import com.project2.ism.Service.CustomerSchemeAssignmentService;
import org.springframework.http.ResponseEntity;
        import org.springframework.web.bind.annotation.*;

        import java.util.List;

@RestController
@RequestMapping("/outward-schemes")
public class CustomerSchemeAssignmentController {

    private final CustomerSchemeAssignmentService assignmentService;



    public CustomerSchemeAssignmentController(CustomerSchemeAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    public ResponseEntity<List<CustomerSchemeAssignmentDTO>> createBulk(
            @RequestBody List<CustomerSchemeAssignmentDTO> dtos) {

        return ResponseEntity.ok(assignmentService.createAssignments(dtos));
    }

    @GetMapping
    public ResponseEntity<List<SchemeGroupedResponseDTO>> getALL() {
        return ResponseEntity.ok(assignmentService.getAllGroupedByScheme());
    }

    @GetMapping("/customer/{type}/{id}")
    public ResponseEntity<List<CustomerSchemeAssignmentDTO>> getByCustomer(
            @PathVariable String type,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(assignmentService.getAssignmentsByCustomer(id, type.toUpperCase()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerSchemeAssignmentDTO> update(
            @PathVariable Long id,
            @RequestBody CustomerSchemeAssignmentDTO dto
    ) {
        return ResponseEntity.ok(assignmentService.updateAssignment(id, dto));
    }

    @PutMapping("/franchise/update-rate/{id}")
    public ResponseEntity<CustomerSchemeAssignmentDTO> franchiseUpdateRate(
            @PathVariable Long id,
            @RequestBody List<CardRateDTO> newRates
    ) {
        return ResponseEntity.ok(assignmentService.franchiseUpdateRate(id, newRates));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }



//----------------------------------For ProductScheme Reports------------------------------------------------


    /**
     * Get all scheme assignments with complete details for product report
     */
    @GetMapping("/report/all")
    public ResponseEntity<List<ProductSchemeReportDTO>> getAllForReport() {
        return ResponseEntity.ok(assignmentService.getAllSchemeAssignmentsForReport());
    }

    /**
     * Get filtered scheme assignments for product report
     */
    @GetMapping("/report/filtered")
    public ResponseEntity<List<ProductSchemeReportDTO>> getFilteredForReport(
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long schemeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean activeOnly
    ) {
        return ResponseEntity.ok(assignmentService.getFilteredSchemeAssignmentsForReport(
                customerType, productId, schemeId, categoryId, activeOnly
        ));
    }

    /**
     * Get scheme assignments by customer type for report
     */
    @GetMapping("/report/customer-type/{customerType}")
    public ResponseEntity<List<ProductSchemeReportDTO>> getByCustomerTypeForReport(
            @PathVariable String customerType
    ) {
        return ResponseEntity.ok(assignmentService.getSchemeAssignmentsByCustomerTypeForReport(customerType));
    }

    /**
     * Get scheme assignments by product for report
     */
    @GetMapping("/report/product/{productId}")
    public ResponseEntity<List<ProductSchemeReportDTO>> getByProductForReport(
            @PathVariable Long productId
    ) {
        return ResponseEntity.ok(assignmentService.getSchemeAssignmentsByProductForReport(productId));
    }

    /**
     * Get active scheme assignments for report
     */
    @GetMapping("/report/active")
    public ResponseEntity<List<ProductSchemeReportDTO>> getActiveForReport() {
        return ResponseEntity.ok(assignmentService.getActiveSchemeAssignmentsForReport());
    }
}
