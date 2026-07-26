package com.project2.ism.Repository;

import com.project2.ism.DTO.ReportDTO.SettledUnsettledReportDto;
import com.project2.ism.Model.VendorTransactions;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VendorTransactionsRepository extends JpaRepository<VendorTransactions, Long> {

    @Query(value = """
    SELECT * FROM vendor_transactions vt
    WHERE vt.settled = 0
      AND vt.date BETWEEN :from AND :to
      AND (
        ( :#{#mids.size()} > 0 AND vt.mid IN (:mids) ) AND
        ( :#{#tids.size()} > 0 AND vt.tid IN (:tids) )
      )
    """, nativeQuery = true)
    List<VendorTransactions> findCandidates(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to,
            @Param("mids") List<String> mids,
            @Param("tids") List<String> tids
//            @Param("sids") List<String> sids
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vt FROM VendorTransactions vt WHERE vt.internalId = :id")
    Optional<VendorTransactions> lockById(@Param("id") Long id);

    Optional<VendorTransactions> findByTransactionReferenceId(String vendorTxPrimaryKey);

    @Query("""
    SELECT MIN(v.date)
    FROM VendorTransactions v
    WHERE v.settled = false AND v.mid IN :mids
""")
    Optional<LocalDateTime> findEarliestUnsettledDateByMids(@Param("mids") List<String> mids);

    @Query("""
SELECT new com.project2.ism.DTO.ReportDTO.SettledUnsettledReportDto(
    v.amount,
    v.brandType,
    v.card,
    v.cardTxnType,
    v.cardType,
    v.cashAtPos,
    v.date,
    v.merchant,
    v.mid,
    v.mobile,
    v.payer,
    v.pgErrorCode,
    v.pgErrorMessage,
    v.receiptNo,
    v.settled,
    v.settledAt,
    v.settledOn,
    v.settlementBatchId,
    v.status,
    v.tid,
    v.transactionReferenceId,
    v.settlementStatus
)
FROM VendorTransactions v
WHERE
    (:settled IS NULL OR v.settled = :settled)

    AND (
        :merchantId IS NULL
        OR EXISTS (
            SELECT psn.id
            FROM ProductSerialNumbers psn
            WHERE psn.merchant.id = :merchantId
              AND psn.mid = v.mid
              AND psn.tid = v.tid
        )
    )

    AND (
        (
            :dateType = 'TRANSACTION_DATE'
            AND (:fromDate IS NULL OR v.date >= :fromDate)
            AND (:toDate IS NULL OR v.date <= :toDate)
        )
        OR
        (
            :dateType = 'SETTLEMENT_DATE'
            AND (:fromDate IS NULL OR v.settledAt >= :fromDate)
            AND (:toDate IS NULL OR v.settledAt <= :toDate)
        )
    )

ORDER BY
    CASE
        WHEN :dateType = 'TRANSACTION_DATE' THEN v.date
        ELSE v.settledAt
    END DESC
""")
    List<SettledUnsettledReportDto> getSettledUnsettledReports(
            @Param("settled") Boolean settled,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("dateType") String dateType,
            @Param("merchantId") Long merchantId
    );

}