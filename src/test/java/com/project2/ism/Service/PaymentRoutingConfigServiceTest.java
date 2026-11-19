package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.*;
import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Routing.PaymentProductVendor;
import com.project2.ism.Model.Routing.PaymentRoutingAssignment;
import com.project2.ism.Model.Routing.PaymentRoutingProfile;
import com.project2.ism.Model.Routing.PaymentRoutingProfileVendor;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PaymentProductRepository;
import com.project2.ism.Repository.PaymentProductVendorRepository;
import com.project2.ism.Repository.PaymentRoutingAssignmentRepository;
import com.project2.ism.Repository.PaymentRoutingAuditRepository;
import com.project2.ism.Repository.PaymentRoutingProfileRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Vendor routing configuration: what is accepted and refused, who can be assigned, and what the simulator
 * says. Nothing here touches a payment: phase 1 only stores and checks settings.
 */
class PaymentRoutingConfigServiceTest {

    private PaymentProductRepository products;
    private PaymentVendorRepository vendors;
    private PaymentProductVendorRepository links;
    private PaymentRoutingProfileRepository profiles;
    private PaymentRoutingAssignmentRepository assignments;
    private PaymentRoutingAuditRepository audits;
    private MerchantRepository merchants;
    private FranchiseRepository franchises;
    private PaymentRoutingConfigService service;

    private PaymentProduct payout;
    private PaymentVendor vimo;
    private PaymentVendor billAvenue;
    private PaymentVendor suivre;

    @BeforeEach
    void setUp() {
        products = mock(PaymentProductRepository.class);
        vendors = mock(PaymentVendorRepository.class);
        links = mock(PaymentProductVendorRepository.class);
        profiles = mock(PaymentRoutingProfileRepository.class);
        assignments = mock(PaymentRoutingAssignmentRepository.class);
        audits = mock(PaymentRoutingAuditRepository.class);
        merchants = mock(MerchantRepository.class);
        franchises = mock(FranchiseRepository.class);
        service = new PaymentRoutingConfigService(products, vendors, links, profiles, assignments, audits,
                merchants, franchises);

        payout = product(1L, "001", "Payout");
        vimo = vendor(1L, "ViMoPay");
        billAvenue = vendor(2L, "Bill Avenue");
        suivre = vendor(3L, "SuivrePay");

        when(products.findById(1L)).thenReturn(Optional.of(payout));
        when(vendors.findById(1L)).thenReturn(Optional.of(vimo));
        when(vendors.findById(2L)).thenReturn(Optional.of(billAvenue));
        when(vendors.findById(3L)).thenReturn(Optional.of(suivre));
        when(profiles.save(any())).thenAnswer(i -> i.getArgument(0));
        when(audits.save(any())).thenAnswer(i -> i.getArgument(0));
        link(payout, vimo, null, true);
    }

    // ---------------------------------------------------------------- helpers

    private static PaymentProduct product(Long id, String code, String name) {
        PaymentProduct p = mock(PaymentProduct.class);
        when(p.getId()).thenReturn(id);
        when(p.getProductCode()).thenReturn(code);
        when(p.getProductName()).thenReturn(name);
        when(p.getStatus()).thenReturn(true);
        return p;
    }

    private static PaymentVendor vendor(Long id, String name) {
        PaymentVendor v = mock(PaymentVendor.class);
        when(v.getId()).thenReturn(id);
        when(v.getVendorName()).thenReturn(name);
        when(v.getStatus()).thenReturn(true);
        return v;
    }

    private void link(PaymentProduct p, PaymentVendor v, String categories, boolean enabled) {
        PaymentProductVendor l = new PaymentProductVendor();
        l.setProduct(p);
        l.setVendor(v);
        l.setCategories(categories);
        l.setEnabled(enabled);
        when(links.findByProductAndVendor(p.getId(), v.getId())).thenReturn(Optional.of(l));
    }

    private static RoutingVendorRequest rv(Long vendorId) {
        return new RoutingVendorRequest(vendorId, true, null, null, null, null);
    }

