package com.project2.ism.Service;



import com.project2.ism.DTO.ReportDTO.FranchiseTransactionReportDTO;
import com.project2.ism.DTO.ReportDTO.FranchiseTransactionReportSummary;
import com.project2.ism.DTO.ReportDTO.MerchantTransactionReportDTO;
import com.project2.ism.DTO.ReportDTO.MerchantTransactionReportSummary;
import com.project2.ism.DTO.ReportDTO.TransactionReportDTO.TransactionReportRequest;
import com.project2.ism.DTO.ReportDTO.TransactionReportDTO.TransactionReportResponse;
import com.project2.ism.DTO.ReportDTO.TransactionReportDTO.TransactionSummary;
import com.project2.ism.DTO.ReportDTO.TransactionReportDTO.*;
import com.project2.ism.Exception.BusinessException;
import com.project2.ism.Exception.ValidationException;
import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.Taxes;
import com.project2.ism.Model.VendorTransactions;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class TransactionReportService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionReportService.class);

    private final MerchantTransDetRepository merchantTransactionRepository;
    private final FranchiseTransDetRepository franchiseTransactionRepository;
    private final TaxesService taxesService;

    private static final int MAX_DATE_RANGE_DAYS = 365;
    private static final int MAX_PAGE_SIZE = 1000;

    public TransactionReportService(MerchantTransDetRepository merchantTransactionRepository,
                                    FranchiseTransDetRepository franchiseTransactionRepository, TaxesService taxesService) {
        this.merchantTransactionRepository = merchantTransactionRepository;
        this.franchiseTransactionRepository = franchiseTransactionRepository;
        this.taxesService = taxesService;
    }


    // Keep all existing methods as they are, ADD these new methods:
