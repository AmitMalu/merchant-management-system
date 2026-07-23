package com.project2.ism.Repository;

import com.project2.ism.DTO.PayoutDTO.PayoutServiceCountDTO;
import com.project2.ism.DTO.PayoutDTO.PayoutTransactionReportDTO;
import com.project2.ism.DTO.ReportDTO.MerchantTransactionReportDTO;
import com.project2.ism.Model.MerchantTransactionDetails;
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
public interface MerchantTransDetRepository extends JpaRepository<MerchantTransactionDetails,Long> {
    Optional<MerchantTransactionDetails> findByTransactionId(Long transactionId);

    boolean existsByVendorTransactionId(String vendorTransactionId);

    Long countByTransactionDateBetween(LocalDateTime localDateTime, LocalDateTime localDateTime1);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM MerchantTransactionDetails m WHERE m.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumAmountByTransactionDateBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    Long countByTranStatusAndTransactionDateBetween(String success, LocalDateTime localDateTime, LocalDateTime localDateTime1);

    @Query("""
        SELECT new com.project2.ism.DTO.ReportDTO.MerchantTransactionReportDTO(
            mtd.transactionId,
            mtd.vendorTransactionId,
            mtd.actionOnBalance,
            mtd.transactionDate,
            mtd.amount,
            mtd.updatedDateAndTimeOfTransaction,
            vt.authCode,
            vt.tid,
            mtd.netAmount,
            mtd.grossCharge,
            ftd.netAmount,
            mtd.charge,
            vt.brandType,
            vt.cardType,
            vt.cardClassification,
            mtd.merchant.businessName,
            ftd.franchise.franchiseName,
            mtd.tranStatus,
            mtd.service,
            mtd.remarks,
            mtd.balBeforeTran,
            mtd.balAfterTran
        )
        FROM MerchantTransactionDetails mtd
        LEFT JOIN VendorTransactions vt
            ON vt.transactionReferenceId = mtd.vendorTransactionId
        LEFT JOIN FranchiseTransactionDetails ftd
            ON ftd.vendorTransactionId = mtd.vendorTransactionId
            AND ftd.franchise.id = mtd.merchant.franchise.id
        WHERE mtd.transactionDate BETWEEN :startDate AND :endDate
        AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId)
        AND (:status IS NULL OR mtd.tranStatus = :status)
        AND (:transactionType IS NULL OR mtd.transactionType = :transactionType)
        ORDER BY mtd.transactionDate DESC
        """)
    Page<MerchantTransactionReportDTO> findPendingMerchantTransactionsByFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("transactionType") String transactionType,
            Pageable pageable
    );

    @Query("SELECT new com.project2.ism.DTO.ReportDTO.MerchantTransactionReportDTO(" +
            "mtd.transactionId, mtd.vendorTransactionId, mtd.actionOnBalance, mtd.transactionDate, mtd.amount, " +
            "mtd.updatedDateAndTimeOfTransaction, vt.authCode, vt.tid, " +
            "mtd.netAmount, mtd.grossCharge, ftd.netAmount, mtd.charge, " +
            "vt.brandType, vt.cardType, vt.cardClassification, " +
            "mtd.merchant.businessName, ftd.franchise.franchiseName, mtd.tranStatus, mtd.service, " +
            "mtd.remarks, mtd.balBeforeTran, mtd.balAfterTran) " +
            "FROM MerchantTransactionDetails mtd " +
            "LEFT JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "LEFT JOIN FranchiseTransactionDetails ftd ON ftd.vendorTransactionId = mtd.vendorTransactionId " +
            "AND ftd.franchise.id = mtd.merchant.franchise.id " +
            "WHERE mtd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:transactionType IS NULL OR mtd.transactionType = :transactionType) " +
            "ORDER BY mtd.transactionDate DESC")
    Page<MerchantTransactionReportDTO> findMerchantTransactionsByFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("transactionType") String transactionType,
            Pageable pageable);

    @Query("SELECT new com.project2.ism.DTO.ReportDTO.MerchantTransactionReportDTO(" +
            "mtd.transactionId, mtd.vendorTransactionId, mtd.actionOnBalance, mtd.transactionDate, mtd.amount, " +
            "mtd.updatedDateAndTimeOfTransaction, vt.authCode, vt.tid, " +
            "mtd.netAmount, mtd.grossCharge, ftd.netAmount, mtd.charge, " +
            "vt.brandType, vt.cardType, vt.cardClassification, " +
            "mtd.merchant.businessName, ftd.franchise.franchiseName, mtd.tranStatus, mtd.service, " +
            "mtd.remarks, mtd.balBeforeTran, mtd.balAfterTran) " +
            "FROM MerchantTransactionDetails mtd " +
            "LEFT JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "LEFT JOIN FranchiseTransactionDetails ftd ON ftd.vendorTransactionId = mtd.vendorTransactionId " +
            "AND ftd.franchise.id = mtd.merchant.franchise.id " +
            "WHERE mtd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:transactionType IS NULL OR mtd.transactionType = :transactionType) " +
            "ORDER BY mtd.updatedDateAndTimeOfTransaction DESC")
    Page<MerchantTransactionReportDTO> findMerchantTransactionsBySettlementDateFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("transactionType") String transactionType,
            Pageable pageable);


    // ============================================================================
