package com.project2.ism.Repository;

import com.project2.ism.DTO.ReportDTO.FranchiseTransactionReportDTO;
import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.VendorTransactions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface FranchiseTransDetRepository extends JpaRepository<FranchiseTransactionDetails, Long> {

    boolean existsByVendorTransactionId(String vendorTransactionId);

    Optional<FranchiseTransactionDetails> findByTransactionId(Long transactionId);

    Optional<FranchiseTransactionDetails> findByVendorTransactionId(String vendorTransactionId);

    Long countByTransactionDateBetween(LocalDateTime localDateTime, LocalDateTime localDateTime1);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM FranchiseTransactionDetails m WHERE m.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByTransactionDateBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);


    Long countByTranStatusAndTransactionDateBetween(String success, LocalDateTime localDateTime, LocalDateTime localDateTime1);



//new for reports lets see if they work

//    @Query("SELECT ftd FROM FranchiseTransactionDetails ftd WHERE " +
//            "ftd.transactionDate BETWEEN :startDate AND :endDate " +
//            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
//            "AND (:status IS NULL OR ftd.tranStatus = :status) " +
//            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType) " +
//            "ORDER BY ftd.transactionDate DESC")
//    Page<FranchiseTransactionDetails> findFranchiseTransactionsByFilters(
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate,
//            @Param("franchiseId") Long franchiseId,
//            @Param("status") String status,
//            @Param("transactionType") String transactionType,
//            Pageable pageable);
//
//    // Query based on settlement date instead of transaction date
//    @Query("SELECT ftd FROM FranchiseTransactionDetails ftd WHERE " +
//            "ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
//            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
//            "AND (:status IS NULL OR ftd.tranStatus = :status) " +
//            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType) " +
//            "ORDER BY ftd.updatedDateAndTimeOfTransaction DESC")
//    Page<FranchiseTransactionDetails> findFranchiseTransactionsBySettlementDateFilters(
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate,
//            @Param("franchiseId") Long franchiseId,
//            @Param("status") String status,
//            @Param("transactionType") String transactionType,
//            Pageable pageable);
//


    // Transaction date based
    @Query("SELECT ftd " +
            "FROM FranchiseTransactionDetails ftd " +
            "LEFT JOIN FETCH ftd.merchantTransactionDetail mtd " +
            "LEFT JOIN FETCH ftd.franchise f " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            //"AND (:status IS NULL OR ftd.tranStatus = :status) " +
            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType) " +
            "ORDER BY ftd.transactionDate DESC")
    Page<FranchiseTransactionDetails> findFranchiseTransactionsByFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId,
            @Param("status") String status,
            @Param("transactionType") String transactionType,
            Pageable pageable);

    // Settlement date based
    @Query("SELECT ftd " +
            "FROM FranchiseTransactionDetails ftd " +
            "LEFT JOIN FETCH ftd.merchantTransactionDetail mtd " +
            "LEFT JOIN FETCH ftd.franchise f " +
            "WHERE ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            //"AND (:status IS NULL OR ftd.tranStatus = :status) " +
            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType) " +
            "ORDER BY ftd.updatedDateAndTimeOfTransaction DESC")
    Page<FranchiseTransactionDetails> findFranchiseTransactionsBySettlementDateFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId,
            @Param("status") String status,
            @Param("transactionType") String transactionType,
            Pageable pageable);

    // For fetching vendor transactions in batch
    @Query("SELECT vt FROM VendorTransactions vt " +
            "WHERE vt.transactionReferenceId IN :vendorTransactionIds")
    List<VendorTransactions> findByTransactionReferenceIdIn(
            @Param("vendorTransactionIds") List<String> vendorTransactionIds);

    // ============================================================================