    private static RoutingRequest request(RoutingVendorRequest... vs) {
        return new RoutingRequest(1L, "Payout - Main", true, null, null, List.of(vs));
    }

    private static String refusal(Runnable r) {
        return assertThrows(IllegalArgumentException.class, r::run).getMessage();
    }

    // ---------------------------------------------------------------- creating a routing

    @Test
    void vendor1IsMandatory_vendors2And3AreOptional() {
        assertTrue(refusal(() -> service.createProfile(new RoutingRequest(1L, "Payout - Main", true, null, null, List.of())))
                .contains("Vendor 1 is required"));

        RoutingDto created = service.createProfile(request(rv(1L)));

        assertEquals(1, created.vendors().size());
        assertEquals(1, created.vendors().get(0).priority());
        assertEquals(3, created.retryCount());          // default: three retries
        assertEquals(3, created.retryWaitSeconds());
        verify(audits).save(any());
    }

    @Test
    void severalVendorsAreSavedInTheOrderGiven() {
        link(payout, billAvenue, null, true);

        RoutingDto created = service.createProfile(request(rv(1L), rv(2L)));

        assertEquals(List.of("ViMoPay", "Bill Avenue"), created.vendors().stream().map(RoutingVendorDto::vendorName).toList());
        assertEquals(List.of(1, 2), created.vendors().stream().map(RoutingVendorDto::priority).toList());
    }

    @Test
    void theSameVendorTwice_aVendorNotLinkedToTheProduct_orInactive_areRefused() {
        assertTrue(refusal(() -> service.createProfile(request(rv(1L), rv(1L)))).contains("more than once"));

        // Bill Avenue is not linked to Payout
        when(links.findByProductAndVendor(1L, 2L)).thenReturn(Optional.empty());
        assertTrue(refusal(() -> service.createProfile(request(rv(1L), rv(2L)))).contains("not linked"));

        link(payout, billAvenue, null, true);
        when(billAvenue.getStatus()).thenReturn(false);
        assertTrue(refusal(() -> service.createProfile(request(rv(1L), rv(2L)))).contains("inactive"));

        verify(profiles, never()).save(any());
    }

    @Test
    void monthlyAndYearlyLimitsAreSaved_andShownBack() {
        RoutingVendorRequest limits = new RoutingVendorRequest(1L, true, new BigDecimal("100"), new BigDecimal("99999"),
                10, new BigDecimal("200000"), 100, new BigDecimal("5000000"), 1000, new BigDecimal("50000000"));

        RoutingVendorDto saved = service.createProfile(request(limits)).vendors().get(0);

        assertEquals(10, saved.dailyTransactionLimit());
        assertEquals(100, saved.monthlyTransactionLimit());
        assertEquals(new BigDecimal("5000000"), saved.monthlyAmountLimit());
        assertEquals(1000, saved.yearlyTransactionLimit());
        assertEquals(new BigDecimal("50000000"), saved.yearlyAmountLimit());

        // a routing saved without them (as before) has none
        RoutingVendorDto plain = service.createProfile(request(rv(1L))).vendors().get(0);
        assertNull(plain.monthlyTransactionLimit());
        assertNull(plain.yearlyAmountLimit());
    }

    @Test
    void badMonthlyAndYearlyLimitsAreRefused() {
        assertTrue(refusal(() -> service.createProfile(request(
                new RoutingVendorRequest(1L, true, null, null, null, null, 0, null, null, null))))
                .contains("monthly transaction limit"));
        assertTrue(refusal(() -> service.createProfile(request(
                new RoutingVendorRequest(1L, true, null, null, null, null, null, new BigDecimal("-5"), null, null))))
                .contains("monthly amount limit"));
        assertTrue(refusal(() -> service.createProfile(request(
                new RoutingVendorRequest(1L, true, null, null, null, null, null, null, 0, null))))
                .contains("yearly transaction limit"));
        assertTrue(refusal(() -> service.createProfile(request(
                new RoutingVendorRequest(1L, true, null, null, null, null, null, null, null, BigDecimal.ZERO))))
                .contains("yearly amount limit"));
    }

