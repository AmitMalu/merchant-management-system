package com.project2.ism.Controller;

import com.project2.ism.DTO.ReportDTO.ApiResponse;
import com.project2.ism.DTO.ReportDTO.FranchiseTransactionReportSummary;
import com.project2.ism.DTO.ReportDTO.MerchantTransactionReportSummary;
import com.project2.ism.DTO.ReportDTO.TransactionReportDTO;

import com.project2.ism.DTO.ReportDTO.TransactionReportDTO.*;
import com.project2.ism.Exception.BusinessException;
import com.project2.ism.Service.TransactionReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Parameter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("v1/reports/transactions")
@Validated
public class TransactionReportController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionReportController.class);

    private final TransactionReportService transactionReportService;

    public TransactionReportController(TransactionReportService transactionReportService) {
        this.transactionReportService = transactionReportService;
    }



// NEW ENDPOINTS - Add these to your existing TransactionReportController.java

    /**
     * Generate enhanced merchant transaction report with date filter and merchant type options
     * GET /api/v1/reports/transactions/merchant/enhanced
     */
    @GetMapping("/merchant/enhanced")
    //@PreAuthorize("hasRole('ADMIN') or hasRole('MERCHANT') or hasRole('FRANCHISE')")
    public ResponseEntity<ApiResponse<TransactionReportResponse>> generateEnhancedMerchantTransactionReport(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "merchantId") Long merchantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "dateFilterType", defaultValue = "TRANSACTION_DATE") String dateFilterType,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) Integer page,
            @RequestParam(value = "size", defaultValue = "50") @Min(1) @Max(1000) Integer size) {

        logger.info("Received request for enhanced merchant transaction report: startDate={}, endDate={}, merchantId={}, dateFilter={}, ",
                startDate, endDate, merchantId, dateFilterType);

        try {
            TransactionReportRequest request = new TransactionReportRequest();
            request.setStartDate(LocalDateTime.parse(startDate));
            request.setEndDate(LocalDateTime.parse(endDate));
            request.setMerchantId(merchantId);
            request.setTransactionStatus(status);
            request.setTransactionType(transactionType);
            request.setDateFilterType(dateFilterType);
            request.setPage(page);
            request.setSize(size);

            TransactionReportResponse report = transactionReportService.generateEnhancedMerchantTransactionReport(request);

            ApiResponse<TransactionReportResponse> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Merchant transaction report generated successfully");
            response.setData(report);
            response.setTimestamp(LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error generating enhanced merchant transaction report", e);
            ApiResponse<TransactionReportResponse> errorResponse = new ApiResponse<>();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Failed to generate enhanced report: " + e.getMessage());
            errorResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Generate enhanced franchise transaction report with date filter options
     * GET /api/v1/reports/transactions/franchise/enhanced
     */
    @GetMapping("/franchise/enhanced")
    //@PreAuthorize("hasRole('ADMIN') or hasRole('FRANCHISE')")
    public ResponseEntity<ApiResponse<TransactionReportResponse>> generateEnhancedFranchiseTransactionReport(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "franchiseId") Long franchiseId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "dateFilterType", defaultValue = "TRANSACTION_DATE") String dateFilterType,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) Integer page,
            @RequestParam(value = "size", defaultValue = "50") @Min(1) @Max(1000) Integer size) {

        logger.info("Received request for enhanced franchise transaction report: startDate={}, endDate={}, franchiseId={}, dateFilter={}",
                startDate, endDate, franchiseId, dateFilterType);

        try {
            TransactionReportRequest request = new TransactionReportRequest();
            request.setStartDate(LocalDateTime.parse(startDate));
            request.setEndDate(LocalDateTime.parse(endDate));
            request.setFranchiseId(franchiseId);
            request.setTransactionStatus(status);
            request.setTransactionType(transactionType);
            request.setDateFilterType(dateFilterType);
            request.setPage(page);
            request.setSize(size);

            TransactionReportResponse report = transactionReportService.generateEnhancedFranchiseTransactionReport(request);

            ApiResponse<TransactionReportResponse> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Franchise transaction report generated successfully");
            response.setData(report);
            response.setTimestamp(LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error generating enhanced franchise transaction report", e);
            ApiResponse<TransactionReportResponse> errorResponse = new ApiResponse<>();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Failed to generate enhanced report: " + e.getMessage());
            errorResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }




    /**
     * Get enhanced franchise merchant performance breakdown with date filter options
     * GET /api/v1/reports/transactions/franchise/merchant-performance/enhanced
     */
    @GetMapping("/franchise/merchant-performance/enhanced")
    //@PreAuthorize("hasRole('ADMIN') or hasRole('FRANCHISE')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEnhancedFranchiseMerchantPerformance(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "franchiseId", required = false) Long franchiseId,
            @RequestParam(value = "dateFilterType", defaultValue = "TRANSACTION_DATE") String dateFilterType) {

        logger.info("Received request for enhanced franchise merchant performance: startDate={}, endDate={}, franchiseId={}, dateFilter={}",
                startDate, endDate, franchiseId, dateFilterType);

        try {
            TransactionReportRequest request = new TransactionReportRequest();
            request.setStartDate(LocalDateTime.parse(startDate));
            request.setEndDate(LocalDateTime.parse(endDate));
            request.setFranchiseId(franchiseId);
            request.setDateFilterType(dateFilterType);

            List<Map<String, Object>> performance = transactionReportService.getEnhancedFranchiseMerchantPerformance(request);

            ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Franchise merchant performance retrieved successfully");
            response.setData(performance);
            response.setTimestamp(LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting enhanced franchise merchant performance", e);
            ApiResponse<List<Map<String, Object>>> errorResponse = new ApiResponse<>();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Failed to get enhanced performance data: " + e.getMessage());
            errorResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /// new ones
    /**
     * Get enhanced merchant transaction type breakdown
     * GET /api/v1/reports/transactions/merchant/breakdown
     */
    @GetMapping("/merchant/breakdown")
    //@PreAuthorize("hasRole('ADMIN') or hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEnhancedMerchantTransactionTypeBreakdown(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "merchantId", required = false) Long merchantId,
            @RequestParam(value = "dateFilterType", defaultValue = "TRANSACTION_DATE") String dateFilterType) {

        logger.info("Received request for merchant transaction type breakdown: startDate={}, endDate={}, merchantId={}, dateFilter={}",
                startDate, endDate, merchantId, dateFilterType);

        try {
            TransactionReportRequest request = new TransactionReportRequest();
            request.setStartDate(LocalDateTime.parse(startDate));
            request.setEndDate(LocalDateTime.parse(endDate));
            request.setMerchantId(merchantId);
            request.setDateFilterType(dateFilterType);

            List<Map<String, Object>> breakdown = transactionReportService.getEnhancedMerchantTransactionTypeBreakdown(request);

            ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Merchant transaction type breakdown retrieved successfully");
            response.setData(breakdown);
            response.setTimestamp(LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting merchant transaction type breakdown", e);
            ApiResponse<List<Map<String, Object>>> errorResponse = new ApiResponse<>();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Failed to get merchant transaction type breakdown: " + e.getMessage());
            errorResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    /**
     * Get top merchants by commission for a franchise
     * GET /api/v1/reports/transactions/franchise/top-merchants
     */
    @GetMapping("/franchise/top-merchants")
    //@PreAuthorize("hasRole('ADMIN') or hasRole('FRANCHISE')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEnhancedTopMerchantsByCommission(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "franchiseId", required = false) Long franchiseId,
            @RequestParam(value = "dateFilterType", defaultValue = "TRANSACTION_DATE") String dateFilterType) {

        logger.info("Received request for top merchants by commission: startDate={}, endDate={}, franchiseId={}, dateFilter={}",
                startDate, endDate, franchiseId, dateFilterType);

        try {
            TransactionReportRequest request = new TransactionReportRequest();
            request.setStartDate(LocalDateTime.parse(startDate));
            request.setEndDate(LocalDateTime.parse(endDate));
            request.setFranchiseId(franchiseId);
            request.setDateFilterType(dateFilterType);

            List<Map<String, Object>> topMerchants = transactionReportService.getEnhancedTopMerchantsByCommission(request);

            ApiResponse<List<Map<String, Object>>> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setMessage("Top merchants by commission retrieved successfully");
            response.setData(topMerchants);
            response.setTimestamp(LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting top merchants by commission", e);
            ApiResponse<List<Map<String, Object>>> errorResponse = new ApiResponse<>();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Failed to get top merchants by commission: " + e.getMessage());
            errorResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

// excel export

    /**
     * Export all merchant transactions as Excel (optimized for large datasets)
     * GET /api/v1/reports/transactions/merchant/export-all
     */
    @GetMapping("/merchant/export-all")
    public ResponseEntity<Resource> exportAllMerchantTransactions(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "dateFilterType", defaultValue = "TRANSACTION_DATE") String dateFilterType,
            @RequestParam(value = "merchantType", required = false) String merchantType, // NEW PARAM
            @RequestParam(value = "includeTaxes", defaultValue = "false") Boolean includeTaxes) {

        logger.info("Exporting all merchant transactions: startDate={}, endDate={}, dateFilter={}, merchantType={}, includeTaxes={}",
                startDate, endDate, dateFilterType, merchantType, includeTaxes);

        try {
            TransactionReportRequest request = new TransactionReportRequest();
            request.setStartDate(LocalDateTime.parse(startDate));
            request.setEndDate(LocalDateTime.parse(endDate));
            request.setTransactionStatus(status);
            request.setTransactionType(transactionType);
            request.setDateFilterType(dateFilterType);
            request.setMerchantType(merchantType); // NEW: DIRECT, FRANCHISE, or null
            request.setMerchantId(null); // null means ALL merchants of selected type

            String userRole = getUserRoleFromSecurityContext();

            // Generate Excel file
            ByteArrayInputStream excelStream = transactionReportService
                    .exportAllMerchantTransactionsToExcel(request, includeTaxes, userRole,merchantType);

            InputStreamResource resource = new InputStreamResource(excelStream);

            String merchantTypeLabel = merchantType != null ? merchantType.toLowerCase() + "_" : "";
            String filename = String.format("merchant_transactions_%s%s_to_%s.xlsx",
                    merchantTypeLabel,
                    LocalDate.parse(startDate.substring(0, 10)),
                    LocalDate.parse(endDate.substring(0, 10)));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error exporting all merchant transactions", e);
            throw new BusinessException("Failed to export transactions: " + e.getMessage());
        }
    }

    /**
     * Export all franchise transactions as Excel (optimized for large datasets)
     * GET /api/v1/reports/transactions/franchise/export-all
     */
    @GetMapping("/franchise/export-all")
    public ResponseEntity<Resource> exportAllFranchiseTransactions(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "transactionType", required = false) String transactionType,
            @RequestParam(value = "dateFilterType", defaultValue = "TRANSACTION_DATE") String dateFilterType,
            @RequestParam(value = "includeTaxes", defaultValue = "false") Boolean includeTaxes) {

        logger.info("Exporting all franchise transactions: startDate={}, endDate={}, dateFilter={}, includeTaxes={}",
                startDate, endDate, dateFilterType, includeTaxes);

        try {
            TransactionReportRequest request = new TransactionReportRequest();
            request.setStartDate(LocalDateTime.parse(startDate));
            request.setEndDate(LocalDateTime.parse(endDate));
            request.setTransactionStatus(status);
            request.setTransactionType(transactionType);
            request.setDateFilterType(dateFilterType);
            request.setFranchiseId(null); // null means ALL franchises

            String userRole = getUserRoleFromSecurityContext();

            // Generate Excel file
            ByteArrayInputStream excelStream = transactionReportService
                    .exportAllFranchiseTransactionsToExcel(request, includeTaxes, userRole);

            InputStreamResource resource = new InputStreamResource(excelStream);

            String filename = String.format("franchise_transactions_%s_to_%s.xlsx",
                    LocalDate.parse(startDate.substring(0, 10)),
                    LocalDate.parse(endDate.substring(0, 10)));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error exporting all franchise transactions", e);
            throw new BusinessException("Failed to export transactions: " + e.getMessage());
        }
    }

    private String getUserRoleFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            return authentication.getAuthorities().stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .orElse("ADMIN");
        }
        return "ADMIN";
    }



}