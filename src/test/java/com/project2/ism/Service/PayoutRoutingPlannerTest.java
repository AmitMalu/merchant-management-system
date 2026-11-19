package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlan;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlanRefusal;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlanVendor;
import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Repository.PaymentProductRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Which vendor a payout of the new flow goes to when "Use routing for payouts" is on. With it off, with no
 * routing for the account, or with a switched-off routing, the fixed vendor is used exactly as before.
 */
class PayoutRoutingPlannerTest {

    private static final Long FIXED = 1L;
    private static final BigDecimal AMOUNT = new BigDecimal("1000");

    private PayoutEngineSwitch engineSwitch;
    private PaymentRoutingConfigService config;
    private PaymentProductRepository products;
    private PayoutTransactionRepository payouts;
    private PayoutRoutingPlanner planner;
    private PaymentProduct product;

    @BeforeEach
    void setUp() {
        engineSwitch = mock(PayoutEngineSwitch.class);
        config = mock(PaymentRoutingConfigService.class);
        products = mock(PaymentProductRepository.class);
        payouts = mock(PayoutTransactionRepository.class);
        planner = new PayoutRoutingPlanner(engineSwitch, config, products, payouts);

        product = mock(PaymentProduct.class);
        when(engineSwitch.routingEnabled()).thenReturn(true);
        when(products.findByProductCode("001")).thenReturn(Optional.of(product));
    }

    private static RoutePlanVendor vendor(long id, String name, int priority, Integer perDay, BigDecimal perDayAmount) {
        return new RoutePlanVendor(id, name, priority, perDay, perDayAmount);
    }

    private void plan(RoutePlanVendor... vendors) {
        when(config.routePlan(any(), eq("MERCHANT"), eq(5L), any(), any()))
                .thenReturn(new RoutePlan(true, 9L, "Payout - Main", 3, 2, List.of(vendors), List.of()));
    }

    private void usage(long vendorId, long count, String total) {
        PayoutTransactionRepository.DailyUsage u = new PayoutTransactionRepository.DailyUsage() {
            public Long getCount() { return count; }
            public BigDecimal getTotal() { return total == null ? null : new BigDecimal(total); }
        };
        when(payouts.dailyUsage(eq(vendorId), any(), any())).thenReturn(u);
    }

    // ---------------------------------------------------------------- when routing does not apply

    @Test
    void routingOff_usesTheFixedVendor_andReadsNothing() {
        when(engineSwitch.routingEnabled()).thenReturn(false);

        PayoutRoutingPlanner.Choice c = planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true);