// should keep these methods all above remove
    /**
     * Generate merchant transaction report with enhanced filtering
     */
    public TransactionReportResponse generateEnhancedMerchantTransactionReport(TransactionReportRequest request) {
        logger.info("Generating enhanced merchant transaction report...");

        validateReportRequest(request);

        try {
            Pageable pageable = createPageable(request);
            Page<MerchantTransactionReportDTO> transactionPage;

            // Choose query based on date filter type
            if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
                transactionPage = merchantTransactionRepository
                        .findMerchantTransactionsBySettlementDateFilters(
                                request.getStartDate(),
                                request.getEndDate(),
                                request.getMerchantId(),
                                request.getTransactionStatus(),
                                request.getTransactionType(),
                                pageable);
            } else {
                transactionPage = merchantTransactionRepository
                        .findMerchantTransactionsByFilters(
                                request.getStartDate(),
                                request.getEndDate(),
                                request.getMerchantId(),
                                request.getTransactionStatus(),
                                request.getTransactionType(),
                                pageable);
            }


            // Get user role from Security Context
            String userRole = getUserRoleFromSecurityContext();

            // Apply role-based filtering
            List<MerchantTransactionReportDTO> adjustedTransactions = transactionPage.getContent()
                    .stream()
                    .map(dto -> applyRoleBasedFiltering(dto, userRole))
                    .collect(Collectors.toList());

            // Get summary
            MerchantTransactionReportSummary summary = getEnhancedMerchantTransactionSummary(request);

            // Build response
            TransactionReportResponse<MerchantTransactionReportDTO> response = new TransactionReportResponse<>();
            response.setTransactions(adjustedTransactions);
            response.setSummary(summary);
            response.setReportGeneratedAt(LocalDateTime.now());
            response.setReportType("MERCHANT_TRANSACTION_REPORT");
            response.setTotalPages(transactionPage.getTotalPages());
            response.setTotalElements(transactionPage.getTotalElements());
            response.setHasNext(transactionPage.hasNext());
            response.setHasPrevious(transactionPage.hasPrevious());

            logger.info("Successfully generated report with {} transactions", adjustedTransactions.size());
            return response;

        } catch (Exception e) {
            logger.error("Error generating report", e);
            throw new BusinessException("Failed to generate report: " + e.getMessage());
        }
    }

    // Helper method to get role from JWT token in Security Context
    private String getUserRoleFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            return authentication.getAuthorities().stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .orElse("ADMIN");  // Default to ADMIN if no role found
        }
        return "ADMIN";
    }

    private BigDecimal getGstPercentage() {
        // Fetch from taxes table (id = 3)
        // Consider caching this value since it rarely changes
        return taxesService.getTaxes().getGst();
    }

    // Helper method to calculate GST
    private void calculateAndSetGst(MerchantTransactionReportDTO dto, BigDecimal gstPercentage) {
        // Calculate GST on system fee (merchant's charge)

        if ("PAYOUT".equalsIgnoreCase(dto.getService()) || "PAYOUT_REFUND".equalsIgnoreCase(dto.getService())) return;
        if (dto.getSystemFee() != null && gstPercentage != null) {
            BigDecimal gstAmount = dto.getSystemFee()
                    .multiply(gstPercentage)
                    .divide(BigDecimal.valueOf(100).add(gstPercentage), 2, RoundingMode.HALF_UP);
            BigDecimal systemFeeExgst = dto.getSystemFee().subtract(gstAmount);
            dto.setGstAmount(gstAmount);
            dto.setSystemFeeExGST(systemFeeExgst);
        }
    }
    // Apply role-based filtering
    private MerchantTransactionReportDTO applyRoleBasedFiltering(MerchantTransactionReportDTO dto, String role) {
        // Replace systemFee with grossCharge for merchant view
        if (dto.getGrossCharge() != null) {
            dto.setSystemFee(dto.getGrossCharge());
        }
        if ("ROLE_MERCHANT".equals(role) || "MERCHANT".equals(role)) {
            // Hide franchise information
            dto.setFranchiseName(null);
            dto.setFranchiseRate(null);
            dto.setCommissionRate(null);
            dto.setCommissionAmount(null);


        }
        else if (role != null && (
                role.equalsIgnoreCase("ROLE_ADMIN") ||
                        role.equalsIgnoreCase("ADMIN") ||
                        role.equalsIgnoreCase("ROLE_SUPER_ADMIN") ||
                        role.equalsIgnoreCase("SUPER_ADMIN"))) {

            // Only Admins and Super Admins get GST calculation
            BigDecimal gstPercentage = getGstPercentage();
            calculateAndSetGst(dto, gstPercentage);
        }

        // For ADMIN/FRANCHISE roles, show everything as-is
        return dto;
    }
    /**
     * Generate franchise transaction report with enhanced filtering and entity mapping
     */
    public TransactionReportResponse generateEnhancedFranchiseTransactionReport(TransactionReportRequest request) {
        logger.info("Generating enhanced franchise transaction report for date range: {} to {}, dateFilter: {}",
                request.getStartDate(), request.getEndDate(), request.getDateFilterType());

        validateReportRequest(request);

        try {
            Pageable pageable = createPageable(request);

            Page<FranchiseTransactionDetails> entityPage;

            // Choose query based on date filter type
            if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
                entityPage = franchiseTransactionRepository
                        .findFranchiseTransactionsBySettlementDateFilters(
                                request.getStartDate(),
                                request.getEndDate(),
                                request.getFranchiseId(),
                                request.getTransactionStatus(),
                                request.getTransactionType(),
                                pageable);
            } else {
                // Default to transaction date
                entityPage = franchiseTransactionRepository
                        .findFranchiseTransactionsByFilters(
                                request.getStartDate(),
                                request.getEndDate(),
                                request.getFranchiseId(),
                                request.getTransactionStatus(),
                                request.getTransactionType(),
                                pageable);
            }
// After fetching the page
//            System.out.println("Total rows returned: " + entityPage.getNumberOfElements());
//            entityPage.getContent().forEach(ftd -> {
//                System.out.println("FTD ID: " + ftd.getTransactionId() +
//                        ", Date: " + ftd.getTransactionDate() +
//                        ", Amount: " + ftd.getAmount() +
//                        ", MerchantTxnId: " + (ftd.getMerchantTransactionDetail() != null
//                        ? ftd.getMerchantTransactionDetail().getTransactionId()
//                        : "null"));
//            });
            // Collect all vendor transaction IDs for batch fetch
            List<String> vendorTransactionIds = entityPage.getContent().stream()
                    .map(FranchiseTransactionDetails::getMerchantTransactionDetail)
                    .filter(Objects::nonNull)
                    .map(MerchantTransactionDetails::getVendorTransactionId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // Batch fetch vendor transactions (if any exist)
            Map<String, VendorTransactions> vendorTransactionMap = new HashMap<>();
            if (!vendorTransactionIds.isEmpty()) {
                List<VendorTransactions> vendorTransactions =
                        franchiseTransactionRepository.findByTransactionReferenceIdIn(vendorTransactionIds);
                vendorTransactionMap = vendorTransactions.stream()
                        .collect(Collectors.toMap(
                                VendorTransactions::getTransactionReferenceId,
                                vt -> vt,
                                (existing, replacement) -> existing // Handle duplicates if any
                        ));
            }

            // Map entities to DTOs
            final Map<String, VendorTransactions> finalVendorMap = vendorTransactionMap;
            Page<FranchiseTransactionReportDTO> transactionPage = entityPage.map(ftd ->
                    mapToFranchiseTransactionReportDTO(ftd, finalVendorMap)
            );

            // Get franchise summary with commission data
            FranchiseTransactionReportSummary summary = getEnhancedFranchiseTransactionSummary(request);

            // Build response
            TransactionReportResponse<FranchiseTransactionReportDTO> response = new TransactionReportResponse<>();
            response.setTransactions(transactionPage.getContent());
            response.setSummary(summary);
            response.setReportGeneratedAt(LocalDateTime.now());
            response.setReportType("ENHANCED_FRANCHISE_TRANSACTION_REPORT");
            response.setTotalPages(transactionPage.getTotalPages());
            response.setTotalElements(transactionPage.getTotalElements());
            response.setHasNext(transactionPage.hasNext());
            response.setHasPrevious(transactionPage.hasPrevious());

            logger.info("Successfully generated enhanced franchise transaction report with {} transactions",
                    transactionPage.getNumberOfElements());
            return response;

        } catch (Exception e) {
            logger.error("Error generating enhanced franchise transaction report", e);
            throw new BusinessException("Failed to generate enhanced franchise transaction report: " + e.getMessage());
        }
    }

    /**
     * Helper method to map FranchiseTransactionDetails entity to DTO
     * Handles NULL merchant and vendor transactions gracefully
     */
    private FranchiseTransactionReportDTO mapToFranchiseTransactionReportDTO(
            FranchiseTransactionDetails ftd,
            Map<String, VendorTransactions> vendorTransactionMap) {

        MerchantTransactionDetails mtd = ftd.getMerchantTransactionDetail();
        VendorTransactions vt = null;
        Taxes taxes = taxesService.getTaxes();
        // Get vendor transaction if merchant transaction exists
        if (mtd != null && mtd.getVendorTransactionId() != null) {
            vt = vendorTransactionMap.get(mtd.getVendorTransactionId());
        }

        // Use your existing DTO constructor - it handles nulls and calculations perfectly!
        FranchiseTransactionReportDTO dto = new FranchiseTransactionReportDTO(
                // Vendor transaction ID (null for standalone CREDIT/DEBIT)
                mtd != null ? mtd.getVendorTransactionId() : null,
                ftd.getTransactionId(),
                ftd.getActionOnBalance(),
                // Transaction date
                ftd.getTransactionDate(),

                // Transaction amount
                ftd.getAmount(),

                // Settlement date
                ftd.getUpdatedDateAndTimeOfTransaction(),

                // Vendor transaction fields (null if no vendor transaction)
                vt != null ? vt.getAuthCode() : null,
                vt != null ? vt.getTid() : null,

                // Merchant transaction fields (null for standalone transactions)
                mtd != null ? mtd.getNetAmount() : null,        // merchantNetAmount
                mtd != null ? mtd.getGrossCharge() : null,      // grossCharge

                // Franchise commission (always present in ftd)
                ftd.getNetAmount(),                              // franchiseCommission

                // System fee
                mtd != null ? mtd.getCharge() : null,            // systemFee
                taxes.getGst(),
                taxes.getTds(),
                // Card details (null if no vendor transaction)
                vt != null ? vt.getBrandType() : null,
                vt != null ? vt.getCardType() : null,
                vt != null ? vt.getCardClassification() : null,

                // Business names
                mtd != null && mtd.getMerchant() != null ? mtd.getMerchant().getBusinessName() : null,
                ftd.getFranchise() != null ? ftd.getFranchise().getFranchiseName() : null,

                // Transaction status
                ftd.getTranStatus(),
                ftd.getService()
        );
        String userRole = getUserRoleFromSecurityContext();
        return applyFranchiseRoleBasedFiltering(dto,userRole);
    }


    private FranchiseTransactionReportDTO applyFranchiseRoleBasedFiltering(
            FranchiseTransactionReportDTO dto, String role) {

        if (role == null) return dto;

        // Normalize and check
        String normalizedRole = role.toUpperCase();

        if (normalizedRole.contains("ADMIN") || normalizedRole.contains("SUPER_ADMIN")) {
            // Admin and Super Admin can see GST and TDS
            return dto;
        } else {
            // Hide sensitive financial details for others
            dto.setSystemFeeExGST(null);
            dto.setGstAmount(null);
            dto.setTdsAmount(null);
            dto.setTdsPercentage(null);
            dto.setNetCommissionAmount(null);
        }

        return dto;
    }

    /**
     * Get enhanced merchant transaction summary
     */
    public FranchiseTransactionReportSummary getEnhancedFranchiseTransactionSummary(TransactionReportRequest request) {
        validateReportRequest(request);

        Map<String, Object> summaryData;

        if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
            summaryData = franchiseTransactionRepository.getFranchiseTransactionSummaryBySettlementDate(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getFranchiseId(),
                    request.getTransactionStatus(),
                    request.getTransactionType());
        } else {
            summaryData = franchiseTransactionRepository.getFranchiseTransactionSummary(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getFranchiseId(),
                    request.getTransactionStatus(),
                    request.getTransactionType());
        }

        return buildFranchiseTransactionSummary(summaryData);
    }

    public MerchantTransactionReportSummary getEnhancedMerchantTransactionSummary(TransactionReportRequest request) {
        validateReportRequest(request);

        Map<String, Object> summaryData;

        if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
            summaryData = merchantTransactionRepository.getMerchantTransactionSummaryBySettlementDate(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getMerchantId(),
                    request.getTransactionStatus(),
                    request.getTransactionType());
        } else {
            summaryData = merchantTransactionRepository.getMerchantTransactionSummary(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getMerchantId(),
                    request.getTransactionStatus(),
                    request.getTransactionType());
        }

        return buildMerchantTransactionSummary(summaryData);
    }

    private FranchiseTransactionReportSummary buildFranchiseTransactionSummary(Map<String, Object> summaryData) {
        FranchiseTransactionReportSummary summary = new FranchiseTransactionReportSummary();

        // Settlement/Commission data
        summary.setSettlementCount(getLongValue(summaryData, "settlementCount"));
        summary.setTotalSettlementAmount(getBigDecimalValue(summaryData, "totalSettlementAmount"));
        summary.setTotalCommissionEarned(getBigDecimalValue(summaryData, "totalCommissionEarned"));

        // Payout data
        summary.setPayoutCount(getLongValue(summaryData, "payoutCount"));
        summary.setTotalPayoutAmount(getBigDecimalValue(summaryData, "totalPayoutAmount"));
        summary.setTotalPayoutFees(getBigDecimalValue(summaryData, "totalPayoutFees"));
        summary.setSuccessfulPayouts(getLongValue(summaryData, "successfulPayouts"));
        summary.setFailedPayouts(getLongValue(summaryData, "failedPayouts"));
        summary.setPendingPayouts(getLongValue(summaryData, "pendingPayouts"));

        // Refund data
        summary.setRefundCount(getLongValue(summaryData, "refundCount"));
        summary.setTotalRefundAmount(getBigDecimalValue(summaryData, "totalRefundAmount"));

        // Net position
        summary.setNetCreditAmount(getBigDecimalValue(summaryData, "netCreditAmount"));
        summary.setNetDebitAmount(getBigDecimalValue(summaryData, "netDebitAmount"));
        summary.setNetBalance(getBigDecimalValue(summaryData, "netBalance"));

        // Overall stats
        summary.setTotalTransactions(getLongValue(summaryData, "totalTransactions"));
        summary.setSuccessCount(getLongValue(summaryData, "successCount"));
        summary.setFailureCount(getLongValue(summaryData, "failureCount"));
        summary.setPendingCount(getLongValue(summaryData, "pendingCount"));
        summary.setActiveMerchants(getLongValue(summaryData, "activeMerchants"));

        return summary;
    }

    private MerchantTransactionReportSummary buildMerchantTransactionSummary(Map<String, Object> summaryData) {
        MerchantTransactionReportSummary summary = new MerchantTransactionReportSummary();

        // Settlement data
        summary.setSettlementCount(getLongValue(summaryData, "settlementCount"));
        summary.setTotalTransactionAmount(getBigDecimalValue(summaryData, "totalTransactionAmount"));
        summary.setTotalSettlementReceived(getBigDecimalValue(summaryData, "totalSettlementReceived"));
        summary.setTotalChargesPaid(getBigDecimalValue(summaryData, "totalChargesPaid"));

        // Payout data
        summary.setPayoutCount(getLongValue(summaryData, "payoutCount"));
        summary.setTotalPayoutAmount(getBigDecimalValue(summaryData, "totalPayoutAmount"));
        summary.setTotalPayoutFees(getBigDecimalValue(summaryData, "totalPayoutFees"));
        summary.setSuccessfulPayouts(getLongValue(summaryData, "successfulPayouts"));
        summary.setFailedPayouts(getLongValue(summaryData, "failedPayouts"));
        summary.setPendingPayouts(getLongValue(summaryData, "pendingPayouts"));

        // Refund data
        summary.setRefundCount(getLongValue(summaryData, "refundCount"));
        summary.setTotalRefundAmount(getBigDecimalValue(summaryData, "totalRefundAmount"));

        // Net position
        summary.setNetCreditAmount(getBigDecimalValue(summaryData, "netCreditAmount"));
        summary.setNetDebitAmount(getBigDecimalValue(summaryData, "netDebitAmount"));
        summary.setNetBalance(getBigDecimalValue(summaryData, "netBalance"));

        // Overall stats
        summary.setTotalTransactions(getLongValue(summaryData, "totalTransactions"));
        summary.setSuccessCount(getLongValue(summaryData, "successCount"));
        summary.setFailureCount(getLongValue(summaryData, "failureCount"));
        summary.setPendingCount(getLongValue(summaryData, "pendingCount"));

        return summary;
    }


    /**
     * Get enhanced franchise merchant performance
     */
    public List<Map<String, Object>> getEnhancedFranchiseMerchantPerformance(TransactionReportRequest request) {
        validateReportRequest(request);

        List<Object[]> performanceData;

        if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
            performanceData = franchiseTransactionRepository.getFranchiseMerchantPerformanceBySettlementDate(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getFranchiseId());
        } else {
            performanceData = franchiseTransactionRepository.getFranchiseMerchantPerformance(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getFranchiseId());
        }

        return performanceData.stream()
                .map(row -> Map.of(
                        "merchantId", row[0],
                        "merchantName", row[1],
                        "transactionCount", row[2],
                        "totalAmount", row[3],
                        "totalCommission", row[4]
                        ))
                .collect(Collectors.toList());
    }