// FRANCHISE TRANSACTION REPOSITORY
// ============================================================================

    @Query("SELECT " +
            // Settlement/Commission transactions (CREDIT)
            "COUNT(CASE WHEN ftd.transactionType = 'CREDIT' AND ftd.service IN ('Settlement', 'COMMISSION') THEN 1 END) as settlementCount, " +
            "COALESCE(SUM(CASE WHEN ftd.transactionType = 'CREDIT' AND ftd.service IN ('Settlement', 'COMMISSION') THEN ftd.amount END), 0) as totalSettlementAmount, " +
            "COALESCE(SUM(CASE WHEN ftd.transactionType = 'CREDIT' AND ftd.service IN ('Settlement', 'COMMISSION') THEN ftd.netAmount END), 0) as totalCommissionEarned, " +

            // Payout transactions (DEBIT)
            "COUNT(CASE WHEN ftd.service = 'PAYOUT' THEN 1 END) as payoutCount, " +
            "COALESCE(SUM(CASE WHEN ftd.service = 'PAYOUT' THEN ABS(ftd.netAmount) END), 0) as totalPayoutAmount, " +
            "COALESCE(SUM(CASE WHEN ftd.service = 'PAYOUT' THEN ABS(ftd.amount - ftd.netAmount) END), 0) as totalPayoutFees, " +
            "COUNT(CASE WHEN ftd.service = 'PAYOUT' AND ftd.tranStatus = 'SUCCESS' THEN 1 END) as successfulPayouts, " +
            "COUNT(CASE WHEN ftd.service = 'PAYOUT' AND ftd.tranStatus = 'FAILED' THEN 1 END) as failedPayouts, " +
            "COUNT(CASE WHEN ftd.service = 'PAYOUT' AND ftd.tranStatus = 'PENDING' THEN 1 END) as pendingPayouts, " +

            // Refund transactions
            "COUNT(CASE WHEN ftd.service = 'PAYOUT_REFUND' THEN 1 END) as refundCount, " +
            "COALESCE(SUM(CASE WHEN ftd.service = 'PAYOUT_REFUND' THEN ftd.amount END), 0) as totalRefundAmount, " +

            // Net position
            "COALESCE(SUM(CASE WHEN ftd.transactionType = 'CREDIT' THEN ftd.amount END), 0) as netCreditAmount, " +
            "COALESCE(SUM(CASE WHEN ftd.transactionType = 'DEBIT' THEN ftd.amount END), 0) as netDebitAmount, " +
            "COALESCE(SUM(ftd.amount), 0) as netBalance, " +

            // Overall stats
            "COUNT(ftd) as totalTransactions, " +
            "COUNT(CASE WHEN ftd.tranStatus IN ('SUCCESS', 'SETTLED') THEN 1 END) as successCount, " +
            "COUNT(CASE WHEN ftd.tranStatus = 'FAILED' THEN 1 END) as failureCount, " +
            "COUNT(CASE WHEN ftd.tranStatus = 'PENDING' THEN 1 END) as pendingCount, " +
            "COUNT(DISTINCT CASE WHEN ftd.tranStatus IN ('SUCCESS', 'SETTLED') AND ftd.service IN ('Settlement', 'COMMISSION') THEN ftd.vendorName END) as activeMerchants " +

            "FROM FranchiseTransactionDetails ftd WHERE " +
            "ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType)")
    Map<String, Object> getFranchiseTransactionSummary(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId,
            @Param("status") String status,
            @Param("transactionType") String transactionType);

    // Settlement date version
    @Query("SELECT " +
            // Settlement/Commission transactions (CREDIT)
            "COUNT(CASE WHEN ftd.transactionType = 'CREDIT' AND ftd.service IN ('Settlement', 'COMMISSION') THEN 1 END) as settlementCount, " +
            "COALESCE(SUM(CASE WHEN ftd.transactionType = 'CREDIT' AND ftd.service IN ('Settlement', 'COMMISSION') THEN ftd.amount END), 0) as totalSettlementAmount, " +
            "COALESCE(SUM(CASE WHEN ftd.transactionType = 'CREDIT' AND ftd.service IN ('Settlement', 'COMMISSION') THEN ftd.netAmount END), 0) as totalCommissionEarned, " +

            // Payout transactions (DEBIT)
            "COUNT(CASE WHEN ftd.service = 'PAYOUT' THEN 1 END) as payoutCount, " +
            "COALESCE(SUM(CASE WHEN ftd.service = 'PAYOUT' THEN ABS(ftd.netAmount) END), 0) as totalPayoutAmount, " +
            "COALESCE(SUM(CASE WHEN ftd.service = 'PAYOUT' THEN ABS(ftd.amount - ftd.netAmount) END), 0) as totalPayoutFees, " +
            "COUNT(CASE WHEN ftd.service = 'PAYOUT' AND ftd.tranStatus = 'SUCCESS' THEN 1 END) as successfulPayouts, " +
            "COUNT(CASE WHEN ftd.service = 'PAYOUT' AND ftd.tranStatus = 'FAILED' THEN 1 END) as failedPayouts, " +
            "COUNT(CASE WHEN ftd.service = 'PAYOUT' AND ftd.tranStatus = 'PENDING' THEN 1 END) as pendingPayouts, " +

            // Refund transactions
            "COUNT(CASE WHEN ftd.service = 'PAYOUT_REFUND' THEN 1 END) as refundCount, " +
            "COALESCE(SUM(CASE WHEN ftd.service = 'PAYOUT_REFUND' THEN ftd.amount END), 0) as totalRefundAmount, " +

            // Net position
            "COALESCE(SUM(CASE WHEN ftd.transactionType = 'CREDIT' THEN ftd.amount END), 0) as netCreditAmount, " +
            "COALESCE(SUM(CASE WHEN ftd.transactionType = 'DEBIT' THEN ftd.amount END), 0) as netDebitAmount, " +
            "COALESCE(SUM(ftd.amount), 0) as netBalance, " +

            // Overall stats
            "COUNT(ftd) as totalTransactions, " +
            "COUNT(CASE WHEN ftd.tranStatus IN ('SUCCESS', 'SETTLED') THEN 1 END) as successCount, " +
            "COUNT(CASE WHEN ftd.tranStatus = 'FAILED' THEN 1 END) as failureCount, " +
            "COUNT(CASE WHEN ftd.tranStatus = 'PENDING' THEN 1 END) as pendingCount, " +
            "COUNT(DISTINCT CASE WHEN ftd.tranStatus IN ('SUCCESS', 'SETTLED') AND ftd.service IN ('Settlement', 'COMMISSION') THEN ftd.vendorName END) as activeMerchants " +

            "FROM FranchiseTransactionDetails ftd WHERE " +
            "ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType)")
    Map<String, Object> getFranchiseTransactionSummaryBySettlementDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId,
            @Param("status") String status,
            @Param("transactionType") String transactionType);


