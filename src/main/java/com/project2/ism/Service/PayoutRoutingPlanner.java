package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlan;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlanRefusal;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlanVendor;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Repository.PaymentProductRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Chooses the vendor for ONE payout of the new flow (V2), from the routing the admin set up for the account:
 * the first vendor in the routing's order that is allowed (linked, active, switched on, inside its min/max) and
 * has not used up its daily count or daily amount. Pending and successful payouts count toward the daily limits.
 *
 * It is only used when "Use routing for payouts" is switched on (Payout Engine tab, default OFF). When it is off,
 * when the account has no routing, or when the routing is switched off, the fixed vendor is used exactly as
 * before. It runs before anything is debited, so a refusal here costs the merchant nothing.
 *
 * When nothing can serve, the payout is refused with the REAL reason (the daily payout limit, the daily amount, or
 * the allowed amounts), worded without any vendor name, because a merchant never sees vendor names. The vendor
 * names stay in the log.
 *
 * The daily limits are checked just before the debit and are not locked: two payouts made in the same instant
 * can both pass, so a limit can be exceeded by one payout at the most.
 */
@Service
public class PayoutRoutingPlanner {

    private static final Logger log = LoggerFactory.getLogger(PayoutRoutingPlanner.class);

    static final String NOT_AVAILABLE = "Payout is not available right now. Please try again later.";
    static final String DAILY_COUNT_REACHED = "Daily payout limit reached. Please try again tomorrow.";
    static final String DAILY_AMOUNT_REACHED =
            "Daily payout amount limit reached. Please try again tomorrow or with a smaller amount.";
    static final String MONTHLY_COUNT_REACHED = "Monthly payout limit reached. Please try again next month.";
    static final String MONTHLY_AMOUNT_REACHED =
            "Monthly payout amount limit reached. Please try again next month or with a smaller amount.";
    static final String YEARLY_COUNT_REACHED = "Yearly payout limit reached. Please try again next year.";
    static final String YEARLY_AMOUNT_REACHED =
            "Yearly payout amount limit reached. Please try again next year or with a smaller amount.";

    /** The vendor to use, how many tries it gets, and how long to wait between them. */
    public record Choice(Long vendorId, int tries, int waitSeconds, boolean routed) {
    }

    private final PayoutEngineSwitch engineSwitch;
    private final PaymentRoutingConfigService configService;
    private final PaymentProductRepository productRepository;
    private final PayoutTransactionRepository payoutRepository;

    @Value("${routing.payout-product-code:001}")
    private String payoutProductCode = "001";

    /** The month the yearly limits restart in: 1 = calendar year (default), 4 = the Indian financial year. */
    @Value("${routing.year-start-month:1}")
    private int yearStartMonth = 1;

    public PayoutRoutingPlanner(PayoutEngineSwitch engineSwitch,
                                PaymentRoutingConfigService configService,
                                PaymentProductRepository productRepository,
                                PayoutTransactionRepository payoutRepository) {
        this.engineSwitch = engineSwitch;
        this.configService = configService;
        this.productRepository = productRepository;
        this.payoutRepository = payoutRepository;
    }