// MERCHANT TRANSACTION REPOSITORY
// ============================================================================

    @Query("SELECT " +
            // Settlement transactions (CREDIT)
            "COUNT(CASE WHEN mtd.transactionType = 'CREDIT' AND mtd.service = 'Settlement' THEN 1 END) as settlementCount, " +
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'CREDIT' AND mtd.service = 'Settlement' THEN mtd.amount END), 0) as totalTransactionAmount, " +
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'CREDIT' AND mtd.service = 'Settlement' THEN mtd.netAmount END), 0) as totalSettlementReceived, " +
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'CREDIT' AND mtd.service = 'Settlement' THEN mtd.grossCharge END), 0) as totalChargesPaid, " +

            // Payout transactions (DEBIT)
            "COUNT(CASE WHEN mtd.service = 'PAYOUT' THEN 1 END) as payoutCount, " +
            "COALESCE(SUM(CASE WHEN mtd.service = 'PAYOUT' THEN ABS(mtd.netAmount) END), 0) as totalPayoutAmount, " +
            "COALESCE(SUM(CASE WHEN mtd.service = 'PAYOUT' THEN ABS(mtd.amount - mtd.netAmount) END), 0) as totalPayoutFees, " +
            "COUNT(CASE WHEN mtd.service = 'PAYOUT' AND mtd.tranStatus = 'SUCCESS' THEN 1 END) as successfulPayouts, " +
            "COUNT(CASE WHEN mtd.service = 'PAYOUT' AND mtd.tranStatus = 'FAILED' THEN 1 END) as failedPayouts, " +
            "COUNT(CASE WHEN mtd.service = 'PAYOUT' AND mtd.tranStatus = 'PENDING' THEN 1 END) as pendingPayouts, " +

            // Refund transactions
            "COUNT(CASE WHEN mtd.service = 'PAYOUT_REFUND' THEN 1 END) as refundCount, " +
            "COALESCE(SUM(CASE WHEN mtd.service = 'PAYOUT_REFUND' THEN mtd.amount END), 0) as totalRefundAmount, " +

            // Net position
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'CREDIT' THEN mtd.amount END), 0) as netCreditAmount, " +
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'DEBIT' THEN mtd.amount END), 0) as netDebitAmount, " +
            "COALESCE(SUM(mtd.amount), 0) as netBalance, " +

            // Overall stats
            "COUNT(mtd) as totalTransactions, " +
            "COUNT(CASE WHEN mtd.tranStatus IN ('SUCCESS', 'SETTLED') THEN 1 END) as successCount, " +
            "COUNT(CASE WHEN mtd.tranStatus = 'FAILED' THEN 1 END) as failureCount, " +
            "COUNT(CASE WHEN mtd.tranStatus = 'PENDING' THEN 1 END) as pendingCount " +

            "FROM MerchantTransactionDetails mtd WHERE " +
            "mtd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:transactionType IS NULL OR mtd.transactionType = :transactionType)")
    Map<String, Object> getMerchantTransactionSummary(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("transactionType") String transactionType);

    // Settlement date version
    @Query("SELECT " +
            // Settlement transactions (CREDIT)
            "COUNT(CASE WHEN mtd.transactionType = 'CREDIT' AND mtd.service = 'Settlement' THEN 1 END) as settlementCount, " +
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'CREDIT' AND mtd.service = 'Settlement' THEN mtd.amount END), 0) as totalTransactionAmount, " +
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'CREDIT' AND mtd.service = 'Settlement' THEN mtd.netAmount END), 0) as totalSettlementReceived, " +
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'CREDIT' AND mtd.service = 'Settlement' THEN mtd.grossCharge END), 0) as totalChargesPaid, " +

            // Payout transactions (DEBIT)
            "COUNT(CASE WHEN mtd.service = 'PAYOUT' THEN 1 END) as payoutCount, " +
            "COALESCE(SUM(CASE WHEN mtd.service = 'PAYOUT' THEN ABS(mtd.netAmount) END), 0) as totalPayoutAmount, " +
            "COALESCE(SUM(CASE WHEN mtd.service = 'PAYOUT' THEN ABS(mtd.amount - mtd.netAmount) END), 0) as totalPayoutFees, " +
            "COUNT(CASE WHEN mtd.service = 'PAYOUT' AND mtd.tranStatus = 'SUCCESS' THEN 1 END) as successfulPayouts, " +
            "COUNT(CASE WHEN mtd.service = 'PAYOUT' AND mtd.tranStatus = 'FAILED' THEN 1 END) as failedPayouts, " +
            "COUNT(CASE WHEN mtd.service = 'PAYOUT' AND mtd.tranStatus = 'PENDING' THEN 1 END) as pendingPayouts, " +

            // Refund transactions
            "COUNT(CASE WHEN mtd.service = 'PAYOUT_REFUND' THEN 1 END) as refundCount, " +
            "COALESCE(SUM(CASE WHEN mtd.service = 'PAYOUT_REFUND' THEN mtd.amount END), 0) as totalRefundAmount, " +

            // Net position
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'CREDIT' THEN mtd.amount END), 0) as netCreditAmount, " +
            "COALESCE(SUM(CASE WHEN mtd.transactionType = 'DEBIT' THEN mtd.amount END), 0) as netDebitAmount, " +
            "COALESCE(SUM(mtd.amount), 0) as netBalance, " +

            // Overall stats
            "COUNT(mtd) as totalTransactions, " +
            "COUNT(CASE WHEN mtd.tranStatus IN ('SUCCESS', 'SETTLED') THEN 1 END) as successCount, " +
            "COUNT(CASE WHEN mtd.tranStatus = 'FAILED' THEN 1 END) as failureCount, " +
            "COUNT(CASE WHEN mtd.tranStatus = 'PENDING' THEN 1 END) as pendingCount " +

            "FROM MerchantTransactionDetails mtd WHERE " +
            "mtd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:transactionType IS NULL OR mtd.transactionType = :transactionType)")
    Map<String, Object> getMerchantTransactionSummaryBySettlementDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("transactionType") String transactionType);

    // for export excel
    // In MerchantTransactionRepository
    /**
     * Stream all merchant transactions with proper handling of different transaction types
     * Handles both settlement transactions (with vendor data) and wallet transactions (PAYOUT/REFUND)
     */
    @Query("SELECT new com.project2.ism.DTO.ReportDTO.MerchantTransactionReportDTO(" +
            "mtd.transactionId, " +
            "mtd.vendorTransactionId, " +
            "mtd.actionOnBalance, " +
            "mtd.transactionDate, " +
            "mtd.amount, " +
            "mtd.updatedDateAndTimeOfTransaction, " +
            // Vendor fields - will be null for PAYOUT/REFUND
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.authCode END, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.tid END, " +
            // Transaction amounts
            "mtd.netAmount, " +
            "mtd.grossCharge, " +
            // Franchise commission - null for PAYOUT/REFUND
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE ftd.netAmount END, " +
            "mtd.charge, " +
            // Card details - null for PAYOUT/REFUND
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.brandType END, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.cardType END, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.cardClassification END, " +
            // Business names
            "mtd.merchant.businessName, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE ftd.franchise.franchiseName END, " +
            "mtd.tranStatus, " +
            "mtd.service, " +
            "mtd.remarks, " +
            "mtd.balBeforeTran, " +
            "mtd.balAfterTran) " +
            "FROM MerchantTransactionDetails mtd " +
            "LEFT JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "AND mtd.service NOT IN ('PAYOUT', 'PAYOUT_REFUND') " +
            "LEFT JOIN FranchiseTransactionDetails ftd ON ftd.vendorTransactionId = mtd.vendorTransactionId " +
            "AND ftd.franchise.id = mtd.merchant.franchise.id " +
            "AND mtd.service NOT IN ('PAYOUT', 'PAYOUT_REFUND') " +
            "WHERE mtd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:transactionType IS NULL OR mtd.transactionType = :transactionType) " +
            "AND (:merchantType IS NULL OR " +
            "     (:merchantType = 'DIRECT' AND mtd.merchant.franchise IS NULL) OR " +
            "     (:merchantType = 'FRANCHISE' AND mtd.merchant.franchise IS NOT NULL)) " +
            "ORDER BY mtd.transactionDate DESC")
    Stream<MerchantTransactionReportDTO> streamAllMerchantTransactionsByFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId,
            @Param("transactionType") String transactionType,
            @Param("merchantType") String merchantType);

    @Query("SELECT new com.project2.ism.DTO.ReportDTO.MerchantTransactionReportDTO(" +
            "mtd.transactionId, " +
            "mtd.vendorTransactionId, " +
            "mtd.actionOnBalance, " +
            "mtd.transactionDate, " +
            "mtd.amount, " +
            "mtd.updatedDateAndTimeOfTransaction, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.authCode END, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.tid END, " +
            "mtd.netAmount, " +
            "mtd.grossCharge, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE ftd.netAmount END, " +
            "mtd.charge, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.brandType END, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.cardType END, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE vt.cardClassification END, " +
            "mtd.merchant.businessName, " +
            "CASE WHEN mtd.service IN ('PAYOUT', 'PAYOUT_REFUND') THEN null ELSE ftd.franchise.franchiseName END, " +
            "mtd.tranStatus, " +
            "mtd.service, " +
            "mtd.remarks, " +
            "mtd.balBeforeTran, " +
            "mtd.balAfterTran) " +
            "FROM MerchantTransactionDetails mtd " +
            "LEFT JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "AND mtd.service NOT IN ('PAYOUT', 'PAYOUT_REFUND') " +
            "LEFT JOIN FranchiseTransactionDetails ftd ON ftd.vendorTransactionId = mtd.vendorTransactionId " +
            "AND ftd.franchise.id = mtd.merchant.franchise.id " +
            "AND mtd.service NOT IN ('PAYOUT', 'PAYOUT_REFUND') " +
            "WHERE mtd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:transactionType IS NULL OR mtd.transactionType = :transactionType) " +
            "AND (:merchantType IS NULL OR " +
            "     (:merchantType = 'DIRECT' AND mtd.merchant.franchise IS NULL) OR " +
            "     (:merchantType = 'FRANCHISE' AND mtd.merchant.franchise IS NOT NULL)) " +
            "ORDER BY mtd.updatedDateAndTimeOfTransaction DESC")
    Stream<MerchantTransactionReportDTO> streamAllMerchantTransactionsBySettlementDateFilters(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId,
            @Param("transactionType") String transactionType,
            @Param("merchantType") String merchantType);

    @Query("SELECT mtd.transactionType, COUNT(mtd), SUM(mtd.amount) " +
            "FROM MerchantTransactionDetails mtd WHERE " +
            "mtd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "GROUP BY mtd.transactionType")
    List<Object[]> getMerchantTransactionTypeBreakdown(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId);

    // Transaction type breakdown based on settlement date
    @Query("SELECT mtd.transactionType, COUNT(mtd), SUM(mtd.amount) " +
            "FROM MerchantTransactionDetails mtd WHERE " +
            "mtd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "GROUP BY mtd.transactionType")
    List<Object[]> getMerchantTransactionTypeBreakdownBySettlementDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId);

// NEW QUERIES - Add these to your existing MerchantTransDetRepository.java

//1
    // MERCHANT-ONLY TRANSACTION DETAILS REPORT (Transaction Date Based)
    @Query("SELECT " +
            "mtd.transactionDate as txnDate, " +
            "mtd.amount as txnAmount, " +
            "mtd.updatedDateAndTimeOfTransaction as settleDate, " +
            "vt.authCode as authCode, " +
            "vt.tid as tid, " +
            "CASE WHEN mtd.amount > 0 THEN ((mtd.amount - mtd.netAmount) / mtd.amount * 100) ELSE 0 END as settlementPercentage, " +
            "mtd.netAmount as settleAmount, " +
            "COALESCE(mtd.charge, 0) as systemFee, " +
            "CASE WHEN mtd.amount > 0 THEN (COALESCE(mtd.charge,0) / mtd.amount * 100) ELSE 0 END as systemFeePercentage, " +
            "vt.cardType as cardType, " +
            "vt.brandType as brandType, " +
            "vt.cardClassification as cardClassification, " +
            "mtd.merchant.businessName as merchantName " +
            "FROM MerchantTransactionDetails mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE mtd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:status IS NULL OR mtd.tranStatus = :status) " +
            "AND (:cardType IS NULL OR vt.cardType = :cardType) " +
            "AND (:brandType IS NULL OR vt.brandType = :brandType) " +
            "ORDER BY mtd.transactionDate DESC")
    Page<Object[]> getMerchantTransactionReportByTxnDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("cardType") String cardType,
            @Param("brandType") String brandType,
            Pageable pageable);

    // MERCHANT-ONLY TRANSACTION DETAILS REPORT (Settlement Date Based)
    @Query("SELECT " +
            "mtd.transactionDate as txnDate, " +
            "mtd.amount as txnAmount, " +
            "mtd.updatedDateAndTimeOfTransaction as settleDate, " +
            "vt.authCode as authCode, " +
            "vt.tid as tid, " +
            "CASE WHEN mtd.amount > 0 THEN ((mtd.amount - mtd.netAmount) / mtd.amount * 100) ELSE 0 END as settlementPercentage, " +
            "mtd.netAmount as settleAmount, " +
            "COALESCE(mtd.charge, 0) as systemFee, " +
            "CASE WHEN mtd.amount > 0 THEN (COALESCE(mtd.charge,0) / mtd.amount * 100) ELSE 0 END as systemFeePercentage, " +
            "vt.cardType as cardType, " +
            "vt.brandType as brandType, " +
            "vt.cardClassification as cardClassification, " +
            "mtd.merchant.businessName as merchantName " +
            "FROM MerchantTransactionDetails mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE mtd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND (:status IS NULL OR mtd.tranStatus = :status) " +
            "AND (:cardType IS NULL OR vt.cardType = :cardType) " +
            "AND (:brandType IS NULL OR vt.brandType = :brandType) " +
            "ORDER BY mtd.updatedDateAndTimeOfTransaction DESC")
    Page<Object[]> getMerchantTransactionReportBySettleDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("cardType") String cardType,
            @Param("brandType") String brandType,
            Pageable pageable);

 //2
    // AGGREGATED SUMMARY BY CARD TYPE AND BRAND (Transaction Date)
    @Query("SELECT " +
            "vt.cardType, " +
            "vt.brandType, " +
            "COUNT(mtd) as transactionCount, " +
            "CAST(SUM(mtd.amount) as bigdecimal), " +
            "CAST(SUM(mtd.netAmount) as bigdecimal), " +
            "CAST(SUM(mtd.charge) as bigdecimal), " +
            "CAST(AVG(mtd.amount) as bigdecimal), " +
            "CAST(AVG(CASE WHEN mtd.amount > 0 THEN (mtd.charge / mtd.amount * 100) ELSE 0 END) as bigdecimal) " +
            "FROM MerchantTransactionDetails mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE mtd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND mtd.tranStatus = 'SETTLED' " +
            "GROUP BY vt.cardType, vt.brandType " +
            "ORDER BY SUM(mtd.amount) DESC")
    List<Object[]> getMerchantCardTypeBrandSummaryByTxnDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId);

    // AGGREGATED SUMMARY BY CARD TYPE AND BRAND (Settlement Date)
    @Query("SELECT " +
            "vt.cardType, " +
            "vt.brandType, " +
            "COUNT(mtd) as transactionCount, " +
            "CAST(SUM(mtd.amount) as bigdecimal), " +
            "CAST(SUM(mtd.netAmount) as bigdecimal), " +
            "CAST(SUM(mtd.charge) as bigdecimal), " +
            "CAST(AVG(mtd.amount) as bigdecimal), " +
            "CAST(AVG(CASE WHEN mtd.amount > 0 THEN (mtd.charge / mtd.amount * 100) ELSE 0 END) as bigdecimal) " +
            "FROM MerchantTransactionDetails mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE mtd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "AND mtd.tranStatus = 'SETTLED' " +
            "GROUP BY vt.cardType, vt.brandType " +
            "ORDER BY SUM(mtd.amount) DESC")
    List<Object[]> getMerchantCardTypeBrandSummaryBySettleDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId);

 //3

    // DAILY TRANSACTION SUMMARY (Merchant Only, Transaction Date)
    @Query("SELECT " +
            "DATE(mtd.transactionDate) as txnDate, " +
            "COUNT(mtd) as totalTransactions, " +
            "CAST(SUM(mtd.amount) as bigdecimal) as totalAmount, " +
            "CAST(SUM(mtd.netAmount) as bigdecimal) as totalSettleAmount, " +
            "CAST(SUM(mtd.charge) as bigdecimal) as totalSystemFee, " +
            "COUNT(CASE WHEN mtd.tranStatus = 'SETTLED' THEN 1 END) as settledCount, " +
            "COUNT(CASE WHEN mtd.tranStatus != 'SETTLED' THEN 1 END) as failedCount, " +
            "CAST(AVG(mtd.amount) as bigdecimal) as averageAmount, " +
            "COUNT(DISTINCT mtd.merchant.id) as uniqueMerchants " +
            "FROM MerchantTransactionDetails mtd " +
            "WHERE mtd.transactionDate BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "GROUP BY DATE(mtd.transactionDate) " +
            "ORDER BY DATE(mtd.transactionDate) DESC")
    List<Object[]> getMerchantDailySummaryByTxnDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId);

    // DAILY TRANSACTION SUMMARY (Merchant Only, Settlement Date)
    @Query("SELECT " +
            "DATE(mtd.updatedDateAndTimeOfTransaction) as settleDate, " +
            "COUNT(mtd) as totalTransactions, " +
            "CAST(SUM(mtd.amount) as bigdecimal) as totalAmount, " +
            "CAST(SUM(mtd.netAmount) as bigdecimal) as totalSettleAmount, " +
            "CAST(SUM(mtd.charge) as bigdecimal) as totalSystemFee, " +
            "COUNT(CASE WHEN mtd.tranStatus = 'SETTLED' THEN 1 END) as settledCount, " +
            "COUNT(CASE WHEN mtd.tranStatus != 'SETTLED' THEN 1 END) as failedCount, " +
            "CAST(AVG(mtd.amount) as bigdecimal) as averageAmount, " +
            "COUNT(DISTINCT mtd.merchant.id) as uniqueMerchants " +
            "FROM MerchantTransactionDetails mtd " +
            "WHERE mtd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "GROUP BY DATE(mtd.updatedDateAndTimeOfTransaction) " +
            "ORDER BY DATE(mtd.updatedDateAndTimeOfTransaction) DESC")
    List<Object[]> getMerchantDailySummaryBySettleDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId);