// export excel
    // In FranchiseTransactionRepository
    @Query("SELECT ftd FROM FranchiseTransactionDetails ftd " +
            "LEFT JOIN FETCH ftd.merchantTransactionDetail mtd " +
            "LEFT JOIN FETCH ftd.franchise f " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType) " +
            "ORDER BY ftd.transactionDate DESC")
    Stream<FranchiseTransactionDetails> streamAllFranchiseTransactionsByFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("transactionType") String transactionType);

    @Query("SELECT ftd FROM FranchiseTransactionDetails ftd " +
            "LEFT JOIN FETCH ftd.merchantTransactionDetail mtd " +
            "LEFT JOIN FETCH ftd.franchise f " +
            "WHERE ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType) " +
            "ORDER BY ftd.updatedDateAndTimeOfTransaction DESC")
    Stream<FranchiseTransactionDetails> streamAllFranchiseTransactionsBySettlementDateFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("transactionType") String transactionType);

    // 3. Performance by merchant (via link to MerchantTransactionDetails)
    @Query("SELECT mtd.merchant.id, mtd.merchant.businessName, COUNT(ftd), SUM(ftd.amount), SUM(ftd.netAmount) " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND ftd.tranStatus = 'SETTLED' " +
            "GROUP BY mtd.merchant.id, mtd.merchant.businessName " +
            "ORDER BY SUM(ftd.netAmount) DESC")
    List<Object[]> getFranchiseMerchantPerformance(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);


    @Query("SELECT mtd.merchant.businessName, SUM(ftd.netAmount) as commission " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND ftd.tranStatus = 'SETTLED' " +
            "GROUP BY mtd.merchant.businessName " +
            "ORDER BY commission DESC")
    List<Object[]> getTopMerchantsByCommission(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);


    @Query("SELECT DATE(ftd.transactionDate), COUNT(ftd), SUM(ftd.amount), SUM(ftd.netAmount) " +
            "FROM FranchiseTransactionDetails ftd " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "GROUP BY DATE(ftd.transactionDate) " +
            "ORDER BY DATE(ftd.transactionDate)")
    List<Object[]> getFranchiseDailyTrends(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);

    @Query("SELECT ftd.remarks, COUNT(ftd) " +
            "FROM FranchiseTransactionDetails ftd " +
            "WHERE ftd.tranStatus != 'SETTLED' " +
            "AND ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "GROUP BY ftd.remarks " +
            "ORDER BY COUNT(ftd) DESC")
    List<Object[]> getFranchiseFailureReasons(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

//    @Query("SELECT " +
//            "COUNT(ftd) as totalTransactions, " +
//            "COALESCE(SUM(ftd.amount), 0) as totalAmount, " +
//            "COALESCE(SUM(ftd.netAmount), 0) as totalCommission, " +
//            "COALESCE(SUM(ftd.amount - ftd.netAmount), 0) as totalNetAmount, " +
//            "COALESCE(AVG(ftd.amount), 0) as averageAmount, " +
//            "COUNT(CASE WHEN ftd.tranStatus = 'SETTLED' THEN 1 END) as successCount, " +
//            "COUNT(CASE WHEN ftd.tranStatus != 'SETTLED' THEN 1 END) as failureCount, " +
//            "COUNT(DISTINCT CASE WHEN ftd.tranStatus = 'SETTLED' THEN ftd.vendorName END) as activeMerchants " +
//            "FROM FranchiseTransactionDetails ftd WHERE " +
//            "ftd.transactionDate BETWEEN :startDate AND :endDate " +
//            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
//            //"AND (:status IS NULL OR ftd.tranStatus = :status) " +
//            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType)")
//    Map<String, Object> getFranchiseTransactionSummary(
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate,
//            @Param("franchiseId") Long franchiseId,
//            @Param("status") String status,
//            @Param("transactionType") String transactionType);
//
//
//
//    // Summary based on settlement date
//    @Query("SELECT " +
//            "COUNT(ftd) as totalTransactions, " +
//            "COALESCE(SUM(ftd.amount), 0) as totalAmount, " +
//            "COALESCE(SUM(ftd.netAmount), 0) as totalCommission, " +
//            "COALESCE(SUM(ftd.amount - ftd.netAmount), 0) as totalNetAmount, " +
//            "COALESCE(AVG(ftd.amount), 0) as averageAmount, " +
//            "COUNT(CASE WHEN ftd.tranStatus = 'SETTLED' THEN 1 END) as successCount, " +
//            "COUNT(CASE WHEN ftd.tranStatus != 'SETTLED' THEN 1 END) as failureCount, " +
//            "COUNT(DISTINCT CASE WHEN ftd.tranStatus = 'SETTLED' THEN ftd.vendorName END) as activeMerchants " +
//            "FROM FranchiseTransactionDetails ftd WHERE " +
//            "ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
//            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
//            //"AND (:status IS NULL OR ftd.tranStatus = :status) " +
//            "AND (:transactionType IS NULL OR ftd.transactionType = :transactionType)")
//    Map<String, Object> getFranchiseTransactionSummaryBySettlementDate(
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate,
//            @Param("franchiseId") Long franchiseId,
//            @Param("status") String status,
//            @Param("transactionType") String transactionType);


    // NEW QUERIES - Add these to your existing FranchiseTransDetRepository.java



    // Merchant performance based on settlement date
    @Query("SELECT mtd.merchant.id, mtd.merchant.businessName, COUNT(ftd), SUM(ftd.amount), SUM(ftd.netAmount) " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "WHERE ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND ftd.tranStatus = 'SETTLED' " +
            "GROUP BY mtd.merchant.id, mtd.merchant.businessName " +
            "ORDER BY SUM(ftd.netAmount) DESC")
    List<Object[]> getFranchiseMerchantPerformanceBySettlementDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);

    // Top merchants by commission based on settlement date
    @Query("SELECT mtd.merchant.businessName, SUM(ftd.netAmount) as commission " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "WHERE ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND ftd.tranStatus = 'SETTLED' " +
            "GROUP BY mtd.merchant.businessName " +
            "ORDER BY commission DESC")
    List<Object[]> getTopMerchantsByCommissionBySettlementDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);

    // Daily trends based on settlement date
    @Query("SELECT DATE(ftd.updatedDateAndTimeOfTransaction), COUNT(ftd), SUM(ftd.amount), SUM(ftd.netAmount) " +
            "FROM FranchiseTransactionDetails ftd " +
            "WHERE ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "GROUP BY DATE(ftd.updatedDateAndTimeOfTransaction) " +
            "ORDER BY DATE(ftd.updatedDateAndTimeOfTransaction)")
    List<Object[]> getFranchiseDailyTrendsBySettlementDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);

