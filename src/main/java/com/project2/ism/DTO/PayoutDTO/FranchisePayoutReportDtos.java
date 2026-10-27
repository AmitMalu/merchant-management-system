package com.project2.ism.DTO.PayoutDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Response shapes for the franchise payout report. */
public final class FranchisePayoutReportDtos {

    private FranchisePayoutReportDtos() {
    }

    /** One payout. The beneficiary account number is masked to its last 4 digits. */
    public record Row(Long id,
                      LocalDateTime createdAt,
                      LocalDateTime completedAt,
                      String referenceId,
                      String accountType,
                      Long accountId,
                      String accountName,
                      String beneficiaryName,
                      String beneficiaryAccount,
                      String beneficiaryIfsc,
                      String beneficiaryBank,
                      String paymentMode,
                      String purpose,
                      BigDecimal amount,
                      BigDecimal charges,
                      BigDecimal totalDeducted,
                      String status,
                      String bankRefNo,
                      String message) {
    }

    /** Counts are for every status; the money totals are for SUCCESS payouts only. */
    public record Summary(long totalCount,
                          long successCount,
                          long pendingCount,
                          long failedCount,
                          BigDecimal successAmount,
                          BigDecimal successCharges,
                          BigDecimal successTotalDeducted) {
    }

    public record Response(List<Row> rows, long totalCount, int page, int size, Summary summary) {
    }
}
