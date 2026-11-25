package com.project2.ism.Service;

import com.project2.ism.DTO.AdminDTO.PaymentsTodayDTO;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Model.Routing.PaymentProductVendor;
import com.project2.ism.Repository.BbpsTransactionRepository;
import com.project2.ism.Repository.CreditCardBillTransactionRepository;
import com.project2.ism.Repository.PaymentProductRepository;
import com.project2.ism.Repository.PaymentProductVendorRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Payout and Bill payment cards of the admin dashboard: totals per status, a line per vendor,
 * Bill Avenue + SuivrePay added together, the comparison with yesterday, and the stuck pending count.
 */
class AdminPaymentsTodayServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 15, 30);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 25, 0, 0);
    private static final LocalDateTime Y_START = LocalDateTime.of(2026, 11, 24, 0, 0);
    private static final LocalDateTime Y_NOW = LocalDateTime.of(2026, 11, 24, 15, 30);

    private PayoutTransactionRepository payouts;
    private BbpsTransactionRepository bills;
    private CreditCardBillTransactionRepository cards;
    private PaymentVendorRepository vendors;
    private PaymentProductRepository products;
    private PaymentProductVendorRepository productVendors;
    private AdminPaymentsTodayService service;

    @BeforeEach
    void setUp() {
        payouts = mock(PayoutTransactionRepository.class);
        bills = mock(BbpsTransactionRepository.class);
        cards = mock(CreditCardBillTransactionRepository.class);
        vendors = mock(PaymentVendorRepository.class);
        products = mock(PaymentProductRepository.class);
        productVendors = mock(PaymentProductVendorRepository.class);
        service = serviceWithDefaultVendor(0);
    }

    private AdminPaymentsTodayService serviceWithDefaultVendor(long defaultVendorId) {
        return new AdminPaymentsTodayService(payouts, bills, cards, vendors, products, productVendors,
                "001", defaultVendorId, 30);
    }

    /** The vendors that are set up for Payout (Product Vendors): [vendor, enabled] pairs. */
    private void payoutVendorsSetUp(Object... vendorAndEnabled) {
        PaymentProduct payout = new PaymentProduct();
        ReflectionTestUtils.setField(payout, "id", 1L);
        when(products.findByProductCode("001")).thenReturn(Optional.of(payout));
        List<PaymentProductVendor> links = new java.util.ArrayList<>();
        for (int i = 0; i < vendorAndEnabled.length; i += 2) {
            PaymentProductVendor link = new PaymentProductVendor();
            link.setVendor((PaymentVendor) vendorAndEnabled[i]);
            link.setEnabled((Boolean) vendorAndEnabled[i + 1]);
            links.add(link);
        }
        when(productVendors.findAllWithDetails(1L)).thenReturn(links);
    }

    private static Object[] row(Object... values) {
        return values;
    }

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }

    private static PaymentVendor vendor(long id, String name) {
        PaymentVendor v = new PaymentVendor();
        ReflectionTestUtils.setField(v, "id", id);
        v.setVendorName(name);
        return v;
    }

    @Test
    void payout_totalsPerStatus_successRate_charges_andChangeSinceYesterdayAtTheSameTime() {
        when(payouts.dashboardTotalsByStatus(START, NOW)).thenReturn(rows(
                row(PayoutTransaction.PayoutStatus.SUCCESS, 86L, new BigDecimal("842500.00"), new BigDecimal("1720.00")),
                row(PayoutTransaction.PayoutStatus.FAILED, 7L, new BigDecimal("61000.00"), new BigDecimal("0")),
                row(PayoutTransaction.PayoutStatus.PENDING, 3L, new BigDecimal("24000.00"), new BigDecimal("0"))));
        when(payouts.dashboardTotalsByStatus(Y_START, Y_NOW)).thenReturn(rows(
                row(PayoutTransaction.PayoutStatus.SUCCESS, 70L, new BigDecimal("752232.14"), new BigDecimal("1500.00"))));

        PaymentsTodayDTO.Summary s = service.today(NOW).payout();

        assertEquals(86, s.success().count());
        assertEquals(0, new BigDecimal("842500.00").compareTo(s.success().amount()));
        assertEquals(7, s.failed().count());
        assertEquals(3, s.pending().count());
        assertEquals(0, new BigDecimal("24000.00").compareTo(s.pending().amount()));
        assertEquals(89.6, s.successRate());                       // 86 of 96
        assertEquals(12.0, s.changePercent());                     // 842500 vs 752232.14
        assertEquals(0, new BigDecimal("1720.00").compareTo(s.chargesCollected()));
    }

    @Test
    void payout_hasOneLinePerVendor_soANewVendorAppearsByItself_biggestFirst() {
        when(payouts.dashboardTotalsByVendor(START, NOW)).thenReturn(rows(
                row(7L, PayoutTransaction.PayoutStatus.SUCCESS, 80L, new BigDecimal("700000")),
                row(7L, PayoutTransaction.PayoutStatus.FAILED, 5L, new BigDecimal("40000")),
                row(9L, PayoutTransaction.PayoutStatus.SUCCESS, 6L, new BigDecimal("142500")),
                row(11L, PayoutTransaction.PayoutStatus.FAILED, 2L, new BigDecimal("9000"))));
        when(vendors.findAllById(any())).thenReturn(List.of(vendor(7L, "Vimo Pay"), vendor(9L, "New Vendor"), vendor(11L, "Third")));

        List<PaymentsTodayDTO.Line> lines = service.today(NOW).payout().lines();

        assertEquals(3, lines.size());
        assertEquals("Vimo Pay", lines.get(0).name());
        assertEquals(80, lines.get(0).count());                     // successful payouts only
        assertEquals("New Vendor", lines.get(1).name());
        assertEquals(6, lines.get(1).count());
        assertEquals("Third", lines.get(2).name());                // had payouts today, none successful
        assertEquals(0, lines.get(2).count());
        assertEquals(0, BigDecimal.ZERO.compareTo(lines.get(2).amount()));
    }

    @Test
    void payout_oldRowsWithoutAVendor_areShownAsOther() {
        when(payouts.dashboardTotalsByVendor(START, NOW)).thenReturn(rows(
                row(null, PayoutTransaction.PayoutStatus.SUCCESS, 2L, new BigDecimal("5000"))));

        List<PaymentsTodayDTO.Line> lines = service.today(NOW).payout().lines();

        assertEquals(1, lines.size());
        assertEquals("Other", lines.get(0).name());
        assertEquals(2, lines.get(0).count());
    }

    @Test
    void nothingToday_givesZerosAndNoPercentages() {
        PaymentsTodayDTO dto = service.today(NOW);

        assertEquals(0, dto.payout().success().count());
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.payout().success().amount()));
        assertNull(dto.payout().successRate());
        assertNull(dto.payout().changePercent());
        assertNull(dto.payout().topFailureReason());
        assertTrue(dto.payout().lines().isEmpty());
        assertNull(dto.billPayment().successRate());
        assertEquals(2, dto.billPayment().lines().size());       // Bill Avenue and SuivrePay always show
    }

    @Test
    void billPayment_addsBillAvenueAndSuivrePayTogether_withALineForEach() {
        when(bills.dashboardTotalsByStatus(START, NOW)).thenReturn(rows(
                row(BbpsTransaction.BbpsStatus.SUCCESS, 142L, new BigDecimal("318240"), new BigDecimal("2130")),
                row(BbpsTransaction.BbpsStatus.FAILED, 5L, new BigDecimal("9800"), new BigDecimal("0")),
                row(BbpsTransaction.BbpsStatus.PENDING, 2L, new BigDecimal("4100"), new BigDecimal("0"))));
        when(cards.dashboardTotalsByStatus(eq(CreditCardBillTransaction.Status.FETCHED), eq(START), eq(NOW))).thenReturn(rows(
                row(CreditCardBillTransaction.Status.SUCCESS, 18L, new BigDecimal("192000"), new BigDecimal("900")),
                row(CreditCardBillTransaction.Status.FAILED, 1L, new BigDecimal("5000"), new BigDecimal("0"))));

        PaymentsTodayDTO.Summary s = service.today(NOW).billPayment();

        assertEquals(160, s.success().count());                    // 142 + 18
        assertEquals(0, new BigDecimal("510240").compareTo(s.success().amount()));
        assertEquals(6, s.failed().count());
        assertEquals(2, s.pending().count());
        assertEquals(0, new BigDecimal("3030").compareTo(s.chargesCollected()));
        assertEquals("Bill Avenue", s.lines().get(0).name());
        assertEquals(142, s.lines().get(0).count());
        assertEquals(0, new BigDecimal("318240").compareTo(s.lines().get(0).amount()));
        assertEquals("SuivrePay (credit card)", s.lines().get(1).name());
        assertEquals(18, s.lines().get(1).count());
        assertEquals(0, new BigDecimal("192000").compareTo(s.lines().get(1).amount()));
    }

    @Test
    void aFetchedCreditCardLookup_isNotAPayment() {
        when(cards.dashboardTotalsByStatus(eq(CreditCardBillTransaction.Status.FETCHED), eq(START), eq(NOW))).thenReturn(rows(
                row(CreditCardBillTransaction.Status.FETCHED, 40L, new BigDecimal("900000"), new BigDecimal("0"))));

        PaymentsTodayDTO.Summary s = service.today(NOW).billPayment();

        assertEquals(0, s.success().count());
        assertEquals(0, s.pending().count());
        assertEquals(0, s.failed().count());
    }

    @Test
    void stuckPending_countsPendingOlderThanTheLimit_fromAnyDay_andTheTopFailureReasonIsTheMostCommon() {
        LocalDateTime cutoff = NOW.minusMinutes(30);
        when(payouts.dashboardStuckPending(PayoutTransaction.PayoutStatus.PENDING, cutoff)).thenReturn(2L);
        when(bills.dashboardStuckPending(BbpsTransaction.BbpsStatus.PENDING, cutoff)).thenReturn(1L);
        when(cards.dashboardStuckPending(CreditCardBillTransaction.Status.PENDING, cutoff)).thenReturn(3L);
        when(payouts.dashboardFailureReasons(eq(PayoutTransaction.PayoutStatus.FAILED), eq(START), eq(NOW), any(Pageable.class)))
                .thenReturn(rows(row("Beneficiary Daily Limit exhausted", 4L)));
        when(bills.dashboardFailureReasons(eq(BbpsTransaction.BbpsStatus.FAILED), eq(START), eq(NOW), any(Pageable.class)))
                .thenReturn(rows(row("Biller down", 2L), row("Invalid consumer", 1L)));
        when(cards.dashboardFailureReasons(eq(CreditCardBillTransaction.Status.FAILED), eq(START), eq(NOW), any(Pageable.class)))
                .thenReturn(rows(row("Biller down", 3L)));

        PaymentsTodayDTO dto = service.today(NOW);

        assertEquals(2, dto.payout().stuckPending());
        assertEquals(4, dto.billPayment().stuckPending());           // Bill Avenue 1 + credit card 3
        assertEquals(30, dto.stuckPendingMinutes());
        assertEquals("Beneficiary Daily Limit exhausted", dto.payout().topFailureReason().reason());
        assertEquals(4, dto.payout().topFailureReason().count());
        assertEquals("Biller down", dto.billPayment().topFailureReason().reason());
        assertEquals(5, dto.billPayment().topFailureReason().count());   // 2 + 3 from both tables
    }

    @Test
    void aVeryLongFailureReason_isCut() {
        when(payouts.dashboardFailureReasons(eq(PayoutTransaction.PayoutStatus.FAILED), eq(START), eq(NOW), any(Pageable.class)))
                .thenReturn(rows(row("x".repeat(500), 1L)));

        String reason = service.today(NOW).payout().topFailureReason().reason();

        assertTrue(reason.length() <= 120);
        assertTrue(reason.endsWith("…"));
    }

    @Test
    void payout_aVendorSetUpForPayoutShowsEvenWithNoPayoutToday_asZero() {
        PaymentVendor vimo = vendor(7L, "Vimo Pay");
        PaymentVendor second = vendor(9L, "Second Vendor");
        payoutVendorsSetUp(vimo, true, second, true);
        when(vendors.findAllById(any())).thenReturn(List.of(vimo, second));

        List<PaymentsTodayDTO.Line> lines = service.today(NOW).payout().lines();

        assertEquals(2, lines.size());                           // both show, with zeros, sorted by name
        assertEquals("Second Vendor", lines.get(0).name());
        assertEquals("Vimo Pay", lines.get(1).name());
        for (PaymentsTodayDTO.Line line : lines) {
            assertEquals(0, line.count());
            assertEquals(0, BigDecimal.ZERO.compareTo(line.amount()));
        }
    }

    @Test
    void payout_theDefaultPayoutVendorShowsEvenWhenNothingIsSetUp() {
        service = serviceWithDefaultVendor(7L);
        when(vendors.findAllById(any())).thenReturn(List.of(vendor(7L, "Vimo Pay")));

        List<PaymentsTodayDTO.Line> lines = service.today(NOW).payout().lines();

        assertEquals(1, lines.size());
        assertEquals("Vimo Pay", lines.get(0).name());
        assertEquals(0, lines.get(0).count());
    }

    @Test
    void payout_aDisabledVendorIsHidden_unlessItHadAPayoutToday() {
        PaymentVendor vimo = vendor(7L, "Vimo Pay");
        PaymentVendor old = vendor(8L, "Old Vendor");
        payoutVendorsSetUp(vimo, true, old, false);
        when(vendors.findAllById(any())).thenReturn(List.of(vimo));

        assertEquals(1, service.today(NOW).payout().lines().size());

        when(payouts.dashboardTotalsByVendor(START, NOW)).thenReturn(rows(
                row(8L, PayoutTransaction.PayoutStatus.SUCCESS, 1L, new BigDecimal("500"))));
        when(vendors.findAllById(any())).thenReturn(List.of(vimo, old));

        List<PaymentsTodayDTO.Line> lines = service.today(NOW).payout().lines();

        assertEquals(2, lines.size());
        assertEquals("Old Vendor", lines.get(0).name());         // has the money, so first
        assertEquals(1, lines.get(0).count());
        assertEquals("Vimo Pay", lines.get(1).name());
    }

    @Test
    void payout_aVendorSetUpAndPaidToday_isNotListedTwice() {
        PaymentVendor vimo = vendor(7L, "Vimo Pay");
        payoutVendorsSetUp(vimo, true);
        service = serviceWithDefaultVendor(7L);
        when(payouts.dashboardTotalsByVendor(START, NOW)).thenReturn(rows(
                row(7L, PayoutTransaction.PayoutStatus.SUCCESS, 3L, new BigDecimal("900"))));
        when(vendors.findAllById(any())).thenReturn(List.of(vimo));

        List<PaymentsTodayDTO.Line> lines = service.today(NOW).payout().lines();

        assertEquals(1, lines.size());
        assertEquals(3, lines.get(0).count());
    }
}