//    /**
//     * Export all merchant transactions to Excel using streaming for memory efficiency
//     */
//    @Transactional(readOnly = true)
//    public ByteArrayInputStream exportAllMerchantTransactionsToExcel(
//            TransactionReportRequest request, Boolean includeTaxes, String userRole,String merchantType) {
//
//        logger.info("Exporting all merchant transactions to Excel with streaming...");
//        validateReportRequest(request);
//
//        try (Workbook workbook = new XSSFWorkbook();
//             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
//
//            Sheet sheet = workbook.createSheet("Merchant Transactions");
//
//            // Create header row
//            Row headerRow = sheet.createRow(0);
//            createMerchantExcelHeader(headerRow, includeTaxes, userRole, merchantType);
//
//            // Stream data and write to Excel
//            AtomicInteger rowNum = new AtomicInteger(1);
//            BigDecimal gstPercentage = includeTaxes ? getGstPercentage() : null;
//
//            Stream<MerchantTransactionReportDTO> transactionStream;
//
//            if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
//                transactionStream = merchantTransactionRepository
//                        .streamAllMerchantTransactionsBySettlementDateFilters(
//                                request.getStartDate(),
//                                request.getEndDate(),
//                                request.getTransactionType(),
//                                request.getMerchantType());
//            } else {
//                transactionStream = merchantTransactionRepository
//                        .streamAllMerchantTransactionsByFilters(
//                                request.getStartDate(),
//                                request.getEndDate(),
//                                request.getTransactionType(),
//                                request.getMerchantType());
//            }
//
//            transactionStream.forEach(dto -> {
//                // Apply role-based filtering
//                MerchantTransactionReportDTO filteredDto = applyRoleBasedFiltering(dto, userRole);
//
//                // Calculate GST if needed
//                if (includeTaxes && gstPercentage != null) {
//                    calculateAndSetGst(filteredDto, gstPercentage);
//                }
//
//                // Create row
//                Row row = sheet.createRow(rowNum.getAndIncrement());
//                populateMerchantExcelRow(row, filteredDto, includeTaxes, userRole);
//
//                // Flush every 1000 rows to manage memory
//                if (rowNum.get() % 1000 == 0) {
//                    logger.info("Processed {} rows", rowNum.get());
//                }
//            });
//
//            // Auto-size columns
//            int columnCount = includeTaxes ? (isAdminRole(userRole) ? 20 : 15) : 12;
//            for (int i = 0; i < columnCount; i++) {
//                sheet.autoSizeColumn(i);
//            }
//
//            workbook.write(out);
//            logger.info("Successfully exported {} merchant transactions to Excel", rowNum.get() - 1);
//
//            return new ByteArrayInputStream(out.toByteArray());
//
//        } catch (Exception e) {
//            logger.error("Error exporting merchant transactions to Excel", e);
//            throw new BusinessException("Failed to export to Excel: " + e.getMessage());
//        }
//    }
//
//    /**
//     * Export all franchise transactions to Excel using streaming
//     */
//    @Transactional(readOnly = true)
//    public ByteArrayInputStream exportAllFranchiseTransactionsToExcel(
//            TransactionReportRequest request, Boolean includeTaxes, String userRole) {
//
//        logger.info("Exporting all franchise transactions to Excel with streaming...");
//        validateReportRequest(request);
//
//        try (Workbook workbook = new XSSFWorkbook();
//             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
//
//            Sheet sheet = workbook.createSheet("Franchise Transactions");
//
//            // Create header row
//            Row headerRow = sheet.createRow(0);
//            createFranchiseExcelHeader(headerRow, includeTaxes, userRole);
//
//            // Prepare vendor transaction map for batch loading
//            Stream<FranchiseTransactionDetails> transactionStream;
//
//            if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
//                transactionStream = franchiseTransactionRepository
//                        .streamAllFranchiseTransactionsBySettlementDateFilters(
//                                request.getStartDate(),
//                                request.getEndDate(),
//                                request.getTransactionType());
//            } else {
//                transactionStream = franchiseTransactionRepository
//                        .streamAllFranchiseTransactionsByFilters(
//                                request.getStartDate(),
//                                request.getEndDate(),
//                                request.getTransactionType());
//            }
//
//            // Process in batches for vendor transaction lookup
//            AtomicInteger rowNum = new AtomicInteger(1);
//            List<FranchiseTransactionDetails> batch = new ArrayList<>();
//
//            transactionStream.forEach(ftd -> {
//                batch.add(ftd);
//
//                // Process batch every 500 records
//                if (batch.size() >= 500) {
//                    processFranchiseBatchToExcel(workbook, sheet, batch, rowNum, includeTaxes, userRole);
//                    batch.clear();
//                }
//            });
//
//            // Process remaining batch
//            if (!batch.isEmpty()) {
//                processFranchiseBatchToExcel(workbook, sheet, batch, rowNum, includeTaxes, userRole);
//            }
//
//            // Auto-size columns
//            int columnCount = includeTaxes ? (isAdminRole(userRole) ? 22 : 18) : 15;
//            for (int i = 0; i < columnCount; i++) {
//                sheet.autoSizeColumn(i);
//            }
//
//            workbook.write(out);
//            logger.info("Successfully exported {} franchise transactions to Excel", rowNum.get() - 1);
//
//            return new ByteArrayInputStream(out.toByteArray());
//
//        } catch (Exception e) {
//            logger.error("Error exporting franchise transactions to Excel", e);
//            throw new BusinessException("Failed to export to Excel: " + e.getMessage());
//        }
//    }
//
//    /**
//     * Process franchise batch with vendor transaction lookup
//     */
//    private void processFranchiseBatchToExcel(Workbook workbook, Sheet sheet,
//                                              List<FranchiseTransactionDetails> batch, AtomicInteger rowNum,
//                                              Boolean includeTaxes, String userRole) {
//
//        // Collect vendor transaction IDs
//        List<String> vendorTransactionIds = batch.stream()
//                .map(FranchiseTransactionDetails::getMerchantTransactionDetail)
//                .filter(Objects::nonNull)
//                .map(MerchantTransactionDetails::getVendorTransactionId)
//                .filter(Objects::nonNull)
//                .distinct()
//                .collect(Collectors.toList());
//
//        // Batch fetch vendor transactions
//        Map<String, VendorTransactions> vendorTransactionMap = new HashMap<>();
//        if (!vendorTransactionIds.isEmpty()) {
//            List<VendorTransactions> vendorTransactions =
//                    franchiseTransactionRepository.findByTransactionReferenceIdIn(vendorTransactionIds);
//            vendorTransactionMap = vendorTransactions.stream()
//                    .collect(Collectors.toMap(
//                            VendorTransactions::getTransactionReferenceId,
//                            vt -> vt,
//                            (existing, replacement) -> existing
//                    ));
//        }
//
//        // Process each transaction in batch
//        final Map<String, VendorTransactions> finalVendorMap = vendorTransactionMap;
//        batch.forEach(ftd -> {
//            FranchiseTransactionReportDTO dto = mapToFranchiseTransactionReportDTO(ftd, finalVendorMap);
//            dto = applyFranchiseRoleBasedFiltering(dto, userRole);
//
//            Row row = sheet.createRow(rowNum.getAndIncrement());
//            populateFranchiseExcelRow(row, dto, includeTaxes, userRole);
//        });
//
//        logger.info("Processed batch, total rows: {}", rowNum.get());
//    }
//
    /**
     * UPDATED: Create header for merchant Excel export with Service column
     */
    private void createMerchantExcelHeader(Row headerRow, Boolean includeTaxes, String userRole, String merchantType) {
        int colNum = 0;

        // Common headers
        headerRow.createCell(colNum++).setCellValue("Transaction ID");
        headerRow.createCell(colNum++).setCellValue("Vendor Transaction ID");
        headerRow.createCell(colNum++).setCellValue("Service");  // NEW: Service type (Settlement/PAYOUT/PAYOUT_REFUND)
        headerRow.createCell(colNum++).setCellValue("Action");
        headerRow.createCell(colNum++).setCellValue("Transaction Date");
        headerRow.createCell(colNum++).setCellValue("Settlement Date");
        headerRow.createCell(colNum++).setCellValue("Amount");
        headerRow.createCell(colNum++).setCellValue("Auth Code");
        headerRow.createCell(colNum++).setCellValue("TID");
        headerRow.createCell(colNum++).setCellValue("Net Amount");
        headerRow.createCell(colNum++).setCellValue("System Fee");
        headerRow.createCell(colNum++).setCellValue("Settlement Rate");
        headerRow.createCell(colNum++).setCellValue("Merchant Rate");
        headerRow.createCell(colNum++).setCellValue("Merchant Name");
        headerRow.createCell(colNum++).setCellValue("Status");

        // Franchise info (only for franchise merchants)
        if (Objects.equals(merchantType, "FRANCHISE")) {
            headerRow.createCell(colNum++).setCellValue("Franchise Name");
        }

        // Tax columns (only if includeTaxes and admin) - shown for all rows, but will be empty for PAYOUT
        if (includeTaxes && isAdminRole(userRole)) {
            headerRow.createCell(colNum++).setCellValue("System Fee (Ex GST)");
            headerRow.createCell(colNum++).setCellValue("GST Amount");
            headerRow.createCell(colNum++).setCellValue("GST %");
        }

        // Card details - will be empty for PAYOUT/REFUND
        headerRow.createCell(colNum++).setCellValue("Brand Type");
        headerRow.createCell(colNum++).setCellValue("Card Type");
        headerRow.createCell(colNum++).setCellValue("Card Classification");
    }

    /**
     * UPDATED: Create header for franchise Excel export with Service column
     */
    private void createFranchiseExcelHeader(Row headerRow, Boolean includeTaxes, String userRole) {
        int colNum = 0;

        // Common headers
        headerRow.createCell(colNum++).setCellValue("Transaction ID");
        headerRow.createCell(colNum++).setCellValue("Vendor Transaction ID");
        headerRow.createCell(colNum++).setCellValue("Service");  // NEW: Service type (COMMISSION/PAYOUT/PAYOUT_REFUND)
        headerRow.createCell(colNum++).setCellValue("Action");
        headerRow.createCell(colNum++).setCellValue("Transaction Date");
        headerRow.createCell(colNum++).setCellValue("Settlement Date");
        headerRow.createCell(colNum++).setCellValue("Amount");
        headerRow.createCell(colNum++).setCellValue("Auth Code");
        headerRow.createCell(colNum++).setCellValue("TID");
        headerRow.createCell(colNum++).setCellValue("Merchant Net Amount");
        headerRow.createCell(colNum++).setCellValue("System Fee");
        headerRow.createCell(colNum++).setCellValue("Franchise Commission");
        headerRow.createCell(colNum++).setCellValue("Settlement Rate");
        headerRow.createCell(colNum++).setCellValue("Merchant Rate");
        headerRow.createCell(colNum++).setCellValue("Franchise Rate");
        headerRow.createCell(colNum++).setCellValue("Merchant Name");
        headerRow.createCell(colNum++).setCellValue("Franchise Name");
        headerRow.createCell(colNum++).setCellValue("Status");

        // Tax columns (only if includeTaxes and admin) - shown for all rows, but will be empty for PAYOUT
        if (includeTaxes && isAdminRole(userRole)) {
            headerRow.createCell(colNum++).setCellValue("System Fee (Ex GST)");
            headerRow.createCell(colNum++).setCellValue("GST Amount");
            headerRow.createCell(colNum++).setCellValue("TDS Amount");
            headerRow.createCell(colNum++).setCellValue("TDS %");
            headerRow.createCell(colNum++).setCellValue("Net Commission");
        }

        // Card details - will be empty for PAYOUT/REFUND
        headerRow.createCell(colNum++).setCellValue("Brand Type");
        headerRow.createCell(colNum++).setCellValue("Card Type");
        headerRow.createCell(colNum++).setCellValue("Card Classification");
    }

    /**
     * UPDATED: Populate merchant Excel row with Service column
     */
    private void populateMerchantExcelRow(Row row, MerchantTransactionReportDTO dto,
                                          Boolean includeTaxes, String userRole) {
        int colNum = 0;

        // Basic transaction info
        setCellValue(row, colNum++, dto.getCustomTxnId());
        setCellValue(row, colNum++, dto.getTxnId());
        setCellValue(row, colNum++, dto.getService());  // NEW: Service column
        setCellValue(row, colNum++, dto.getActionOnBalance());
        setCellValue(row, colNum++, dto.getTxnDate());
        setCellValue(row, colNum++, dto.getSettleDate());
        setCellValue(row, colNum++, dto.getTxnAmount());

        // Vendor details (will be empty for PAYOUT/REFUND)
        setCellValue(row, colNum++, dto.getAuthCode());
        setCellValue(row, colNum++, dto.getTid());

        // Amounts
        setCellValue(row, colNum++, dto.getSettleAmount());
        setCellValue(row, colNum++, dto.getSystemFee());
        setCellValue(row, colNum++, dto.getSettlementPercentage());
        setCellValue(row, colNum++, dto.getMerchantRate());
        setCellValue(row, colNum++, dto.getMerchantName());
        setCellValue(row, colNum++, dto.getState());

        // Franchise info (if applicable)
        if (dto.getFranchiseName() != null) {
            setCellValue(row, colNum++, dto.getFranchiseName());
        }

        // Tax columns (admin only, will be empty for PAYOUT)
        if (includeTaxes && isAdminRole(userRole)) {
            if (!isWalletTransaction(dto.getService())) {
                setCellValue(row, colNum++, dto.getSystemFeeExGST());
                setCellValue(row, colNum++, dto.getGstAmount());
                setCellValue(row, colNum++, taxesService.getTaxes().getGst());
            } else {
                // Empty cells for PAYOUT/REFUND
                setCellValue(row, colNum++, "");
                setCellValue(row, colNum++, "");
                setCellValue(row, colNum++, "");
            }
        }

        // Card details (will be empty for PAYOUT/REFUND)
        setCellValue(row, colNum++, dto.getBrandType());
        setCellValue(row, colNum++, dto.getCardType());
        setCellValue(row, colNum++, dto.getCardClassification());
    }

    /**
     * UPDATED: Populate franchise Excel row with Service column
     */
    private void populateFranchiseExcelRow(Row row, FranchiseTransactionReportDTO dto,
                                           Boolean includeTaxes, String userRole) {
        int colNum = 0;

        // Basic info
        setCellValue(row, colNum++, dto.getCustomTxnId());
        setCellValue(row, colNum++, dto.getTxnId());
        setCellValue(row, colNum++, dto.getService());  // NEW: Service column
        setCellValue(row, colNum++, dto.getActionOnBalance());
        setCellValue(row, colNum++, dto.getTxnDate());
        setCellValue(row, colNum++, dto.getSettleDate());
        setCellValue(row, colNum++, dto.getTxnAmount());

        // Vendor details (empty for PAYOUT)
        setCellValue(row, colNum++, dto.getAuthCode());
        setCellValue(row, colNum++, dto.getTid());

        // Amounts
        setCellValue(row, colNum++, dto.getSettleAmount());
        setCellValue(row, colNum++, dto.getSystemFee());
        setCellValue(row, colNum++, dto.getCommissionAmount());
        setCellValue(row, colNum++, dto.getSettlementRate());
        setCellValue(row, colNum++, dto.getMerchantRate());
        setCellValue(row, colNum++, dto.getFranchiseRate());
        setCellValue(row, colNum++, dto.getMerchantName());
        setCellValue(row, colNum++, dto.getFranchiseName());
        setCellValue(row, colNum++, dto.getState());

        // Tax columns (admin only, will be empty for PAYOUT)
        if (includeTaxes && isAdminRole(userRole)) {
            if (!isWalletTransaction(dto.getService())) {
                setCellValue(row, colNum++, dto.getSystemFeeExGST());
                setCellValue(row, colNum++, dto.getGstAmount());
                setCellValue(row, colNum++, dto.getTdsAmount());
                setCellValue(row, colNum++, dto.getTdsPercentage());
                setCellValue(row, colNum++, dto.getNetCommissionAmount());
            } else {
                // Empty cells for PAYOUT/REFUND
                setCellValue(row, colNum++, "");
                setCellValue(row, colNum++, "");
                setCellValue(row, colNum++, "");
                setCellValue(row, colNum++, "");
                setCellValue(row, colNum++, "");
            }
        }

        // Card details (empty for PAYOUT)
        setCellValue(row, colNum++, dto.getBrandType());
        setCellValue(row, colNum++, dto.getCardType());
        setCellValue(row, colNum++, dto.getCardClassification());
    }

    /**
     * UPDATED: Auto-size columns with correct count including Service column
     */
    private void autoSizeColumns(Sheet sheet, Boolean includeTaxes, String userRole, String merchantType) {
        int baseColumns = 15; // Base columns for merchant (added 1 for Service)
        int extraColumns = 0;

        if (Objects.equals(merchantType, "FRANCHISE")) {
            extraColumns += 1; // Franchise name column
        }

        if (includeTaxes && isAdminRole(userRole)) {
            extraColumns += 3; // GST columns
        }

        extraColumns += 3; // Card details

        int totalColumns = baseColumns + extraColumns;

        for (int i = 0; i < totalColumns; i++) {
            try {
                sheet.autoSizeColumn(i);
            } catch (Exception e) {
                logger.warn("Failed to auto-size column {}", i);
            }
        }
    }

    /**
     * UPDATED: Overload for franchise with correct count including Service column
     */
    private void autoSizeColumns(Sheet sheet, Boolean includeTaxes, String userRole) {
        int baseColumns = 18; // Base columns for franchise (added 1 for Service)
        int extraColumns = 0;

        if (includeTaxes && isAdminRole(userRole)) {
            extraColumns += 5; // Tax columns
        }

        extraColumns += 3; // Card details

        int totalColumns = baseColumns + extraColumns;

        for (int i = 0; i < totalColumns; i++) {
            try {
                sheet.autoSizeColumn(i);
            } catch (Exception e) {
                logger.warn("Failed to auto-size column {}", i);
            }
        }
    }