///////////


    // 1. COMPREHENSIVE TRANSACTION DETAILS REPORT
    @Query("SELECT " +
            "ftd.transactionDate as txnDate, " +
            "ftd.amount as txnAmount, " +
            "ftd.updatedDateAndTimeOfTransaction as settleDate, " +
            "vt.authCode as authCode, " +
            "vt.tid as tid, " +
            "CASE WHEN ftd.amount > 0 THEN ((ftd.amount - ftd.netAmount) / ftd.amount * 100) ELSE 0 END as settlementPercentage, " +
            "mtd.netAmount as settleAmount, " +
            "mtd.grossCharge as systemFee, " +
            "CASE WHEN ftd.amount > 0 THEN (mtd.grossCharge / ftd.amount * 100) ELSE 0 END as retailorPercentage, " +
            "ftd.netAmount as commissionAmount, " +
            "vt.cardType as cardType, " +
            "vt.brandType as brandType, " +
            "vt.cardClassification as cardClassification, " +
            "mtd.merchant.businessName as merchantName, " +
            "ftd.franchise.franchiseName, " +
            "ftd.franchise.status as state " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:status IS NULL OR ftd.tranStatus = :status) " +
            "AND (:cardType IS NULL OR vt.cardType = :cardType) " +
            "AND (:brandType IS NULL OR vt.brandType = :brandType) " +
            "ORDER BY ftd.transactionDate DESC")
    Page<Object[]> getDetailedTransactionReport(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId,
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("cardType") String cardType,
            @Param("brandType") String brandType,
            Pageable pageable);

    // 2. SETTLEMENT DATE BASED DETAILED REPORT
    @Query("SELECT " +
            "ftd.transactionDate as txnDate, " +
            "ftd.amount as txnAmount, " +
            "ftd.updatedDateAndTimeOfTransaction as settleDate, " +
            "vt.authCode as authCode, " +
            "vt.tid as tid, " +
            "CASE WHEN ftd.amount > 0 THEN ((ftd.amount - ftd.netAmount) / ftd.amount * 100) ELSE 0 END as settlementPercentage, " +
            "mtd.netAmount as settleAmount, " +
            "mtd.grossCharge as systemFee, " +
            "CASE WHEN ftd.amount > 0 THEN (mtd.grossCharge / ftd.amount * 100) ELSE 0 END as retailorPercentage, " +
            "mtd.charge as commissionAmount, " +
            "vt.cardType as cardType, " +
            "vt.brandType as brandType, " +
            "vt.cardClassification as cardClassification, " +
            "mtd.merchant.businessName as merchantName, " +
            "ftd.franchise.franchiseName, " +
            "ftd.franchise.status as state " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:status IS NULL OR ftd.tranStatus = :status) " +
            "AND (:cardType IS NULL OR vt.cardType = :cardType) " +
            "AND (:brandType IS NULL OR vt.brandType = :brandType) " +
            "ORDER BY ftd.updatedDateAndTimeOfTransaction DESC")
    Page<Object[]> getDetailedTransactionReportBySettlementDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId,
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("cardType") String cardType,
            @Param("brandType") String brandType,
            Pageable pageable);

    // 3. AGGREGATED SUMMARY BY CARD TYPE AND BRAND

    @Query("SELECT " +
            "vt.cardType, " +
            "vt.brandType, " +
            "COUNT(ftd) as transactionCount, " +
            "CAST(SUM(ftd.amount) as bigdecimal), " +
            "CAST(SUM(ftd.netAmount) as bigdecimal), " +
            "CAST(SUM(mtd.charge) as bigdecimal), " +
            "CAST(SUM(mtd.grossCharge) as bigdecimal), " +
            "CAST(AVG(ftd.amount) as bigdecimal), " +
            "CAST(AVG(CASE WHEN ftd.amount > 0 THEN (mtd.grossCharge / ftd.amount * 100) ELSE 0 END) as bigdecimal) " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND ftd.tranStatus = 'SETTLED' " +
            "GROUP BY vt.cardType, vt.brandType " +
            "ORDER BY SUM(ftd.amount) DESC")
    List<Object[]> getCardTypeBrandSummary(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);

    @Query("SELECT " +
            "vt.cardType, " +
            "vt.brandType, " +
            "COUNT(ftd) as transactionCount, " +
            "CAST(SUM(ftd.amount) as bigdecimal), " +
            "CAST(SUM(ftd.netAmount) as bigdecimal), " +
            "CAST(SUM(mtd.charge) as bigdecimal), " +
            "CAST(SUM(mtd.grossCharge) as bigdecimal), " +
            "CAST(AVG(ftd.amount) as bigdecimal), " +
            "CAST(AVG(CASE WHEN ftd.amount > 0 THEN (mtd.grossCharge / ftd.amount * 100) ELSE 0 END) as bigdecimal) " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE ftd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND ftd.tranStatus = 'SETTLED' " +
            "GROUP BY vt.cardType, vt.brandType " +
            "ORDER BY SUM(ftd.amount) DESC")
    List<Object[]> getCardTypeBrandSummaryBySettleDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);


    // 4. DAILY TRANSACTION SUMMARY WITH BREAKDOWN
    @Query("SELECT " +
            "DATE(ftd.transactionDate) as txnDate, " +
            "COUNT(ftd) as totalTransactions, " +
            "CAST(SUM(ftd.amount) as bigdecimal) as totalAmount, " +
            "CAST(SUM(mtd.netAmount) as bigdecimal) as totalSettleAmount, " +
            "CAST(SUM(ftd.netAmount) as bigdecimal) as totalCommission, " +
            "CAST(SUM(mtd.charge) as bigdecimal) as totalSystemFee, " +
            "CAST(SUM(mtd.grossCharge) as bigdecimal) as totalMerchantFee, " +
            "COUNT(CASE WHEN ftd.tranStatus = 'SETTLED' THEN 1 END) as settledCount, " +
            "COUNT(CASE WHEN ftd.tranStatus != 'SETTLED' THEN 1 END) as failedCount, " +
            "CAST(AVG(ftd.amount) as bigdecimal) as averageAmount, " +
            "COUNT(DISTINCT mtd.merchant.id) as uniqueMerchants " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "GROUP BY DATE(ftd.transactionDate) " +
            "ORDER BY DATE(ftd.transactionDate) DESC")
    List<Object[]> getDailySummaryReport(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);

    // 5. MERCHANT WISE DETAILED PERFORMANCE
    @Query("SELECT " +
            "mtd.merchant.id as merchantId, " +
            "mtd.merchant.businessName as merchantName, " +
            "COUNT(ftd) as transactionCount, " +
            "SUM(ftd.amount) as totalAmount, " +
            "SUM(mtd.netAmount) as totalSettleAmount, " +
            "SUM(ftd.netAmount) as totalCommission, " +
            "SUM(mtd.grossCharge) as totalMDR, " +
            "AVG(ftd.amount) as averageAmount, " +
            "COUNT(CASE WHEN ftd.tranStatus = 'SETTLED' THEN 1 END) as successCount, " +
            "COUNT(CASE WHEN ftd.tranStatus != 'SETTLED' THEN 1 END) as failureCount, " +
            "CASE WHEN COUNT(ftd) > 0 THEN (COUNT(CASE WHEN ftd.tranStatus = 'SETTLED' THEN 1 END) * 100.0 / COUNT(ftd)) ELSE 0 END as successRate " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "GROUP BY mtd.merchant.id, mtd.merchant.businessName " +
            "ORDER BY SUM(ftd.amount) DESC")
    List<Object[]> getMerchantWiseDetailedPerformance(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);

    // 6. FRANCHISE COMPARISON REPORT
    @Query("SELECT " +
            "ftd.franchise.id as franchiseId, " +
            "ftd.franchise.franchiseName, " +
            "ftd.franchise.status as state, " +
            "COUNT(ftd) as transactionCount, " +
            "SUM(ftd.amount) as totalAmount, " +
            "SUM(mtd.netAmount) as totalSettleAmount, " +
            "SUM(ftd.netAmount) as totalCommission, " +
            "COUNT(DISTINCT mtd.merchant.id) as uniqueMerchants, " +
            "AVG(ftd.amount) as averageAmount, " +
            "COUNT(CASE WHEN ftd.tranStatus = 'SETTLED' THEN 1 END) as successCount, " +
            "CASE WHEN COUNT(ftd) > 0 THEN (COUNT(CASE WHEN ftd.tranStatus = 'SETTLED' THEN 1 END) * 100.0 / COUNT(ftd)) ELSE 0 END as successRate " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "GROUP BY ftd.franchise.id, ftd.franchise.franchiseName, ftd.franchise.status " +
            "ORDER BY SUM(ftd.amount) DESC")
    List<Object[]> getFranchiseComparisonReport(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // 7. TERMINAL WISE TRANSACTION ANALYSIS
    @Query("SELECT " +
            "vt.tid as terminalId, " +
            "mtd.merchant.businessName as merchantName, " +
            "ftd.franchise.franchiseName, " +
            "COUNT(ftd) as transactionCount, " +
            "SUM(ftd.amount) as totalAmount, " +
            "SUM(ftd.netAmount) as totalSettleAmount, " +
            "SUM(mtd.charge) as totalCommission, " +
            "AVG(ftd.amount) as averageAmount, " +
            "COUNT(CASE WHEN ftd.tranStatus = 'SETTLED' THEN 1 END) as successCount, " +
            "COUNT(CASE WHEN ftd.tranStatus != 'SETTLED' THEN 1 END) as failureCount " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "GROUP BY vt.tid, mtd.merchant.businessName, ftd.franchise.franchiseName " +
            "ORDER BY SUM(ftd.amount) DESC")
    List<Object[]> getTerminalWiseAnalysis(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId,
            @Param("merchantId") Long merchantId);

    // 8. TRANSACTION TREND ANALYSIS (HOURLY BREAKDOWN)
    @Query("SELECT " +
            "DATE(ftd.transactionDate) as txnDate, " +
            "HOUR(ftd.transactionDate) as txnHour, " +
            "COUNT(ftd) as transactionCount, " +
            "SUM(ftd.amount) as totalAmount, " +
            "AVG(ftd.amount) as averageAmount, " +
            "COUNT(CASE WHEN ftd.tranStatus = 'SETTLED' THEN 1 END) as successCount " +
            "FROM FranchiseTransactionDetails ftd " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "GROUP BY DATE(ftd.transactionDate), HOUR(ftd.transactionDate) " +
            "ORDER BY DATE(ftd.transactionDate) DESC, HOUR(ftd.transactionDate)")
    List<Object[]> getHourlyTransactionTrend(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);

    // 9. FAILED TRANSACTION ANALYSIS
    @Query("SELECT " +
            "ftd.transactionDate, " +
            "ftd.amount, " +
            "ftd.tranStatus, " +
            "ftd.failureRemarks, " +
            "vt.errorCode, " +
            "vt.pgErrorMessage, " +
            "vt.tid, " +
            "vt.cardType, " +
            "vt.brandType, " +
            "mtd.merchant.businessName as merchantName, " +
            "ftd.franchise.franchiseName " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND ftd.tranStatus != 'SETTLED' " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "ORDER BY ftd.transactionDate DESC")
    Page<Object[]> getFailedTransactionAnalysis(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId,
            @Param("merchantId") Long merchantId,
            Pageable pageable);

    // 10. SETTLEMENT DELAY ANALYSIS
    @Query("SELECT " +
            "ftd.transactionDate, " +
            "ftd.updatedDateAndTimeOfTransaction as settlementDate, " +
            "TIMESTAMPDIFF(HOUR, ftd.transactionDate, ftd.updatedDateAndTimeOfTransaction) as settlementDelayHours, " +
            "ftd.amount, " +
            "ftd.netAmount, " +
            "vt.tid, " +
            "mtd.merchant.businessName as merchantName, " +
            "ftd.franchise.franchiseName " +
            "FROM FranchiseTransactionDetails ftd " +
            "JOIN ftd.merchantTransactionDetail mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE ftd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND ftd.tranStatus = 'SETTLED' " +
            "AND ftd.updatedDateAndTimeOfTransaction IS NOT NULL " +
            "AND (:franchiseId IS NULL OR ftd.franchise.id = :franchiseId) " +
            "ORDER BY TIMESTAMPDIFF(HOUR, ftd.transactionDate, ftd.updatedDateAndTimeOfTransaction) DESC")
    List<Object[]> getSettlementDelayAnalysis(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("franchiseId") Long franchiseId);
}