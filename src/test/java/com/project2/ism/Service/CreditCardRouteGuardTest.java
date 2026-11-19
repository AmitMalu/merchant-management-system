package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlan;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlanVendor;
import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Repository.PaymentProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Credit card follows routing: the merchant gets the screen of the vendor routing chose, and the other one is
 * refused. It never restricts on doubt, and with the menu rules off (or not applying to the account) it does
 * nothing at all.
 */
class CreditCardRouteGuardTest {

    private static final CustomerRef MERCHANT = CustomerRef.merchant(2L);

    private MenuRulesSwitch rules;
    private PaymentRoutingConfigService config;
    private PaymentProductRepository products;
    private CreditCardRouteGuard guard;

    @BeforeEach
    void setUp() {
        rules = mock(MenuRulesSwitch.class);
        config = mock(PaymentRoutingConfigService.class);
        products = mock(PaymentProductRepository.class);
        guard = new CreditCardRouteGuard(rules, config, products);

        when(rules.applies(any())).thenReturn(true);
        when(products.findByProductCode("002")).thenReturn(Optional.of(mock(PaymentProduct.class)));
    }

    private void routeTo(String... vendorNames) {
        List<RoutePlanVendor> vendors = new java.util.ArrayList<>();
        int p = 1;
        for (String n : vendorNames) {
            vendors.add(new RoutePlanVendor((long) p, n, p, null, null));
            p++;
        }
        when(config.routePlan(any(), eq("MERCHANT"), eq(2L), any(), eq("Credit Card")))
                .thenReturn(new RoutePlan(true, 9L, "Bill Payment - Default", 3, 3, vendors, List.of()));
    }

    // ---------------------------------------------------------------- rules off / not for this account

    @Test
    void whenTheRulesDoNotApplyToTheAccount_nothingIsRead_andNothingIsBlocked() {
        when(rules.applies(any())).thenReturn(false);

        assertNull(guard.routedVendor(MERCHANT));
        assertFalse(guard.blocks(MERCHANT, CreditCardRouteGuard.SUIVREPAY));
        assertFalse(guard.blocks(MERCHANT, CreditCardRouteGuard.BILL_AVENUE));
        assertFalse(guard.blocksBillAvenueCreditCard(2L, "Credit Card"));
        verifyNoInteractions(config, products);
    }

    // ---------------------------------------------------------------- on: the routed vendor's screen only

    @Test
    void routedToSuivrePay_theBillAvenueScreenIsRefused_andTheSuivrePayOneIsNot() {
        routeTo("SuivrePay", "Bill Avenue");

        assertEquals("SUIVREPAY", guard.routedVendor(MERCHANT));
        assertFalse(guard.blocks(MERCHANT, CreditCardRouteGuard.SUIVREPAY));
        assertTrue(guard.blocks(MERCHANT, CreditCardRouteGuard.BILL_AVENUE));
        assertTrue(guard.blocksBillAvenueCreditCard(2L, "Credit Card"));
    }

    @Test
    void routedToBillAvenue_theSuivrePayScreenIsRefused() {
        routeTo("Bill Avenue", "SuivrePay");

        assertEquals("BILLAVENUE", guard.routedVendor(MERCHANT));
        assertTrue(guard.blocks(MERCHANT, CreditCardRouteGuard.SUIVREPAY));
        assertFalse(guard.blocksBillAvenueCreditCard(2L, "Credit Card"));
    }

    @Test
    void billAvenueAlsoPaysOtherCategories_andThoseAreNeverBlocked() {
        routeTo("SuivrePay");

        assertTrue(guard.blocksBillAvenueCreditCard(2L, "Credit Card"));
        assertTrue(guard.blocksBillAvenueCreditCard(2L, " credit card "));
        assertFalse(guard.blocksBillAvenueCreditCard(2L, "Electricity"));
        assertFalse(guard.blocksBillAvenueCreditCard(2L, null));
        assertFalse(guard.blocksBillAvenueCreditCard(null, "Credit Card"));
    }

    // ---------------------------------------------------------------- doubt means no restriction

    @Test
    void noRoutingForTheAccount_keepsBothScreens() {
        when(config.routePlan(any(), any(), any(), any(), any()))
                .thenReturn(new RoutePlan(false, null, null, 1, 0, List.of(), List.of("none")));

        assertNull(guard.routedVendor(MERCHANT));
        assertFalse(guard.blocks(MERCHANT, CreditCardRouteGuard.BILL_AVENUE));
    }

    @Test
    void aRoutingWithNoAllowedVendor_keepsBothScreens() {
        routeTo();

        assertNull(guard.routedVendor(MERCHANT));
    }

    @Test
    void aVendorItDoesNotRecognise_keepsBothScreens() {
        routeTo("Some Other Bank");

        assertNull(guard.routedVendor(MERCHANT));
        assertFalse(guard.blocks(MERCHANT, CreditCardRouteGuard.SUIVREPAY));
    }

    @Test
    void aFranchiseIsNeverRestricted_becauseItHasNoBillAvenueScreen() {
        CustomerRef franchise = CustomerRef.franchise(2L);
        when(config.routePlan(any(), eq("FRANCHISE"), any(), any(), any()))
                .thenReturn(new RoutePlan(true, 9L, "x", 3, 3,
                        List.of(new RoutePlanVendor(1L, "Bill Avenue", 1, null, null)), List.of()));

        assertNull(guard.routedVendor(franchise));
        assertFalse(guard.blocks(franchise, CreditCardRouteGuard.SUIVREPAY));
    }

    @Test
    void ifTheRoutingCannotBeRead_nobodyIsBlocked() {
        when(config.routePlan(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("db down"));

        assertNull(guard.routedVendor(MERCHANT));
        assertFalse(guard.blocks(MERCHANT, CreditCardRouteGuard.SUIVREPAY));
        assertFalse(guard.blocksBillAvenueCreditCard(2L, "Credit Card"));
    }

    @Test
    void aMissingBillPaymentProduct_blocksNobody() {
        when(products.findByProductCode("002")).thenReturn(Optional.empty());

        assertNull(guard.routedVendor(MERCHANT));
        verifyNoInteractions(config);
    }

    @Test
    void vendorNamesAreRecognisedLoosely() {
        assertEquals("BILLAVENUE", CreditCardRouteGuard.codeOf("Bill Avenue"));
        assertEquals("BILLAVENUE", CreditCardRouteGuard.codeOf("BillAvenue"));
        assertEquals("BILLAVENUE", CreditCardRouteGuard.codeOf("bill-avenue (BBPS)"));
        assertEquals("SUIVREPAY", CreditCardRouteGuard.codeOf("SuivrePay"));
        assertEquals("SUIVREPAY", CreditCardRouteGuard.codeOf("Suivre Pay"));
        assertNull(CreditCardRouteGuard.codeOf("Vimo Pay"));
        assertNull(CreditCardRouteGuard.codeOf(null));
    }
}
