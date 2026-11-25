package com.project2.ism.Service;

import com.project2.ism.DTO.AdminDTO.PaymentsTodayDTO;
import com.project2.ism.DTO.AdminDTO.PaymentsTodayDTO.Bucket;
import com.project2.ism.DTO.AdminDTO.PaymentsTodayDTO.FailureReason;
import com.project2.ism.DTO.AdminDTO.PaymentsTodayDTO.Line;
import com.project2.ism.DTO.AdminDTO.PaymentsTodayDTO.Summary;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Repository.BbpsTransactionRepository;
import com.project2.ism.Repository.CreditCardBillTransactionRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Numbers for the Payout and Bill payment cards on the admin dashboard.
 *
 * Payout: payout_transactions, with one line per vendor that had payments today (a new vendor appears by
 * itself). Bill payment: Bill Avenue (bbps_transactions) and SuivrePay credit card payments
 * (credit_card_bill_transactions) added together, with one line for each.
 *
 * Read only. "Today" is from 00:00 until now, in the server's time zone (the same clock that stamps the rows).
 */
@Service
public class AdminPaymentsTodayService {

    static final String BILL_AVENUE = "Bill Avenue";
    static final String SUIVREPAY = "SuivrePay (credit card)";
    private static final int REASON_MAX_LENGTH = 120;

    private final PayoutTransactionRepository payouts;
    private final BbpsTransactionRepository bills;
    private final CreditCardBillTransactionRepository cards;
    private final PaymentVendorRepository vendors;
    private final int stuckPendingMinutes;

    public AdminPaymentsTodayService(PayoutTransactionRepository payouts,
                                     BbpsTransactionRepository bills,
                                     CreditCardBillTransactionRepository cards,
                                     PaymentVendorRepository vendors,
                                     @Value("${dashboard.stuck-pending-minutes:30}") int stuckPendingMinutes) {
        this.payouts = payouts;
        this.bills = bills;
        this.cards = cards;
        this.vendors = vendors;
        this.stuckPendingMinutes = stuckPendingMinutes;
    }

    @Transactional(readOnly = true)
    public PaymentsTodayDTO today() {
        return today(LocalDateTime.now());
    }

    PaymentsTodayDTO today(LocalDateTime now) {
        LocalDateTime start = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterdayStart = start.minusDays(1);
        LocalDateTime yesterdayNow = now.minusDays(1);   // yesterday up to the same time of day
        LocalDateTime stuckCutoff = now.minusMinutes(stuckPendingMinutes);

        return new PaymentsTodayDTO(now, stuckPendingMinutes,
                payoutSummary(start, now, yesterdayStart, yesterdayNow, stuckCutoff),
                billSummary(start, now, yesterdayStart, yesterdayNow, stuckCutoff));
    }

    // ------------------------------------------------------------------ payout

    private Summary payoutSummary(LocalDateTime start, LocalDateTime now, LocalDateTime yStart, LocalDateTime yNow,
                                  LocalDateTime stuckCutoff) {
        Totals today = Totals.of(payouts.dashboardTotalsByStatus(start, now));
        Totals yesterday = Totals.of(payouts.dashboardTotalsByStatus(yStart, yNow));

        return summary(today, yesterday,
                topReason(payouts.dashboardFailureReasons(PayoutTransaction.PayoutStatus.FAILED, start, now, PageRequest.of(0, 1))),
                payouts.dashboardStuckPending(PayoutTransaction.PayoutStatus.PENDING, stuckCutoff),
                payoutVendorLines(payouts.dashboardTotalsByVendor(start, now)));
    }

    /** One line per vendor that had a payout today: its successful payouts. Biggest first. */
    private List<Line> payoutVendorLines(List<Object[]> rows) {
        Map<Long, long[]> counts = new LinkedHashMap<>();
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        for (Object[] row : rows) {
            Long vendorId = row[0] == null ? null : ((Number) row[0]).longValue();
            seen.add(vendorId);
            counts.computeIfAbsent(vendorId, k -> new long[1]);
            amounts.putIfAbsent(vendorId, BigDecimal.ZERO);
            if ("SUCCESS".equals(String.valueOf(row[1]))) {
                counts.get(vendorId)[0] += ((Number) row[2]).longValue();
                amounts.merge(vendorId, money(row[3]), BigDecimal::add);
            }
        }

        Map<Long, String> names = new LinkedHashMap<>();
        List<Long> ids = seen.stream().filter(id -> id != null).toList();
        if (!ids.isEmpty()) {
            for (PaymentVendor v : vendors.findAllById(ids)) {
                names.put(v.getId(), v.getVendorName());
            }
        }

        List<Line> lines = new ArrayList<>();
        for (Long vendorId : seen) {
            String name = vendorId == null ? "Other" : names.getOrDefault(vendorId, "Vendor " + vendorId);
            lines.add(new Line(vendorId, name, counts.get(vendorId)[0], amounts.get(vendorId)));
        }
        lines.sort(Comparator.comparing(Line::amount).reversed().thenComparing(Line::name));
        return lines;
    }

    // ------------------------------------------------------------ bill payment