    @Test
    void aLongerPeriodCannotAllowLessThanAShorterOne() {
        // monthly 5 < daily 10: the monthly limit could never be what stops a payout
        assertTrue(refusal(() -> service.createProfile(request(
                new RoutingVendorRequest(1L, true, null, null, 10, null, 5, null, null, null))))
                .contains("monthly limit cannot be smaller than the daily limit"));
        assertTrue(refusal(() -> service.createProfile(request(
                new RoutingVendorRequest(1L, true, null, null, null, null, 50, null, 20, null))))
                .contains("yearly limit cannot be smaller than the monthly limit"));
        assertTrue(refusal(() -> service.createProfile(request(
                new RoutingVendorRequest(1L, true, null, null, null, new BigDecimal("1000"),
                        null, new BigDecimal("500"), null, null))))
                .contains("monthly limit cannot be smaller than the daily limit"));
        // equal is fine, and a limit set on only one of the periods is fine
        service.createProfile(request(new RoutingVendorRequest(1L, true, null, null, 10, null, 10, null, 10, null)));
        service.createProfile(request(new RoutingVendorRequest(1L, true, null, null, 10, null, null, null, 5000, null)));
    }

    @Test
    void routePlanCarriesTheMonthlyAndYearlyLimits() {
        PaymentRoutingProfile p = profileWith(10L, "Payout - Main");
        p.getVendors().get(0).setMonthlyTransactionLimit(100);
        p.getVendors().get(0).setMonthlyAmountLimit(new BigDecimal("5000000"));
        p.getVendors().get(0).setYearlyTransactionLimit(1000);
        p.getVendors().get(0).setYearlyAmountLimit(new BigDecimal("50000000"));
        PaymentRoutingAssignment a = new PaymentRoutingAssignment();
        a.setProfile(p);
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.of(a));

        var vendor = service.routePlan(payout, "MERCHANT", 5L, new BigDecimal("1000"), null).vendors().get(0);