//    /**
//     * Populate merchant Excel row
//     */
//    private void populateMerchantExcelRow(Row row, MerchantTransactionReportDTO dto,
//                                          Boolean includeTaxes, String userRole) {
//        int colNum = 0;
//
//        row.createCell(colNum++).setCellValue(dto.getCustomTxnId() != null ? dto.getCustomTxnId().toString() : "");
//        row.createCell(colNum++).setCellValue(dto.getTxnId() != null ? dto.getTxnId() : "");
//        row.createCell(colNum++).setCellValue(dto.getActionOnBalance() != null ? dto.getActionOnBalance() : "");
//        row.createCell(colNum++).setCellValue(dto.getTxnDate() != null ? dto.getTxnDate().toString() : "");
//        row.createCell(colNum++).setCellValue(dto.getSettleDate() != null ? dto.getSettleDate().toString() : "");
//        row.createCell(colNum++).setCellValue(dto.getTxnAmount() != null ? dto.getTxnAmount().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getAuthCode() != null ? dto.getAuthCode() : "");
//        row.createCell(colNum++).setCellValue(dto.getTid() != null ? dto.getTid() : "");
//        row.createCell(colNum++).setCellValue(dto.getSettleAmount() != null ? dto.getSettleAmount().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getSystemFee() != null ? dto.getSystemFee().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getSettlementPercentage() != null ? dto.getSettlementPercentage().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getMerchantRate() != null ? dto.getMerchantRate().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getMerchantName() != null ? dto.getMerchantName() : "");
//        row.createCell(colNum++).setCellValue(dto.getState() != null ? dto.getState() : "");
//
//        if (dto.getFranchiseName() != null) {
//            row.createCell(colNum++).setCellValue(dto.getFranchiseName());
//        }
//
//        if (includeTaxes && isAdminRole(userRole)) {
//            row.createCell(colNum++).setCellValue(dto.getSystemFeeExGST() != null ? dto.getSystemFeeExGST().doubleValue() : 0.0);
//            row.createCell(colNum++).setCellValue(dto.getGstAmount() != null ? dto.getGstAmount().doubleValue() : 0.0);
//
//            row.createCell(colNum++).setCellValue(taxesService.getTaxes().getGst() != null ? taxesService.getTaxes().getGst().doubleValue() : 0.0);
//        }
//
//        row.createCell(colNum++).setCellValue(dto.getBrandType() != null ? dto.getBrandType() : "");
//        row.createCell(colNum++).setCellValue(dto.getCardType() != null ? dto.getCardType() : "");
//        row.createCell(colNum++).setCellValue(dto.getCardClassification() != null ? dto.getCardClassification() : "");
//    }
//
//    /**
//     * Populate franchise Excel row
//     */
//    private void populateFranchiseExcelRow(Row row, FranchiseTransactionReportDTO dto,
//                                           Boolean includeTaxes, String userRole) {
//        int colNum = 0;
//
//        row.createCell(colNum++).setCellValue(dto.getCustomTxnId() != null ? dto.getCustomTxnId().toString() : "");
//        row.createCell(colNum++).setCellValue(dto.getTxnId() != null ? dto.getTxnId() : "");
//        row.createCell(colNum++).setCellValue(dto.getActionOnBalance() != null ? dto.getActionOnBalance() : "");
//        row.createCell(colNum++).setCellValue(dto.getTxnDate() != null ? dto.getTxnDate().toString() : "");
//        row.createCell(colNum++).setCellValue(dto.getSettleDate() != null ? dto.getSettleDate().toString() : "");
//        row.createCell(colNum++).setCellValue(dto.getTxnAmount() != null ? dto.getTxnAmount().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getAuthCode() != null ? dto.getAuthCode() : "");
//        row.createCell(colNum++).setCellValue(dto.getTid() != null ? dto.getTid() : "");
//        row.createCell(colNum++).setCellValue(dto.getSettleAmount() != null ? dto.getSettleAmount().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getSystemFee() != null ? dto.getSystemFee().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getCommissionAmount() != null ? dto.getCommissionAmount().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getSettlementRate() != null ? dto.getSettlementRate().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getMerchantRate() != null ? dto.getMerchantRate().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getFranchiseRate() != null ? dto.getFranchiseRate().doubleValue() : 0.0);
//        row.createCell(colNum++).setCellValue(dto.getMerchantName() != null ? dto.getMerchantName() : "");
//        row.createCell(colNum++).setCellValue(dto.getFranchiseName() != null ? dto.getFranchiseName() : "");
//        row.createCell(colNum++).setCellValue(dto.getState() != null ? dto.getState() : "");
//
//        if (includeTaxes && isAdminRole(userRole)) {
//            row.createCell(colNum++).setCellValue(dto.getSystemFeeExGST() != null ? dto.getSystemFeeExGST().doubleValue() : 0.0);
//            row.createCell(colNum++).setCellValue(dto.getGstAmount() != null ? dto.getGstAmount().doubleValue() : 0.0);
//            row.createCell(colNum++).setCellValue(dto.getTdsAmount() != null ? dto.getTdsAmount().doubleValue() : 0.0);
//            row.createCell(colNum++).setCellValue(dto.getTdsPercentage() != null ? dto.getTdsPercentage().doubleValue() : 0.0);
//            row.createCell(colNum++).setCellValue(dto.getNetCommissionAmount() != null ? dto.getNetCommissionAmount().doubleValue() : 0.0);
//        }
//
//        row.createCell(colNum++).setCellValue(dto.getBrandType() != null ? dto.getBrandType() : "");
//        row.createCell(colNum++).setCellValue(dto.getCardType() != null ? dto.getCardType() : "");
//        row.createCell(colNum++).setCellValue(dto.getCardClassification() != null ? dto.getCardClassification() : "");
//    }

    /**
     * Helper methods for role checking
     */
    private boolean isAdminRole(String role) {
        if (role == null) return false;
        String normalized = role.toUpperCase();
        return normalized.contains("ADMIN") || normalized.contains("SUPER_ADMIN");
    }

    private boolean isMerchantRole(String role) {
        if (role == null) return false;
        String normalized = role.toUpperCase();
        return normalized.contains("MERCHANT");
    }



    // more new ones
    /**
     * Get merchant transaction type breakdown
     */
    public List<Map<String, Object>> getEnhancedMerchantTransactionTypeBreakdown(TransactionReportRequest request) {
        validateReportRequest(request);

        List<Object[]> breakdownData;

        if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
            breakdownData = merchantTransactionRepository.getMerchantTransactionTypeBreakdownBySettlementDate(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getMerchantId()
            );
        } else {
            breakdownData = merchantTransactionRepository.getMerchantTransactionTypeBreakdown(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getMerchantId()
            );
        }

        return breakdownData.stream()
                .map(row -> Map.of(
                        "transactionType", row[0],
                        "transactionCount", row[1],
                        "totalAmount", row[2]
                ))
                .collect(Collectors.toList());
    }


    /**
     * Get top merchants by commission for a franchise
     */
    public List<Map<String, Object>> getEnhancedTopMerchantsByCommission(TransactionReportRequest request) {
        validateReportRequest(request);

        List<Object[]> topMerchants;

        if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
            topMerchants = franchiseTransactionRepository.getTopMerchantsByCommissionBySettlementDate(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getFranchiseId()
            );
        } else {
            topMerchants = franchiseTransactionRepository.getTopMerchantsByCommission(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getFranchiseId()
            );
        }

        return topMerchants.stream()
                .map(row -> Map.of(
                        "merchantName", row[0],
                        "commission", row[1]
                ))
                .collect(Collectors.toList());
    }