    /**
     * @param fixedVendorId the vendor used when routing does not apply
     * @param hasAdapter    can the payout code actually send a payout to this vendor?
     * @throws IllegalStateException when routing applies but no vendor can serve the payout (nothing is debited);
     *                               the message says why
     */
    public Choice choose(String accountType, Long accountId, BigDecimal amount, Long fixedVendorId,
                         Predicate<Long> hasAdapter) {
        Choice fixed = new Choice(fixedVendorId, 1, 0, false);
        if (!engineSwitch.routingEnabled()) {
            return fixed;
        }
        var product = productRepository.findByProductCode(payoutProductCode).orElse(null);
        if (product == null) {
            log.warn("Payout routing: product {} not found, using the fixed vendor", payoutProductCode);
            return fixed;
        }

        RoutePlan plan = configService.routePlan(product, accountType, accountId, amount, null);
        if (!plan.hasRouting()) {
            return fixed;
        }

        List<String> notes = new ArrayList<>(plan.notes());
        List<RoutePlanRefusal> refusals = new ArrayList<>(plan.refusals());
        LocalDate today = LocalDate.now();
        for (RoutePlanVendor v : plan.vendors()) {
            if (!hasAdapter.test(v.vendorId())) {
                notes.add(v.priority() + ". " + v.vendorName() + ": payouts cannot be sent to this vendor yet");
                refusals.add(new RoutePlanRefusal(v.priority(), null));
                continue;
            }
            Limit limit = limitReached(v, amount, today);
            if (limit != null) {
                notes.add(v.priority() + ". " + v.vendorName() + ": " + limit.detail());
                refusals.add(new RoutePlanRefusal(v.priority(), limit.message()));
                continue;
            }
            log.info("Payout routing: {} | account={}:{} | amount={} | vendor={} | tries={}",
                    plan.routingName(), accountType, accountId, amount, v.vendorName(), plan.retryCount());
            return new Choice(v.vendorId(), plan.retryCount(), plan.retryWaitSeconds(), true);
        }

        // The vendor names stay in the log. The merchant is told the reason of the first vendor in the order.
        String message = refusals.stream()
                .min(Comparator.comparingInt(RoutePlanRefusal::priority))
                .map(RoutePlanRefusal::message)
                .filter(m -> m != null && !m.isBlank())
                .orElse(NOT_AVAILABLE);
        log.warn("Payout routing: no vendor can serve | routing={} | account={}:{} | amount={} | {} | told: {}",
                plan.routingName(), accountType, accountId, amount, String.join("; ", notes), message);
        throw new IllegalStateException(message);
    }

    private record Limit(String message, String detail) {
    }

    /** The shortest period that has run out wins: today, then this month, then this year. */
    private Limit limitReached(RoutePlanVendor v, BigDecimal amount, LocalDate today) {
        Limit limit = check(v, amount, today.atStartOfDay(), "daily", v.dailyTransactionLimit(),
                v.dailyAmountLimit(), DAILY_COUNT_REACHED, DAILY_AMOUNT_REACHED);
        if (limit == null) {
            limit = check(v, amount, today.withDayOfMonth(1).atStartOfDay(), "monthly",
                    v.monthlyTransactionLimit(), v.monthlyAmountLimit(), MONTHLY_COUNT_REACHED, MONTHLY_AMOUNT_REACHED);
        }
        if (limit == null) {
            limit = check(v, amount, yearStart(today).atStartOfDay(), "yearly",
                    v.yearlyTransactionLimit(), v.yearlyAmountLimit(), YEARLY_COUNT_REACHED, YEARLY_AMOUNT_REACHED);
        }
        return limit;
    }

    /** The first day of the current year: 1 January, or the month in routing.year-start-month (4 = financial year). */
    LocalDate yearStart(LocalDate today) {
        int month = Math.min(Math.max(yearStartMonth, 1), 12);
        LocalDate start = LocalDate.of(today.getYear(), month, 1);
        return start.isAfter(today) ? start.minusYears(1) : start;
    }

    private Limit check(RoutePlanVendor v, BigDecimal amount, LocalDateTime from, String period,
                        Integer countLimit, BigDecimal amountLimit, String countMessage, String amountMessage) {
        if (countLimit == null && amountLimit == null) {
            return null;                 // no limit for this period: nothing to count
        }
        PayoutTransactionRepository.DailyUsage usage = payoutRepository.dailyUsage(
                v.vendorId(), from,
                List.of(PayoutTransaction.PayoutStatus.PENDING, PayoutTransaction.PayoutStatus.SUCCESS));
        long count = usage == null || usage.getCount() == null ? 0 : usage.getCount();
        BigDecimal total = usage == null || usage.getTotal() == null ? BigDecimal.ZERO : usage.getTotal();

        if (countLimit != null && count >= countLimit) {
            return new Limit(countMessage, period + " payout count reached (" + count + " of " + countLimit + ")");
        }
        if (amountLimit != null && total.add(amount).compareTo(amountLimit) > 0) {
            return new Limit(amountMessage, period + " amount would be passed ("
                    + total.stripTrailingZeros().toPlainString() + " taken of "
                    + amountLimit.stripTrailingZeros().toPlainString() + ")");
        }
        return null;
    }
}
