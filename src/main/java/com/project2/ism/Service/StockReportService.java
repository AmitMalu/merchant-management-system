package com.project2.ism.Service;

// StockReportService.java

import com.project2.ism.DTO.ReportDTO.StockReportDTO;
import com.project2.ism.DTO.ReportDTO.StockReportResponseDTO;
import com.project2.ism.DTO.ReportDTO.StockReportSummaryDTO;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Repository.ProductSerialsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StockReportService {

    @Autowired
    private ProductSerialsRepository productSerialNumbersRepository;

    @Transactional(readOnly = true)
    public StockReportResponseDTO generateStockReport(
            String status,
            Long productId,
            Long merchantId,
            Long franchiseId,
            LocalDateTime fromDate,
            LocalDateTime toDate
    ) {
        List<ProductSerialNumbers> serialNumbers;

        // Fetch data based on filters
        if (status != null || productId != null || merchantId != null || franchiseId != null || fromDate != null || toDate != null) {
            serialNumbers = productSerialNumbersRepository.findByFilters(
                    status, productId, merchantId, franchiseId, fromDate, toDate
            );
        } else {
            serialNumbers = productSerialNumbersRepository.findAll();
        }

        // Transform to DTOs
        List<StockReportDTO> reportDTOs = serialNumbers.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        // Generate summary
        StockReportSummaryDTO summary = generateSummary(serialNumbers);

        return new StockReportResponseDTO(reportDTOs, summary);
    }

    private StockReportDTO convertToDTO(ProductSerialNumbers psn) {
        StockReportDTO dto = new StockReportDTO();

        // Basic PSN fields
        dto.setSerialNumberId(psn.getId());
        dto.setSid(psn.getSid());
        dto.setMid(psn.getMid());
        dto.setTid(psn.getTid());
        dto.setVpaid(psn.getVpaid());
        dto.setMobNumber(psn.getMobNumber());

        // Product info
        if (psn.getProduct() != null) {
            dto.setProductId(psn.getProduct().getId());
            dto.setProductName(psn.getProduct().getProductName());
            dto.setProductCode(psn.getProduct().getProductCode());
            dto.setBrand(psn.getProduct().getBrand());
            dto.setModel(psn.getProduct().getModel());

            if (psn.getProduct().getProductCategory() != null) {
                dto.setProductCategory(psn.getProduct().getProductCategory().getCategoryName());
            }
        }

        // Determine status
        String status = determineStatus(psn);
        dto.setStatus(status);

        // Allocation info - Check both direct allocation and outward transaction
        if (psn.getMerchant() != null) {
            dto.setAllocationType("MERCHANT");
            dto.setAllocatedToId(psn.getMerchant().getId());
            dto.setAllocatedToName(psn.getMerchant().getBusinessName());
            dto.setAllocatedToType("MERCHANT");
        } else if (psn.getFranchise() != null) {
            dto.setAllocationType("FRANCHISE");
            dto.setAllocatedToId(psn.getFranchise().getId());
            dto.setAllocatedToName(psn.getFranchise().getFranchiseName());
            dto.setAllocatedToType("FRANCHISE");
        } else if (psn.getOutwardTransaction() != null) {
            // Check outward transaction for allocation
            if (psn.getOutwardTransaction().getMerchant() != null) {
                dto.setAllocationType("MERCHANT");
                dto.setAllocatedToId(psn.getOutwardTransaction().getMerchant().getId());
                dto.setAllocatedToName(psn.getOutwardTransaction().getMerchant().getBusinessName());
                dto.setAllocatedToType("MERCHANT");
            } else if (psn.getOutwardTransaction().getFranchise() != null) {
                dto.setAllocationType("FRANCHISE");
                dto.setAllocatedToId(psn.getOutwardTransaction().getFranchise().getId());
                dto.setAllocatedToName(psn.getOutwardTransaction().getFranchise().getFranchiseName());
                dto.setAllocatedToType("FRANCHISE");
            }
        } else {
            dto.setAllocationType("NONE");
        }

        // Inward Transaction info
        if (psn.getInwardTransaction() != null) {
            dto.setInwardTransactionId(psn.getInwardTransaction().getId());
            dto.setInwardReceivedDate(psn.getInwardTransaction().getReceivedDate());
            dto.setInvoiceNumber(psn.getInwardTransaction().getInvoiceNumber());
            dto.setProductCondition(psn.getInwardTransaction().getProductCondition());
        }

        // Outward Transaction info
        if (psn.getOutwardTransaction() != null) {
            dto.setOutwardTransactionId(psn.getOutwardTransaction().getId());
            dto.setDispatchDate(psn.getOutwardTransaction().getDispatchDate());
            dto.setDeliveryNumber(psn.getOutwardTransaction().getDeliveryNumber());
            dto.setOutwardReceivedDate(psn.getOutwardTransaction().getReceivedDate());
        }

        // Return Transaction info
        if (psn.getReturnTransaction() != null) {
            dto.setReturnTransactionId(psn.getReturnTransaction().getId());
            dto.setReturnDate(psn.getReturnTransaction().getReturnDate());
            dto.setReturnNumber(psn.getReturnTransaction().getReturnNumber());
            dto.setReturnReason(psn.getReturnTransaction().getReturnReason());
            dto.setReturnCondition(psn.getReturnTransaction().getReturnCondition());
        }

        // Product Distribution info
        if (psn.getProductDistribution() != null) {
            dto.setProductDistributionId(psn.getProductDistribution().getId());
        }

        // PSN specific dates
        dto.setReceivedDate(psn.getReceivedDate());
        dto.setReceivedDateByFranchise(psn.getReceivedDateByFranchise());

        return dto;
    }

    private String determineStatus(ProductSerialNumbers psn) {
        // Priority order: ReturnTransaction > OutwardTransaction > Available
        if (psn.getReturnTransaction() != null) {
            return "RETURNED";
        }
        if (psn.getOutwardTransaction() != null || psn.getMerchant() != null || psn.getFranchise() != null) {
            return "ALLOCATED";
        }
        return "AVAILABLE";
    }

    private StockReportSummaryDTO generateSummary(List<ProductSerialNumbers> serialNumbers) {
        StockReportSummaryDTO summary = new StockReportSummaryDTO();

        summary.setTotalStock((long) serialNumbers.size());

        // Status counts
        long available = serialNumbers.stream()
                .filter(psn -> determineStatus(psn).equals("AVAILABLE"))
                .count();
        summary.setAvailableStock(available);

        long allocated = serialNumbers.stream()
                .filter(psn -> determineStatus(psn).equals("ALLOCATED"))
                .count();
        summary.setAllocatedStock(allocated);

        long returned = serialNumbers.stream()
                .filter(psn -> determineStatus(psn).equals("RETURNED"))
                .count();
        summary.setReturnedStock(returned);

        // Allocation type counts
        long toMerchants = serialNumbers.stream()
                .filter(psn -> psn.getMerchant() != null ||
                        (psn.getOutwardTransaction() != null && psn.getOutwardTransaction().getMerchant() != null))
                .count();
        summary.setAllocatedToMerchants(toMerchants);

        long toFranchises = serialNumbers.stream()
                .filter(psn -> psn.getFranchise() != null ||
                        (psn.getOutwardTransaction() != null && psn.getOutwardTransaction().getFranchise() != null))
                .count();
        summary.setAllocatedToFranchises(toFranchises);

        // Product-wise count
        Map<String, Long> productWise = serialNumbers.stream()
                .filter(psn -> psn.getProduct() != null)
                .collect(Collectors.groupingBy(
                        psn -> psn.getProduct().getProductName(),
                        Collectors.counting()
                ));
        summary.setProductWiseCount(productWise);

        // Status-wise count
        Map<String, Long> statusWise = serialNumbers.stream()
                .collect(Collectors.groupingBy(
                        this::determineStatus,
                        Collectors.counting()
                ));
        summary.setStatusWiseCount(statusWise);

        return summary;
    }
}