// Keep all existing private helper methods as they are
    // Private helper methods

    private void validateReportRequest(TransactionReportRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new ValidationException("Start date and end date are required");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new ValidationException("Start date must be before end date");
        }

        long daysBetween = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
        if (daysBetween > MAX_DATE_RANGE_DAYS) {
            throw new ValidationException("Date range cannot exceed " + MAX_DATE_RANGE_DAYS + " days");
        }

        if (request.getSize() > MAX_PAGE_SIZE) {
            throw new ValidationException("Page size cannot exceed " + MAX_PAGE_SIZE);
        }
    }

    private Pageable createPageable(TransactionReportRequest request) {
        return PageRequest.of(
                Math.max(0, request.getPage()),
                Math.min(request.getSize(), MAX_PAGE_SIZE));
    }






    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return BigDecimal.ZERO;

        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }

        return BigDecimal.ZERO;
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0L;

        if (value instanceof Long) return (Long) value;

        if (value instanceof BigInteger) {
            return ((BigInteger) value).longValue();
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        return 0L;
    }

    /**
     * UPDATED: Export all merchant transactions to Excel with proper PAYOUT handling
     */
    @Transactional(readOnly = true)
    public ByteArrayInputStream exportAllMerchantTransactionsToExcel(
            TransactionReportRequest request, Boolean includeTaxes,
            String userRole, String merchantType) {

        logger.info("Exporting merchant transactions: dateFilter={}, merchantType={}, includeTaxes={}",
                request.getDateFilterType(), merchantType, includeTaxes);
        validateReportRequest(request);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Merchant Transactions");

            // Create header
            Row headerRow = sheet.createRow(0);
            createMerchantExcelHeader(headerRow, includeTaxes, userRole, merchantType);

            // Stream and process data
            AtomicInteger rowNum = new AtomicInteger(1);
            BigDecimal gstPercentage = includeTaxes ? getGstPercentage() : null;

            Stream<MerchantTransactionReportDTO> transactionStream = getTransactionStream(
                    request, merchantType);

            transactionStream.forEach(dto -> {
                try {
                    // Apply role-based filtering
                    MerchantTransactionReportDTO filteredDto = applyRoleBasedFiltering(dto, userRole);

                    // Calculate GST only for settlement transactions and admin users
                    if (includeTaxes && gstPercentage != null &&
                            !isWalletTransaction(filteredDto.getService())) {
                        calculateAndSetGst(filteredDto, gstPercentage);
                    }

                    // Create Excel row
                    Row row = sheet.createRow(rowNum.getAndIncrement());
                    populateMerchantExcelRow(row, filteredDto, includeTaxes, userRole);

                    // Log progress
                    if (rowNum.get() % 1000 == 0) {
                        logger.info("Processed {} rows", rowNum.get());
                    }
                } catch (Exception e) {
                    logger.error("Error processing transaction {}: {}",
                            dto.getCustomTxnId(), e.getMessage());
                    // Continue processing other rows
                }
            });

            // Auto-size columns
            autoSizeColumns(sheet, includeTaxes, userRole, merchantType);

            workbook.write(out);
            logger.info("Successfully exported {} transactions", rowNum.get() - 1);

            return new ByteArrayInputStream(out.toByteArray());

        } catch (Exception e) {
            logger.error("Error exporting merchant transactions", e);
            throw new BusinessException("Failed to export to Excel: " + e.getMessage());
        }
    }

    /**
     * Get appropriate transaction stream based on date filter type
     */
    private Stream<MerchantTransactionReportDTO> getTransactionStream(
            TransactionReportRequest request, String merchantType) {

        if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
            return merchantTransactionRepository
                    .streamAllMerchantTransactionsBySettlementDateFilters(
                            request.getStartDate(),
                            request.getEndDate(),
                            request.getTransactionType(),
                            merchantType);
        } else {
            return merchantTransactionRepository
                    .streamAllMerchantTransactionsByFilters(
                            request.getStartDate(),
                            request.getEndDate(),
                            request.getTransactionType(),
                            merchantType);
        }
    }

    /**
     * UPDATED: Export franchise transactions with proper handling
     */
    @Transactional(readOnly = true)
    public ByteArrayInputStream exportAllFranchiseTransactionsToExcel(
            TransactionReportRequest request, Boolean includeTaxes, String userRole) {

        logger.info("Exporting franchise transactions: dateFilter={}, includeTaxes={}",
                request.getDateFilterType(), includeTaxes);
        validateReportRequest(request);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Franchise Transactions");

            // Create header
            Row headerRow = sheet.createRow(0);
            createFranchiseExcelHeader(headerRow, includeTaxes, userRole);

            // Process in batches for better performance
            AtomicInteger rowNum = new AtomicInteger(1);
            List<FranchiseTransactionDetails> batch = new ArrayList<>();

            Stream<FranchiseTransactionDetails> transactionStream = getFranchiseTransactionStream(request);

            transactionStream.forEach(ftd -> {
                batch.add(ftd);

                // Process batch every 500 records
                if (batch.size() >= 500) {
                    processFranchiseBatchToExcel(
                            workbook, sheet, batch, rowNum, includeTaxes, userRole);
                    batch.clear();
                }
            });

            // Process remaining batch
            if (!batch.isEmpty()) {
                processFranchiseBatchToExcel(
                        workbook, sheet, batch, rowNum, includeTaxes, userRole);
            }

            // Auto-size columns
            autoSizeColumns(sheet, includeTaxes, userRole);

            workbook.write(out);
            logger.info("Successfully exported {} franchise transactions", rowNum.get() - 1);

            return new ByteArrayInputStream(out.toByteArray());

        } catch (Exception e) {
            logger.error("Error exporting franchise transactions", e);
            throw new BusinessException("Failed to export to Excel: " + e.getMessage());
        }
    }

    /**
     * Get franchise transaction stream based on date filter
     */
    private Stream<FranchiseTransactionDetails> getFranchiseTransactionStream(
            TransactionReportRequest request) {

        if ("SETTLEMENT_DATE".equals(request.getDateFilterType())) {
            return franchiseTransactionRepository
                    .streamAllFranchiseTransactionsBySettlementDateFilters(
                            request.getStartDate(),
                            request.getEndDate(),
                            request.getTransactionType());
        } else {
            return franchiseTransactionRepository
                    .streamAllFranchiseTransactionsByFilters(
                            request.getStartDate(),
                            request.getEndDate(),
                            request.getTransactionType());
        }
    }

    /**
     * UPDATED: Process franchise batch with proper vendor transaction lookup
     */
    private void processFranchiseBatchToExcel(
            Workbook workbook, Sheet sheet,
            List<FranchiseTransactionDetails> batch, AtomicInteger rowNum,
            Boolean includeTaxes, String userRole) {

        try {
            // Collect vendor transaction IDs (only for non-PAYOUT transactions)
            List<String> vendorTransactionIds = batch.stream()
                    .filter(ftd -> !isWalletTransaction(ftd.getService()))
                    .map(FranchiseTransactionDetails::getMerchantTransactionDetail)
                    .filter(Objects::nonNull)
                    .map(MerchantTransactionDetails::getVendorTransactionId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // Batch fetch vendor transactions
            Map<String, VendorTransactions> vendorTransactionMap = new HashMap<>();
            if (!vendorTransactionIds.isEmpty()) {
                List<VendorTransactions> vendorTransactions =
                        franchiseTransactionRepository.findByTransactionReferenceIdIn(vendorTransactionIds);
                vendorTransactionMap = vendorTransactions.stream()
                        .collect(Collectors.toMap(
                                VendorTransactions::getTransactionReferenceId,
                                vt -> vt,
                                (existing, replacement) -> existing
                        ));
            }

            // Process each transaction
            final Map<String, VendorTransactions> finalVendorMap = vendorTransactionMap;
            batch.forEach(ftd -> {
                try {
                    FranchiseTransactionReportDTO dto =
                            mapToFranchiseTransactionReportDTO(ftd, finalVendorMap);
                    dto = applyFranchiseRoleBasedFiltering(dto, userRole);

                    Row row = sheet.createRow(rowNum.getAndIncrement());
                    populateFranchiseExcelRow(row, dto, includeTaxes, userRole);
                } catch (Exception e) {
                    logger.error("Error processing franchise transaction {}: {}",
                            ftd.getTransactionId(), e.getMessage());
                    // Continue with other transactions
                }
            });

            logger.debug("Processed batch, total rows: {}", rowNum.get());

        } catch (Exception e) {
            logger.error("Error processing franchise batch", e);
            // Don't throw - let the export continue with remaining batches
        }
    }

    /**
     * Helper: Check if transaction is wallet-related
     */
    private boolean isWalletTransaction(String service) {
        return service != null &&
                (service.equalsIgnoreCase("PAYOUT") ||
                        service.equalsIgnoreCase("PAYOUT_REFUND"));
    }

    /**
     * Null-safe cell value setter
     */
    private void setCellValue(Row row, int colNum, Object value) {
        Cell cell = row.createCell(colNum);

        if (value == null) {
            cell.setCellValue("");
            return;
        }

        if (value instanceof BigDecimal) {
            cell.setCellValue(((BigDecimal) value).doubleValue());
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof LocalDateTime) {
            cell.setCellValue(value.toString());
        } else {
            cell.setCellValue(value.toString());
        }
    }


}