    private Summary billSummary(LocalDateTime start, LocalDateTime now, LocalDateTime yStart, LocalDateTime yNow,
                                LocalDateTime stuckCutoff) {
        Totals billToday = Totals.of(bills.dashboardTotalsByStatus(start, now));
        Totals cardToday = Totals.of(cards.dashboardTotalsByStatus(CreditCardBillTransaction.Status.FETCHED, start, now));
        Totals billYesterday = Totals.of(bills.dashboardTotalsByStatus(yStart, yNow));
        Totals cardYesterday = Totals.of(cards.dashboardTotalsByStatus(CreditCardBillTransaction.Status.FETCHED, yStart, yNow));

        FailureReason reason = topReason(merge(
                bills.dashboardFailureReasons(BbpsTransaction.BbpsStatus.FAILED, start, now, PageRequest.of(0, 3)),
                cards.dashboardFailureReasons(CreditCardBillTransaction.Status.FAILED, start, now, PageRequest.of(0, 3))));

        List<Line> lines = List.of(
                new Line(null, BILL_AVENUE, billToday.success.count(), billToday.success.amount()),
                new Line(null, SUIVREPAY, cardToday.success.count(), cardToday.success.amount()));

        return summary(billToday.plus(cardToday), billYesterday.plus(cardYesterday), reason,
                bills.dashboardStuckPending(BbpsTransaction.BbpsStatus.PENDING, stuckCutoff)
                        + cards.dashboardStuckPending(CreditCardBillTransaction.Status.PENDING, stuckCutoff), lines);
    }

    // ----------------------------------------------------------------- helpers

    private Summary summary(Totals today, Totals yesterday, FailureReason reason, long stuck, List<Line> lines) {
        long all = today.success.count() + today.failed.count() + today.pending.count();
        Double rate = all == 0 ? null
                : BigDecimal.valueOf(today.success.count() * 100.0 / all).setScale(1, RoundingMode.HALF_UP).doubleValue();

        BigDecimal before = yesterday.success.amount();
        Double change = before.signum() == 0 ? null
                : today.success.amount().subtract(before).multiply(BigDecimal.valueOf(100))
                .divide(before, 1, RoundingMode.HALF_UP).doubleValue();

        return new Summary(today.success, today.failed, today.pending, rate, before, change,
                today.charges, reason, stuck, lines);
    }

    /** Failure messages from several tables added together; the most common one, or null. */
    private static List<Object[]> merge(List<Object[]> a, List<Object[]> b) {
        Map<String, Long> total = new LinkedHashMap<>();
        for (List<Object[]> rows : List.of(a, b)) {
            for (Object[] row : rows) {
                total.merge(String.valueOf(row[0]), ((Number) row[1]).longValue(), Long::sum);
            }
        }
        List<Object[]> out = new ArrayList<>();
        total.forEach((message, count) -> out.add(new Object[]{message, count}));
        out.sort((x, y) -> Long.compare((Long) y[1], (Long) x[1]));
        return out;
    }

    private static FailureReason topReason(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        String reason = String.valueOf(rows.get(0)[0]).trim();
        if (reason.isEmpty()) {
            return null;
        }
        if (reason.length() > REASON_MAX_LENGTH) {
            reason = reason.substring(0, REASON_MAX_LENGTH - 1) + "…";
        }
        return new FailureReason(reason, ((Number) rows.get(0)[1]).longValue());
    }

    private static BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    /** Count and amount for each status, and the charges of the successful ones. */
    private static final class Totals {
        final Bucket success;
        final Bucket failed;
        final Bucket pending;
        final BigDecimal charges;

        Totals(Bucket success, Bucket failed, Bucket pending, BigDecimal charges) {
            this.success = success;
            this.failed = failed;
            this.pending = pending;
            this.charges = charges;
        }

        /** Rows are [status, count, amount, charges]. Any status other than SUCCESS / FAILED / PENDING is ignored. */
        static Totals of(List<Object[]> rows) {
            Bucket success = new Bucket(0, BigDecimal.ZERO);
            Bucket failed = success;
            Bucket pending = success;
            BigDecimal charges = BigDecimal.ZERO;
            BiFunction<Object[], Bucket, Bucket> add = (row, old) ->
                    new Bucket(old.count() + ((Number) row[1]).longValue(), old.amount().add(money(row[2])));
            for (Object[] row : rows) {
                switch (String.valueOf(row[0])) {
                    case "SUCCESS" -> {
                        success = add.apply(row, success);
                        charges = charges.add(money(row[3]));
                    }
                    case "FAILED" -> failed = add.apply(row, failed);
                    case "PENDING" -> pending = add.apply(row, pending);
                    default -> { /* not a payment */ }
                }
            }
            return new Totals(success, failed, pending, charges);
        }

        Totals plus(Totals other) {
            return new Totals(
                    new Bucket(success.count() + other.success.count(), success.amount().add(other.success.amount())),
                    new Bucket(failed.count() + other.failed.count(), failed.amount().add(other.failed.amount())),
                    new Bucket(pending.count() + other.pending.count(), pending.amount().add(other.pending.amount())),
                    charges.add(other.charges));
        }
    }
}