//4
  // TERMINAL WISE TRANSACTION ANALYSIS (Merchant Only, Transaction Date)
    @Query("SELECT " +
        "vt.tid as terminalId, " +
        "mtd.merchant.businessName as merchantName, " +
        "COUNT(mtd) as transactionCount, " +
        "CAST(SUM(mtd.amount) as bigdecimal) as totalAmount, " +
        "CAST(SUM(mtd.netAmount) as bigdecimal) as totalSettleAmount, " +
        "CAST(SUM(mtd.charge) as bigdecimal) as totalSystemFee, " +
        "CAST(AVG(mtd.amount) as bigdecimal) as averageAmount, " +
        "COUNT(CASE WHEN mtd.tranStatus = 'SETTLED' THEN 1 END) as successCount, " +
        "COUNT(CASE WHEN mtd.tranStatus != 'SETTLED' THEN 1 END) as failureCount " +
        "FROM MerchantTransactionDetails mtd " +
        "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
        "WHERE mtd.transactionDate BETWEEN :startDate AND :endDate " +
        "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
        "GROUP BY vt.tid, mtd.merchant.businessName " +
        "ORDER BY SUM(mtd.amount) DESC")
    List<Object[]> getTerminalWiseAnalysisByTxnDate(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("merchantId") Long merchantId);

    // TERMINAL WISE TRANSACTION ANALYSIS (Merchant Only, Settlement Date)
    @Query("SELECT " +
            "vt.tid as terminalId, " +
            "mtd.merchant.businessName as merchantName, " +
            "COUNT(mtd) as transactionCount, " +
            "CAST(SUM(mtd.amount) as bigdecimal) as totalAmount, " +
            "CAST(SUM(mtd.netAmount) as bigdecimal) as totalSettleAmount, " +
            "CAST(SUM(mtd.charge) as bigdecimal) as totalSystemFee, " +
            "CAST(AVG(mtd.amount) as bigdecimal) as averageAmount, " +
            "COUNT(CASE WHEN mtd.tranStatus = 'SETTLED' THEN 1 END) as successCount, " +
            "COUNT(CASE WHEN mtd.tranStatus != 'SETTLED' THEN 1 END) as failureCount " +
            "FROM MerchantTransactionDetails mtd " +
            "JOIN VendorTransactions vt ON vt.transactionReferenceId = mtd.vendorTransactionId " +
            "WHERE mtd.updatedDateAndTimeOfTransaction BETWEEN :startDate AND :endDate " +
            "AND (:merchantId IS NULL OR mtd.merchant.id = :merchantId) " +
            "GROUP BY vt.tid, mtd.merchant.businessName " +
            "ORDER BY SUM(mtd.amount) DESC")
    List<Object[]> getTerminalWiseAnalysisBySettleDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("merchantId") Long merchantId);


    @Query(
            value = """
            SELECT
                t.transactionId,
                t.amount,
                t.charge,
                t.balAfterTran,
                t.balBeforeTran,
                t.bankRefId,
                t.remarks,
                t.tranStatus,
                t.transactionDate,
                t.transactionType,
                t.merchantId,
                t.franchiseId
            FROM (
                SELECT
                    m.transaction_id AS transactionId,
                    (ABS(m.amount) - COALESCE(m.charge, 0)) AS amount,
                    COALESCE(m.charge, 0) AS charge,
                    m.bal_after_tran AS balAfterTran,
                    m.bal_before_tran AS balBeforeTran,
                    m.bank_ref_id AS bankRefId,
                    m.remarks AS remarks,
                    m.tran_status AS tranStatus,
                    m.transaction_date AS transactionDate,
                    m.transaction_type AS transactionType,
                    m.merchant_id AS merchantId,
                    NULL AS franchiseId
                FROM merchant_transaction_details m
                WHERE m.transaction_date >= :startDate
                  AND m.transaction_date < :endDate
                  AND m.service IN (:services)
                  AND (
                        :merchantId IS NULL
                        OR m.merchant_id = :merchantId
                  )

                UNION ALL

                SELECT
                    f.transaction_id AS transactionId,
                    (ABS(f.amount) - COALESCE(f.charge, 0)) AS amount,
                    COALESCE(f.charge, 0) AS charge,
                    f.bal_after_tran AS balAfterTran,
                    f.bal_before_tran AS balBeforeTran,
                    f.bank_ref_id AS bankRefId,
                    f.remarks AS remarks,
                    f.tran_status AS tranStatus,
                    f.transaction_date AS transactionDate,
                    f.transaction_type AS transactionType,
                    NULL AS merchantId,
                    f.franchise_id AS franchiseId
                FROM franchise_transaction_details f
                WHERE f.transaction_date >= :startDate
                  AND f.transaction_date < :endDate
                  AND f.service IN (:services)
                  AND :merchantId IS NULL
            ) t
            ORDER BY t.transactionDate DESC
            """,

            countQuery = """
            SELECT COUNT(*)
            FROM (
                SELECT m.transaction_id
                FROM merchant_transaction_details m
                WHERE m.transaction_date >= :startDate
                  AND m.transaction_date < :endDate
                  AND m.service IN (:services)
                  AND (
                        :merchantId IS NULL
                        OR m.merchant_id = :merchantId
                  )

                UNION ALL

                SELECT f.transaction_id
                FROM franchise_transaction_details f
                WHERE f.transaction_date >= :startDate
                  AND f.transaction_date < :endDate
                  AND f.service IN (:services)
                  AND :merchantId IS NULL
            ) x
            """,
            nativeQuery = true
    )
    Page<PayoutTransactionReportDTO> fetchTransactions(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("services") List<String> services,
            @Param("merchantId") Long merchantId,
            Pageable pageable
    );

    @Query(
            value = """
            SELECT
                COUNT(*) AS totalCount,

                COALESCE(
                    SUM(
                        CASE
                            WHEN service = 'PAYOUT' THEN 1
                            ELSE 0
                        END
                    ),
                    0
                ) AS payoutCount,

                COALESCE(
                    SUM(
                        CASE
                            WHEN service = 'PAYOUT_REFUND' THEN 1
                            ELSE 0
                        END
                    ),
                    0
                ) AS payoutRefundCount

            FROM (
                SELECT m.service
                FROM merchant_transaction_details m
                WHERE m.transaction_date >= :startDate
                  AND m.transaction_date < :endDate
                  AND m.service IN (:services)
                  AND (
                        :merchantId IS NULL
                        OR m.merchant_id = :merchantId
                  )

                UNION ALL

                SELECT f.service
                FROM franchise_transaction_details f
                WHERE f.transaction_date >= :startDate
                  AND f.transaction_date < :endDate
                  AND f.service IN (:services)
                  AND :merchantId IS NULL
            ) x
            """,
            nativeQuery = true
    )
    PayoutServiceCountDTO fetchServiceCounts(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("services") List<String> services,
            @Param("merchantId") Long merchantId
    );



}
