//// 3. Enhanced ReportService.java
//package com.project2.ism.Service;
//
//import com.project2.ism.DTO.*;
//import com.project2.ism.Model.FranchiseSettlementBatch.BatchStatus.*;
//import com.project2.ism.Repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.stream.Collectors;
//
//import static com.project2.ism.Model.FranchiseSettlementBatch.BatchStatus.*;
//
//
//@Service
//public class ReportService {
//
//    @Autowired
//    private FranchiseRepository franchiseRepository;
//
//    @Autowired
//    private MerchantRepository merchantRepository;
//
//    @Autowired
//    private FranchiseTransDetRepository franchiseTransactionDetailsRepository;
//
//    @Autowired
//    private MerchantTransDetRepository merchantTransactionDetailsRepository;
//
//    @Autowired
//    private FranchiseSettlementBatchRepository franchiseSettlementBatchRepository;
//
//    @Autowired
//    private MerchantSettlementBatchRepository merchantSettlementBatchRepository;
//
//    public DashboardSummaryDTO getDashboardSummary(LocalDate fromDate, LocalDate toDate) {
//        DashboardSummaryDTO dto = new DashboardSummaryDTO();
//
//        // Basic stats
//        dto.setTotalFranchises(franchiseRepository.count());
//        dto.setTotalMerchants(merchantRepository.count());
//        dto.setTotalDirectMerchants(merchantRepository.countDirectMerchants());
//        dto.setTotalFranchiseMerchants(merchantRepository.countFranchiseMerchants());
//
//        // Time-based transaction stats
//        dto.setTotalTransactionsToday(getTransactionCount(LocalDate.now(), LocalDate.now()));
//        dto.setTotalTransactionsThisMonth(getTransactionCount(fromDate, toDate));
//        dto.setTotalRevenueThisMonth(getTotalRevenue(fromDate, toDate));
//        dto.setSuccessfulTransactions(getSuccessfulTransactionCount(fromDate, toDate));
//        dto.setFailedTransactions(getFailedTransactionCount(fromDate, toDate));
//
//        // Settlement stats
//        dto.setPendingSettlements(getPendingSettlementsCount());
//        dto.setCompletedSettlementsToday(getCompletedSettlementsCount(LocalDate.now()));
//
//        // Wallet balances
//        dto.setTotalFranchiseWalletBalance(franchiseRepository.sumWalletBalances());
//        dto.setTotalDirectMerchantWalletBalance(merchantRepository.sumDirectMerchantWallets());
//
//        return dto;
//    }
//
//    private Long getTransactionCount(LocalDate fromDate, LocalDate toDate) {
//        Long franchiseCount = franchiseTransactionDetailsRepository.countByTransactionDateBetween(
//                fromDate.atStartOfDay(),
//                toDate.plusDays(1).atStartOfDay()
//        );
//        Long merchantCount = merchantTransactionDetailsRepository.countByTransactionDateBetween(
//                fromDate.atStartOfDay(),
//                toDate.plusDays(1).atStartOfDay()
//        );
//
//        return (franchiseCount != null ? franchiseCount : 0L) +
//                (merchantCount != null ? merchantCount : 0L);
//    }
//
//    private BigDecimal getTotalRevenue(LocalDate fromDate, LocalDate toDate) {
//        BigDecimal franchiseRevenue = franchiseTransactionDetailsRepository.sumAmountByTransactionDateBetween(
//                fromDate.atStartOfDay(),
//                toDate.plusDays(1).atStartOfDay()
//        );
//        BigDecimal merchantRevenue = merchantTransactionDetailsRepository.sumAmountByTransactionDateBetween(
//                fromDate.atStartOfDay(),
//                toDate.plusDays(1).atStartOfDay()
//        );
//
//        return (franchiseRevenue != null ? franchiseRevenue : BigDecimal.ZERO)
//                .add(merchantRevenue != null ? merchantRevenue : BigDecimal.ZERO);
//    }
//
//    private Long getSuccessfulTransactionCount(LocalDate fromDate, LocalDate toDate) {
//        Long franchiseSuccess = franchiseTransactionDetailsRepository.countByTranStatusAndTransactionDateBetween(
//                "COMPLETED", fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay()
//        );
//        Long merchantSuccess = merchantTransactionDetailsRepository.countByTranStatusAndTransactionDateBetween(
//                "COMPLETED", fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay()
//        );
//
//        return (franchiseSuccess != null ? franchiseSuccess : 0L) +
//                (merchantSuccess != null ? merchantSuccess : 0L);
//    }
//
//    private Long getFailedTransactionCount(LocalDate fromDate, LocalDate toDate) {
//        Long franchiseFailed = franchiseTransactionDetailsRepository.countByTranStatusAndTransactionDateBetween(
//                "FAILED", fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay()
//        );
//        Long merchantFailed = merchantTransactionDetailsRepository.countByTranStatusAndTransactionDateBetween(
//                "FAILED", fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay()
//        );
//
//        return (franchiseFailed != null ? franchiseFailed : 0L) +
//                (merchantFailed != null ? merchantFailed : 0L);
//    }
//
//    private Long getPendingSettlementsCount() {
//        Long franchisePending = franchiseSettlementBatchRepository.countByStatus(PROCESSING);
//
//        Long merchantPending = merchantSettlementBatchRepository.countByStatus("PROCESSING");
//
//
//        return (franchisePending != null ? franchisePending : 0L) +
//                (merchantPending != null ? merchantPending : 0L);
//    }
//
//    private Long getCompletedSettlementsCount(LocalDate date) {
//        LocalDateTime startOfDay = date.atStartOfDay();
//        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
//
//        Long franchiseCompleted = franchiseSettlementBatchRepository.countByStatusAndProcessingCompletedAtBetween(
//                COMPLETED, startOfDay, endOfDay
//        );
//        Long merchantCompleted = merchantSettlementBatchRepository.countByStatusAndProcessingCompletedAtBetween(
//                "COMPLETED", startOfDay, endOfDay
//        );
//
//        return (franchiseCompleted != null ? franchiseCompleted : 0L) +
//                (merchantCompleted != null ? merchantCompleted : 0L);
//    }
//
//    public Map<String, Object> getDashboardCharts(LocalDate fromDate, LocalDate toDate) {
//        Map<String, Object> charts = new HashMap<>();
//
//        charts.put("transactionTrend", getTransactionTrend(fromDate, toDate));
//        charts.put("cardTypeDistribution", getCardTypeDistribution(fromDate, toDate));
//        charts.put("settlementStatus", getSettlementStatusDistribution());
//        charts.put("topMerchants", getTopMerchantsByRevenue(fromDate, toDate));
//
//        return charts;
//    }
//
//    private List<TransactionTrendDTO> getTransactionTrend(LocalDate fromDate, LocalDate toDate) {
//        List<TransactionTrendDTO> trend = new ArrayList<>();
//        LocalDate current = fromDate;
//
//        while (!current.isAfter(toDate)) {
//            Long count = getTransactionCount(current, current);
//            BigDecimal amount = getTotalRevenue(current, current);
//            trend.add(new TransactionTrendDTO(current, count, amount));
//            current = current.plusDays(1);
//        }
//
//        return trend;
//    }
//
//    private List<CardTypeDistributionDTO> getCardTypeDistribution(LocalDate fromDate, LocalDate toDate) {
//        // This would need custom repository methods to get card type data
//        // Placeholder implementation
//        List<CardTypeDistributionDTO> distribution = new ArrayList<>();
//        Long totalTransactions = getTransactionCount(fromDate, toDate);
//
//        if (totalTransactions > 0) {
//            // Example distribution - replace with actual repository calls
//            distribution.add(new CardTypeDistributionDTO("VISA", totalTransactions * 40 / 100, new BigDecimal("40.0")));
//            distribution.add(new CardTypeDistributionDTO("MASTERCARD", totalTransactions * 35 / 100, new BigDecimal("35.0")));
//            distribution.add(new CardTypeDistributionDTO("RUPAY", totalTransactions * 25 / 100, new BigDecimal("25.0")));
//        }
//
//        return distribution;
//    }
//
//    private List<SettlementStatusDTO> getSettlementStatusDistribution() {
//        List<SettlementStatusDTO> distribution = new ArrayList<>();
//
//        // Franchise settlements
//        Long franchisePending = franchiseSettlementBatchRepository.countByStatus(PROCESSING);
//        Long franchiseCompleted = franchiseSettlementBatchRepository.countByStatus(COMPLETED);
//        Long franchiseFailed = franchiseSettlementBatchRepository.countByStatus(FAILED);
//
//        // Merchant settlements
//
//        Long merchantPending = merchantSettlementBatchRepository.countByStatus("PROCESSING");
//        Long merchantCompleted = merchantSettlementBatchRepository.countByStatus("COMPLETED");
//        Long merchantFailed = merchantSettlementBatchRepository.countByStatus("FAILED");
//
//
//        distribution.add(new SettlementStatusDTO("PROCESSING",
//                (franchisePending != null ? franchisePending : 0L) + (merchantPending != null ? merchantPending : 0L)));
//        distribution.add(new SettlementStatusDTO("COMPLETED",
//                (franchiseCompleted != null ? franchiseCompleted : 0L) + (merchantCompleted != null ? merchantCompleted : 0L)));
//        distribution.add(new SettlementStatusDTO("FAILED",
//                (franchiseFailed != null ? franchiseFailed : 0L) + (merchantFailed != null ? merchantFailed : 0L)));
//
//        return distribution;
//    }
//
//    private List<TopMerchantDTO> getTopMerchantsByRevenue(LocalDate fromDate, LocalDate toDate) {
//        // This would need a custom repository method to get top merchants by revenue
//        // Placeholder implementation - you'll need to implement the repository method
//        List<TopMerchantDTO> topMerchants = new ArrayList<>();
//
//        // Example - replace with actual repository call
//        // List<Object[]> results = merchantTransactionDetailsRepository.findTopMerchantsByRevenue(
//        //     fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay(), 10);
//
//        // for (Object[] result : results) {
//        //     String merchantName = (String) result[0];
//        //     String merchantId = (String) result[1];
//        //     BigDecimal revenue = (BigDecimal) result[2];
//        //     Long transactionCount = (Long) result[3];
//        //     topMerchants.add(new TopMerchantDTO(merchantName, merchantId, revenue, transactionCount));
//        // }
//
//        return topMerchants;
//    }
//}