        assertEquals(FIXED, c.vendorId());
        assertEquals(1, c.tries());
        assertFalse(c.routed());
        verifyNoInteractions(config, products, payouts);
    }

    @Test
    void noRoutingForTheAccount_usesTheFixedVendor() {
        when(config.routePlan(any(), any(), any(), any(), any()))
                .thenReturn(new RoutePlan(false, null, null, 1, 0, List.of(), List.of("none")));

        PayoutRoutingPlanner.Choice c = planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true);

        assertEquals(FIXED, c.vendorId());
        assertFalse(c.routed());
    }

    @Test
    void aMissingPayoutProduct_usesTheFixedVendor() {
        when(products.findByProductCode("001")).thenReturn(Optional.empty());

        assertEquals(FIXED, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true).vendorId());
        verifyNoInteractions(config);
    }

    // ---------------------------------------------------------------- the choice

    @Test
    void theFirstAllowedVendorIsChosen_withTheRoutingsRetrySettings() {
        plan(vendor(1, "ViMoPay", 1, null, null), vendor(2, "Other", 2, null, null));

        PayoutRoutingPlanner.Choice c = planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true);

        assertEquals(1L, c.vendorId());
        assertEquals(3, c.tries());
        assertEquals(2, c.waitSeconds());
        assertTrue(c.routed());
        verify(payouts, never()).dailyUsage(anyLong(), any(), any());      // no daily limit set: nothing to count
    }

    @Test
    void aVendorThatUsedItsDailyCount_isSkipped() {
        plan(vendor(1, "ViMoPay", 1, 10, null), vendor(2, "Other", 2, null, null));
        usage(1L, 10, "5000");

        assertEquals(2L, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true).vendorId());
    }

    @Test
    void aVendorBelowItsDailyCount_isStillUsed() {
        plan(vendor(1, "ViMoPay", 1, 10, null), vendor(2, "Other", 2, null, null));
        usage(1L, 9, "5000");

        assertEquals(1L, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true).vendorId());
    }

    @Test
    void aPayoutThatWouldPassTheDailyAmount_goesToTheNextVendor_butOneThatFitsExactlyIsAllowed() {
        plan(vendor(1, "ViMoPay", 1, null, new BigDecimal("100000")), vendor(2, "Other", 2, null, null));

        usage(1L, 3, "99500");
        assertEquals(2L, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true).vendorId());

        usage(1L, 3, "99000");
        assertEquals(1L, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true).vendorId());
    }

    @Test
    void anEmptyDay_countsAsZero() {
        plan(vendor(1, "ViMoPay", 1, 1, new BigDecimal("5000")));
        when(payouts.dailyUsage(eq(1L), any(), any())).thenReturn(null);

        assertEquals(1L, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true).vendorId());
    }

    @Test
    void theDailyLimitsCountPendingAndSuccessfulPayoutsOnly() {
        plan(vendor(1, "ViMoPay", 1, 5, null));
        usage(1L, 0, null);

        planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true);

        verify(payouts).dailyUsage(eq(1L), any(), eq(List.of(PayoutTransaction.PayoutStatus.PENDING,
                PayoutTransaction.PayoutStatus.SUCCESS)));
    }

    @Test
    void aVendorThePayoutCodeCannotSendTo_isSkipped() {
        plan(vendor(7, "Not built yet", 1, null, null), vendor(1, "ViMoPay", 2, null, null));

        assertEquals(1L, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> id == 1L).vendorId());
    }

    // ---------------------------------------------------------------- nobody can serve

    @Test
    void whenTheDailyCountIsUsedUp_theRefusalSaysSo_withoutNamingAVendor() {
        plan(vendor(1, "ViMoPay", 1, 2, null));
        usage(1L, 2, "100");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true));

        assertEquals("Daily payout limit reached. Please try again tomorrow.", e.getMessage());
        assertFalse(e.getMessage().contains("ViMoPay"));
    }

    @Test
    void whenTheDailyAmountWouldBePassed_theRefusalSaysSo() {
        plan(vendor(1, "ViMoPay", 1, null, new BigDecimal("5000")));
        usage(1L, 1, "4500");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true));

        assertEquals("Daily payout amount limit reached. Please try again tomorrow or with a smaller amount.",
                e.getMessage());
    }

    // ---------------------------------------------------------------- monthly and yearly limits

    private static RoutePlanVendor withLimits(long id, String name, int priority, Integer monthCount,
                                              BigDecimal monthAmount, Integer yearCount, BigDecimal yearAmount) {
        return new RoutePlanVendor(id, name, priority, null, null, monthCount, monthAmount, yearCount, yearAmount);
    }

    private void usageSince(long vendorId, java.time.LocalDateTime from, long count, String total) {
        PayoutTransactionRepository.DailyUsage u = new PayoutTransactionRepository.DailyUsage() {
            public Long getCount() { return count; }
            public BigDecimal getTotal() { return total == null ? null : new BigDecimal(total); }
        };
        when(payouts.dailyUsage(eq(vendorId), eq(from), any())).thenReturn(u);
    }

    private static java.time.LocalDateTime monthStart() {
        return java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }

    private java.time.LocalDateTime yearStart() {
        return planner.yearStart(java.time.LocalDate.now()).atStartOfDay();
    }

    @Test
    void theMonthlyCountIsCheckedOverTheCalendarMonth() {
        plan(withLimits(1, "ViMoPay", 1, 100, null, null, null));
        usageSince(1L, monthStart(), 100, "1000");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true));
        assertEquals("Monthly payout limit reached. Please try again next month.", e.getMessage());

        usageSince(1L, monthStart(), 99, "1000");
        assertEquals(1L, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true).vendorId());
    }

    @Test
    void theMonthlyAmountIsCheckedWithThisPayoutAdded() {
        plan(withLimits(1, "ViMoPay", 1, null, new BigDecimal("500000"), null, null));

        usageSince(1L, monthStart(), 40, "499500");          // 499,500 + 1,000 > 500,000
        assertEquals("Monthly payout amount limit reached. Please try again next month or with a smaller amount.",
                assertThrows(IllegalStateException.class,
                        () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true)).getMessage());

        usageSince(1L, monthStart(), 40, "499000");          // 499,000 + 1,000 = 500,000: allowed
        assertEquals(1L, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true).vendorId());
    }

    @Test
    void theYearlyLimitsAreChecked_andSayYearly() {
        plan(withLimits(1, "ViMoPay", 1, null, null, 1000, new BigDecimal("9000000")));

        usageSince(1L, yearStart(), 1000, "10");
        assertEquals("Yearly payout limit reached. Please try again next year.",
                assertThrows(IllegalStateException.class,
                        () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true)).getMessage());

        usageSince(1L, yearStart(), 5, "8999500");
        assertEquals("Yearly payout amount limit reached. Please try again next year or with a smaller amount.",
                assertThrows(IllegalStateException.class,
                        () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true)).getMessage());
    }

    @Test
    void theShortestPeriodThatRanOutIsTheOneReported() {
        when(config.routePlan(any(), eq("MERCHANT"), eq(5L), any(), any()))
                .thenReturn(new RoutePlan(true, 9L, "Payout - Main", 3, 2,
                        List.of(new RoutePlanVendor(1L, "ViMoPay", 1, 2, null, 50, null, 500, null)), List.of()));
        usageSince(1L, java.time.LocalDate.now().atStartOfDay(), 2, "100");    // today is used up
        usageSince(1L, monthStart(), 50, "100");                               // and so is the month
        usageSince(1L, yearStart(), 500, "100");                               // and the year

        assertEquals("Daily payout limit reached. Please try again tomorrow.",
                assertThrows(IllegalStateException.class,
                        () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true)).getMessage());
    }

    @Test
    void aPeriodWithoutALimitIsNeverCounted() {
        plan(withLimits(1, "ViMoPay", 1, 100, null, null, null));          // only a monthly count
        usageSince(1L, monthStart(), 1, "10");

        planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true);

        verify(payouts, times(1)).dailyUsage(eq(1L), any(), any());         // the month only: not today, not the year
        verify(payouts).dailyUsage(eq(1L), eq(monthStart()), any());
    }

    @Test
    void aFailedMonthlyLimitMovesToTheNextVendor() {
        plan(withLimits(1, "ViMoPay", 1, 10, null, null, null), withLimits(2, "Other", 2, null, null, null, null));
        usageSince(1L, monthStart(), 10, "0");

        assertEquals(2L, planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true).vendorId());
    }

    @Test
    void theYearStartsInJanuary_orInTheMonthYouSet() {
        assertEquals(java.time.LocalDate.of(2026, 1, 1), planner.yearStart(java.time.LocalDate.of(2026, 11, 18)));
        assertEquals(java.time.LocalDate.of(2026, 1, 1), planner.yearStart(java.time.LocalDate.of(2026, 1, 1)));

        org.springframework.test.util.ReflectionTestUtils.setField(planner, "yearStartMonth", 4);   // financial year
        assertEquals(java.time.LocalDate.of(2026, 4, 1), planner.yearStart(java.time.LocalDate.of(2026, 11, 18)));
        assertEquals(java.time.LocalDate.of(2026, 4, 1), planner.yearStart(java.time.LocalDate.of(2026, 4, 1)));
        assertEquals(java.time.LocalDate.of(2025, 4, 1), planner.yearStart(java.time.LocalDate.of(2026, 2, 10)));
        assertEquals(java.time.LocalDate.of(2025, 4, 1), planner.yearStart(java.time.LocalDate.of(2026, 3, 31)));

        org.springframework.test.util.ReflectionTestUtils.setField(planner, "yearStartMonth", 99);  // nonsense = December
        assertEquals(java.time.LocalDate.of(2025, 12, 1), planner.yearStart(java.time.LocalDate.of(2026, 3, 1)));
    }

    @Test
    void anAmountOutsideTheAllowedRange_isExplained_withTheLimitButWithoutTheVendor() {
        when(config.routePlan(any(), any(), any(), any(), any()))
                .thenReturn(new RoutePlan(true, 9L, "Payout - Main", 3, 2, List.of(),
                        List.of("1. ViMoPay: Amount is above this vendor's maximum (5000)"),
                        List.of(new RoutePlanRefusal(1, "Amount is above the maximum allowed (5000)."))));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true));

        assertEquals("Amount is above the maximum allowed (5000).", e.getMessage());
        assertFalse(e.getMessage().contains("ViMoPay"));
    }

    @Test
    void theReasonOfTheFirstVendorInTheOrderIsTheOneShown() {
        plan(vendor(1, "ViMoPay", 1, 1, null), vendor(2, "Other", 2, null, new BigDecimal("10")));
        usage(1L, 1, "100");
        usage(2L, 0, "0");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true));

        assertEquals("Daily payout limit reached. Please try again tomorrow.", e.getMessage());     // vendor 1's
    }

    @Test
    void whenTheReasonIsNotOneThatCanBeShown_itIsTheGenericMessage() {
        // a routing whose vendors are all unusable (not linked / inactive / switched off): no specific reason
        when(config.routePlan(any(), any(), any(), any(), any()))
                .thenReturn(new RoutePlan(true, 9L, "Payout - Main", 3, 2, List.of(),
                        List.of("1. ViMoPay: The vendor is inactive"),
                        List.of(new RoutePlanRefusal(1, null))));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> true));
        assertEquals("Payout is not available right now. Please try again later.", e.getMessage());

        // and a vendor the payout code cannot send to
        plan(vendor(7, "Not built yet", 1, null, null));
        assertEquals("Payout is not available right now. Please try again later.",
                assertThrows(IllegalStateException.class,
                        () -> planner.choose("MERCHANT", 5L, AMOUNT, FIXED, id -> false)).getMessage());
    }
}