        assertEquals(100, vendor.monthlyTransactionLimit());
        assertEquals(new BigDecimal("5000000"), vendor.monthlyAmountLimit());
        assertEquals(1000, vendor.yearlyTransactionLimit());
        assertEquals(new BigDecimal("50000000"), vendor.yearlyAmountLimit());
    }

    @Test
    void vendor1CannotBeSwitchedOff() {
        RoutingVendorRequest off = new RoutingVendorRequest(1L, false, null, null, null, null);
        assertTrue(refusal(() -> service.createProfile(request(off))).contains("Vendor 1 must be switched on"));
    }

    @Test
    void badLimitsAndRetrySettingsAreRefused() {
        RoutingVendorRequest minAboveMax = new RoutingVendorRequest(1L, true, new BigDecimal("500"), new BigDecimal("100"), null, null);
        assertTrue(refusal(() -> service.createProfile(request(minAboveMax))).contains("less than the maximum"));

        RoutingVendorRequest zeroCount = new RoutingVendorRequest(1L, true, null, null, 0, null);
        assertTrue(refusal(() -> service.createProfile(request(zeroCount))).contains("daily transaction limit"));

        RoutingVendorRequest negativeAmount = new RoutingVendorRequest(1L, true, null, null, null, new BigDecimal("-1"));
        assertTrue(refusal(() -> service.createProfile(request(negativeAmount))).contains("daily amount limit"));

        RoutingRequest tooManyRetries = new RoutingRequest(1L, "Payout - Main", true, 50, 3, List.of(rv(1L)));
        assertTrue(refusal(() -> service.createProfile(tooManyRetries)).contains("Retries"));

        RoutingRequest tooLongWait = new RoutingRequest(1L, "Payout - Main", true, 3, 500, List.of(rv(1L)));
        assertTrue(refusal(() -> service.createProfile(tooLongWait)).contains("Wait between attempts"));
    }

    @Test
    void aRoutingNameIsUniquePerProduct() {
        when(profiles.existsByProduct_IdAndNameIgnoreCase(1L, "Payout - Main")).thenReturn(true);
        assertTrue(refusal(() -> service.createProfile(request(rv(1L)))).contains("already exists"));
    }

    // ---------------------------------------------------------------- assignments

    private PaymentRoutingProfile profileWith(Long id, String name) {
        PaymentRoutingProfile p = new PaymentRoutingProfile();
        p.setProduct(payout);
        p.setName(name);
        p.setStatus(true);
        ReflectionTestUtils.setField(p, "id", id);
        PaymentRoutingProfileVendor v1 = new PaymentRoutingProfileVendor();
        v1.setVendor(vimo);
        v1.setPriority(1);
        v1.setEnabled(true);
        p.addVendor(v1);
        when(profiles.findByIdWithVendors(id)).thenReturn(Optional.of(p));
        return p;
    }

    private Merchant merchant(Long id, String name, Boolean payoutOn) {
        Merchant m = mock(Merchant.class);
        when(m.getId()).thenReturn(id);
        when(m.getBusinessName()).thenReturn(name);
        when(m.getPayout()).thenReturn(payoutOn);
        return m;
    }

    @Test
    void anAccountAlreadyOnAnotherRouting_stopsTheWholeAssignment() {
        PaymentRoutingProfile main = profileWith(10L, "Payout - Main");
        PaymentRoutingProfile other = profileWith(11L, "Payout - Other");
        Merchant ramesh = merchant(5L, "Ramesh Traders", true);
        Merchant suresh = merchant(6L, "Suresh Stores", true);
        when(merchants.findAllById(any())).thenReturn(List.of(ramesh, suresh));

        PaymentRoutingAssignment existing = new PaymentRoutingAssignment();
        existing.setProfile(other);
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.of(existing));
        when(assignments.findForAccount(1L, "MERCHANT", 6L)).thenReturn(Optional.empty());

        String message = refusal(() -> service.addAssignments(10L, List.of(
                new TargetRef("MERCHANT", 5L), new TargetRef("MERCHANT", 6L))));

        assertTrue(message.contains("Ramesh Traders"), message);
        assertTrue(message.contains("Payout - Other"), message);
        verify(assignments, never()).saveAll(any());        // all or nothing: Suresh was not assigned either
        assertNotNull(main);
    }

    @Test
    void newAccountsAreAssigned_andAnAccountAlreadyOnThisRoutingIsSkipped() {
        PaymentRoutingProfile main = profileWith(10L, "Payout - Main");
        Merchant ramesh = merchant(5L, "Ramesh Traders", true);
        Merchant suresh = merchant(6L, "Suresh Stores", true);
        when(merchants.findAllById(any())).thenReturn(List.of(ramesh, suresh));

        PaymentRoutingAssignment already = new PaymentRoutingAssignment();
        already.setProfile(main);
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.of(already));
        when(assignments.findForAccount(1L, "MERCHANT", 6L)).thenReturn(Optional.empty());

        int added = service.addAssignments(10L, List.of(new TargetRef("MERCHANT", 5L), new TargetRef("MERCHANT", 6L)));

        assertEquals(1, added);
    }

    @Test
    void aRoutingWithAssignedAccountsCannotBeDeleted() {
        profileWith(10L, "Payout - Main");
        when(assignments.countByProfile_Id(10L)).thenReturn(7L);

        assertTrue(refusal(() -> service.deleteProfile(10L)).contains("7 account(s)"));
        verify(profiles, never()).delete(any());
    }

    // ---------------------------------------------------------------- the simulator

    @Test
    void simulator_picksTheFirstVendorThatCanServe_andExplainsTheOthers() {
        PaymentRoutingProfile p = profileWith(10L, "Payout - Main");
        link(payout, billAvenue, null, true);
        PaymentRoutingProfileVendor second = new PaymentRoutingProfileVendor();
        second.setVendor(billAvenue);
        second.setPriority(2);
        second.setEnabled(true);
        p.addVendor(second);
        // vendor 1 only up to 5,000
        p.getVendors().get(0).setMaxAmount(new BigDecimal("5000"));

        Merchant ramesh = merchant(5L, "Ramesh Traders", true);
        when(merchants.findAllById(any())).thenReturn(List.of(ramesh));
        PaymentRoutingAssignment a = new PaymentRoutingAssignment();
        a.setProfile(p);
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.of(a));

        SimulationResult small = service.simulate(1L, "MERCHANT", 5L, new BigDecimal("1000"), null);
        assertEquals("ViMoPay", small.chosenVendor());

        SimulationResult big = service.simulate(1L, "MERCHANT", 5L, new BigDecimal("9000"), null);
        assertEquals("Bill Avenue", big.chosenVendor());
        assertFalse(big.vendors().get(0).eligible());
        assertTrue(big.vendors().get(0).reason().contains("above this vendor's maximum"), big.vendors().get(0).reason());
    }

    @Test
    void simulator_skipsAVendorThatDoesNotServeTheCategory() {
        PaymentProduct bill = product(2L, "002", "Bill Payment");
        when(products.findById(2L)).thenReturn(Optional.of(bill));
        link(bill, suivre, "Credit Card", true);
        link(bill, billAvenue, null, true);

        PaymentRoutingProfile p = new PaymentRoutingProfile();
        p.setProduct(bill);
        p.setName("Bill Payment - Default");
        p.setStatus(true);
        ReflectionTestUtils.setField(p, "id", 20L);
        PaymentRoutingProfileVendor first = new PaymentRoutingProfileVendor();
        first.setVendor(suivre);
        first.setPriority(1);
        first.setEnabled(true);
        PaymentRoutingProfileVendor second = new PaymentRoutingProfileVendor();
        second.setVendor(billAvenue);
        second.setPriority(2);
        second.setEnabled(true);
        p.addVendor(first);
        p.addVendor(second);
        when(profiles.findByIdWithVendors(20L)).thenReturn(Optional.of(p));

        Merchant m = merchant(5L, "Ramesh Traders", true);
        when(merchants.findAllById(any())).thenReturn(List.of(m));
        PaymentRoutingAssignment a = new PaymentRoutingAssignment();
        a.setProfile(p);
        when(assignments.findForAccount(2L, "MERCHANT", 5L)).thenReturn(Optional.of(a));

        // SuivrePay is first but serves only credit cards: electricity must go to Bill Avenue
        assertEquals("Bill Avenue", service.simulate(2L, "MERCHANT", 5L, new BigDecimal("500"), "Electricity").chosenVendor());
        assertEquals("SuivrePay", service.simulate(2L, "MERCHANT", 5L, new BigDecimal("500"), "credit card").chosenVendor());
    }

    @Test
    void simulator_saysSoWhenAnAccountHasNoRouting() {
        Merchant m = merchant(5L, "Ramesh Traders", true);
        when(merchants.findAllById(any())).thenReturn(List.of(m));
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.empty());

        SimulationResult result = service.simulate(1L, "MERCHANT", 5L, new BigDecimal("100"), null);

        assertNull(result.chosenVendor());
        assertTrue(result.message().contains("no routing"), result.message());
    }

    // ---------------------------------------------------------------- the decision used by the trial run

    @Test
    void decide_picksTheFirstVendorThatCanServe_andExplainsWhy() {
        PaymentRoutingProfile p = profileWith(10L, "Payout - Main");
        link(payout, billAvenue, null, true);
        PaymentRoutingProfileVendor second = new PaymentRoutingProfileVendor();
        second.setVendor(billAvenue);
        second.setPriority(2);
        second.setEnabled(true);
        p.addVendor(second);
        p.getVendors().get(0).setMaxAmount(new BigDecimal("5000"));
        PaymentRoutingAssignment a = new PaymentRoutingAssignment();
        a.setProfile(p);
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.of(a));

        Decision small = service.decide(payout, "MERCHANT", 5L, new BigDecimal("1000"), null);
        assertEquals("ViMoPay", small.chosenVendorName());
        assertFalse(small.noRouting());

        Decision big = service.decide(payout, "MERCHANT", 5L, new BigDecimal("9000"), null);
        assertEquals("Bill Avenue", big.chosenVendorName());
        assertTrue(big.summary().contains("above this vendor's maximum"), big.summary());
    }

    @Test
    void decide_saysNoRoutingWithoutThrowing() {
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.empty());

        Decision d = service.decide(payout, "MERCHANT", 5L, new BigDecimal("100"), null);

        assertTrue(d.noRouting());
        assertNull(d.chosenVendorId());
    }

    @Test
    void decide_aSwitchedOffRoutingChoosesNothing() {
        PaymentRoutingProfile p = profileWith(10L, "Payout - Main");
        p.setStatus(false);
        PaymentRoutingAssignment a = new PaymentRoutingAssignment();
        a.setProfile(p);
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.of(a));

        Decision d = service.decide(payout, "MERCHANT", 5L, new BigDecimal("100"), null);

        assertTrue(d.noRouting());
        assertNull(d.chosenVendorId());
    }

    // ---------------------------------------------------------------- the plan used by real payments

    @Test
    void routePlan_listsEveryVendorThatCanServe_inOrder_withLimitsAndRetrySettings() {
        PaymentRoutingProfile p = profileWith(10L, "Payout - Main");
        link(payout, billAvenue, null, true);
        PaymentRoutingProfileVendor second = new PaymentRoutingProfileVendor();
        second.setVendor(billAvenue);
        second.setPriority(2);
        second.setEnabled(true);
        p.addVendor(second);
        p.getVendors().get(0).setMaxAmount(new BigDecimal("5000"));
        p.getVendors().get(0).setDailyTransactionLimit(7);
        PaymentRoutingAssignment a = new PaymentRoutingAssignment();
        a.setProfile(p);
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.of(a));

        RoutePlan small = service.routePlan(payout, "MERCHANT", 5L, new BigDecimal("1000"), null);
        assertTrue(small.hasRouting());
        assertEquals(2, small.vendors().size());
        assertEquals("ViMoPay", small.vendors().get(0).vendorName());
        assertEquals(7, small.vendors().get(0).dailyTransactionLimit());
        assertEquals(3, small.retryCount());
        assertEquals(3, small.retryWaitSeconds());

        RoutePlan big = service.routePlan(payout, "MERCHANT", 5L, new BigDecimal("9000"), null);
        assertEquals(1, big.vendors().size());
        assertEquals("Bill Avenue", big.vendors().get(0).vendorName());
        assertTrue(big.notes().get(0).contains("above this vendor's maximum"), big.notes().toString());
        // the words for a merchant: the allowed amount, never a vendor name
        assertEquals(1, big.refusals().size());
        assertEquals(1, big.refusals().get(0).priority());
        assertEquals("Amount is above the maximum allowed (5000).", big.refusals().get(0).message());
    }

    @Test
    void routePlan_aVendorThatIsNotUsable_hasNoShowableReason() {
        PaymentRoutingProfile p = profileWith(10L, "Payout - Main");
        p.getVendors().get(0).setEnabled(false);          // switched off inside this routing
        PaymentRoutingAssignment a = new PaymentRoutingAssignment();
        a.setProfile(p);
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.of(a));

        RoutePlan plan = service.routePlan(payout, "MERCHANT", 5L, new BigDecimal("1000"), null);

        assertTrue(plan.vendors().isEmpty());
        assertEquals(1, plan.refusals().size());
        assertNull(plan.refusals().get(0).message());      // the caller says "not available right now"
    }

    // ---------------------------------------------------------------- categories and the menu rule helpers

    @Test
    void categoriesFor_billPaymentListsTheBillAvenueServices_andWhatIsAlreadySaved_payoutHasNone() {
        PaymentProduct bill = product(5L, "002", "Bill Payment");
        when(products.findById(5L)).thenReturn(Optional.of(bill));
        com.project2.ism.Repository.BillAvenueConfigRepository billers =
                mock(com.project2.ism.Repository.BillAvenueConfigRepository.class);
        when(billers.findDistinctServicesByVendorName("Bill Avenue"))
                .thenReturn(List.of("Electricity", "Credit Card", " ", "Water"));
        ReflectionTestUtils.setField(service, "billerRepository", billers);
        PaymentProductVendor saved = new PaymentProductVendor();
        saved.setProduct(bill);
        saved.setVendor(suivre);
        saved.setCategories("credit card, Insurance");
        saved.setEnabled(true);
        when(links.findAllWithDetails(5L)).thenReturn(List.of(saved));

        assertEquals(List.of("Credit Card", "Electricity", "Insurance", "Water"), service.categoriesFor(5L));
        assertTrue(service.categoriesFor(1L).isEmpty());      // Payout has no categories
    }

    @Test
    void hasActiveRouting_isTrueOnlyForAnAssignedRoutingThatIsSwitchedOn() {
        PaymentRoutingProfile p = profileWith(10L, "Payout - Main");
        PaymentRoutingAssignment a = new PaymentRoutingAssignment();
        a.setProfile(p);
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.of(a));

        assertTrue(service.hasActiveRouting(payout, "MERCHANT", 5L));
        assertFalse(service.hasActiveRouting(payout, "MERCHANT", 6L));        // nothing assigned
        p.setStatus(false);
        assertFalse(service.hasActiveRouting(payout, "MERCHANT", 5L));        // the routing is switched off
    }

    @Test
    void routePlan_noRoutingOrASwitchedOffRoutingMeansTheCallerKeepsItsFixedVendor() {
        when(assignments.findForAccount(1L, "MERCHANT", 5L)).thenReturn(Optional.empty());
        assertFalse(service.routePlan(payout, "MERCHANT", 5L, new BigDecimal("100"), null).hasRouting());

        PaymentRoutingProfile p = profileWith(10L, "Payout - Main");
        p.setStatus(false);
        PaymentRoutingAssignment a = new PaymentRoutingAssignment();
        a.setProfile(p);
        when(assignments.findForAccount(1L, "MERCHANT", 6L)).thenReturn(Optional.of(a));
        RoutePlan off = service.routePlan(payout, "MERCHANT", 6L, new BigDecimal("100"), null);
        assertFalse(off.hasRouting());
        assertTrue(off.vendors().isEmpty());
    }

    // ---------------------------------------------------------------- the menu rule

    @Test
    void myProducts_listsOnlyActiveRoutings_andReportsWhetherTheRuleIsOn() {
        PaymentRoutingProfile on = profileWith(10L, "Payout - Main");
        PaymentRoutingProfile off = profileWith(11L, "Payout - Off");
        off.setStatus(false);
        PaymentRoutingAssignment a1 = new PaymentRoutingAssignment();
        a1.setProfile(on);
        a1.setProduct(payout);
        PaymentRoutingAssignment a2 = new PaymentRoutingAssignment();
        a2.setProfile(off);
        a2.setProduct(payout);
        when(assignments.findAllForAccount("MERCHANT", 5L)).thenReturn(List.of(a1, a2));

        MyProducts mine = service.myProducts(CustomerRef.merchant(5L));

        assertFalse(mine.gatingEnabled());                  // off by default
        assertEquals(1, mine.products().size());
        assertEquals("Payout", mine.products().get(0).productName());

        ReflectionTestUtils.setField(service, "gatingEnabled", true);
        assertTrue(service.myProducts(CustomerRef.merchant(5L)).gatingEnabled());
    }

    // ---------------------------------------------------------------- first-time setup

    @Test
    void firstTimeSetupPreview_countsWhatItWouldDo_andChangesNothing() {
        Merchant yes = merchant(5L, "Ramesh Traders", true);
        Merchant no = merchant(6L, "Suresh Stores", false);
        when(merchants.findAll()).thenReturn(List.of(yes, no));
        Franchise legacy = mock(Franchise.class);        // payout empty = still works
        when(legacy.getId()).thenReturn(3L);
        when(legacy.getPayout()).thenReturn(null);
        when(franchises.findAll()).thenReturn(List.of(legacy));
        when(links.findByProductAndVendor(1L, 1L)).thenReturn(Optional.empty());     // not linked yet
        when(profiles.findAllWithVendors(1L)).thenReturn(List.of());
        when(assignments.findByProductAndType(anyLong(), any())).thenReturn(List.of());

        // the admin chose ViMoPay as Vendor 1 for Payout
        SeedResult result = service.seedDefaults(true,
                new SeedRequest(List.of(new SeedProductChoice(1L, List.of(new SeedVendorChoice(1L, null))))));

        assertTrue(result.dryRun());
        assertEquals(1, result.linksCreated());
        assertEquals(1, result.profilesCreated());
        assertEquals(2, result.assignmentsCreated());       // the merchant with payout on + the legacy franchise
        verify(links, never()).save(any());
        verify(profiles, never()).save(any());
        verify(assignments, never()).saveAll(any());
    }

    @Test
    void firstTimeSetup_neverPicksAVendorItself() {
        // nothing chosen at all, or a product without Vendor 1: refused / skipped, nothing is created
        assertTrue(refusal(() -> service.seedDefaults(false, null)).contains("Choose Vendor 1"));
        assertTrue(refusal(() -> service.seedDefaults(false, new SeedRequest(List.of()))).contains("Choose Vendor 1"));
        assertTrue(refusal(() -> service.seedDefaults(false,
                new SeedRequest(List.of(new SeedProductChoice(1L, List.of()))))).contains("Choose Vendor 1"));

        // the same vendor twice is refused
        assertTrue(refusal(() -> service.seedDefaults(false, new SeedRequest(List.of(new SeedProductChoice(1L,
                List.of(new SeedVendorChoice(1L, null), new SeedVendorChoice(1L, null))))))).contains("more than once"));

        verify(profiles, never()).save(any());
        verify(links, never()).save(any());
        verify(assignments, never()).saveAll(any());
    }

    @Test
    void firstTimeSetup_keepsTheVendorOrderOfARoutingThatAlreadyExists() {
        PaymentRoutingProfile existing = profileWith(10L, "Payout - Default");
        when(profiles.findAllWithVendors(1L)).thenReturn(List.of(existing));
        when(links.findByProductAndVendor(1L, 1L)).thenReturn(Optional.empty());
        when(merchants.findAll()).thenReturn(List.of());
        when(franchises.findAll()).thenReturn(List.of());
        when(assignments.findByProductAndType(anyLong(), any())).thenReturn(List.of());

        SeedResult result = service.seedDefaults(true,
                new SeedRequest(List.of(new SeedProductChoice(1L, List.of(new SeedVendorChoice(1L, null))))));

        assertEquals(0, result.profilesCreated());
        assertTrue(result.lines().stream().anyMatch(l -> l.contains("order are not changed")), result.lines().toString());
    }

    // ---------------------------------------------------------------- small rules

    @Test
    void categoriesAreTidied_andEmptyMeansEveryCategory() {
        assertEquals("Credit Card, Electricity", PaymentRoutingConfigService.normalizeCategories(" Credit  Card , credit card,Electricity ,"));
        assertNull(PaymentRoutingConfigService.normalizeCategories("  , "));
        assertTrue(PaymentRoutingConfigService.servesCategory(null, "Anything"));
        assertTrue(PaymentRoutingConfigService.servesCategory("Credit Card", "credit card"));
        assertFalse(PaymentRoutingConfigService.servesCategory("Credit Card", "Electricity"));
    }

    @Test
    void aProductVendorLinkInUseCannotBeSwitchedOffOrRemoved() {
        when(profiles.vendorUsedInProduct(1L, 1L)).thenReturn(true);

        assertTrue(refusal(() -> service.saveProductVendor(new ProductVendorRequest(1L, 1L, null, false)))
                .contains("Remove it from that routing first"));

        PaymentProductVendor l = new PaymentProductVendor();
        l.setProduct(payout);
        l.setVendor(vimo);
        when(links.findById(9L)).thenReturn(Optional.of(l));
        assertTrue(refusal(() -> service.removeProductVendor(9L)).contains("Remove it from that routing first"));
        verify(links, never()).delete(any());
        verify(links, never()).save(eq(null));
    }
}
