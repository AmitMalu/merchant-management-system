package com.project2.ism.DTO.AdminDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The Payout and Bill payment cards on the admin dashboard: today's numbers (from 00:00 until now).
 */
public record PaymentsTodayDTO(
        LocalDateTime asOf,
        int stuckPendingMinutes,
        Summary payout,
        Summary billPayment) {

    /** A number of payments and their total amount. */
    public record Bucket(long count, BigDecimal amount) {
    }

    /** One line under the totals: a vendor (Payout) or a payment channel (Bill payment). */
    public record Line(Long vendorId, String name, long count, BigDecimal amount) {
    }

    public record FailureReason(String reason, long count) {
    }

    /**
     * success.amount is "paid today". successRate is a percentage of all of today's payments (null when
     * there are none). changePercent compares the successful amount with yesterday up to the same time
     * (null when yesterday had none). stuckPending counts payments PENDING for longer than
     * stuckPendingMinutes, from any day.
     */
    public record Summary(
            Bucket success,
            Bucket failed,
            Bucket pending,
            Double successRate,
            BigDecimal yesterdaySuccessAmount,
            Double changePercent,
            BigDecimal chargesCollected,
            FailureReason topFailureReason,
            long stuckPending,
            List<Line> lines) {
    }
}
