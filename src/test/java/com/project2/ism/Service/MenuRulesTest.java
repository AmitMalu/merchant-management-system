package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.MyMenu;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlan;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.RoutePlanVendor;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.TargetRef;
import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Model.Routing.PaymentRoutingSetting;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PaymentProductRepository;
import com.project2.ism.Repository.PaymentRoutingAuditRepository;
import com.project2.ism.Repository.PaymentRoutingSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Menu Rules: the switch (off by default, only the chosen accounts, or everyone) and what each account then sees.
 * A menu is hidden only when the rule clearly says so; any doubt shows everything.
 */
class MenuRulesTest {

    private static final CustomerRef MERCHANT = CustomerRef.merchant(2L);
    private static final CustomerRef FRANCHISE = CustomerRef.franchise(4L);
    private static final MyMenu EVERYTHING = new MyMenu(false, true, true, true);

    private PaymentRoutingSettingRepository settings;
    private MerchantRepository merchants;
    private FranchiseRepository franchises;
    private MenuRulesSwitch rules;

    private PaymentRoutingConfigService config;
    private PaymentProductRepository products;
    private MenuRulesService service;
    private PaymentProduct payout;
    private PaymentProduct bill;

    @BeforeEach
    void setUp() {
        settings = mock(PaymentRoutingSettingRepository.class);
        merchants = mock(MerchantRepository.class);
        franchises = mock(FranchiseRepository.class);
        rules = new MenuRulesSwitch(settings, mock(PaymentRoutingAuditRepository.class), merchants, franchises);
        when(settings.findById(anyString())).thenReturn(Optional.empty());
        when(settings.save(any())).thenAnswer(i -> i.getArgument(0));

        Merchant m = mock(Merchant.class);
        when(m.getId()).thenReturn(2L);
        when(m.getBusinessName()).thenReturn("EXPLOREMORE");
        when(merchants.findAllById(any())).thenReturn(List.of(m));
        Franchise f = mock(Franchise.class);
        when(f.getId()).thenReturn(4L);
        when(f.getFranchiseName()).thenReturn("AAMBIKA");
        when(franchises.findAllById(any())).thenReturn(List.of(f));

        config = mock(PaymentRoutingConfigService.class);
        products = mock(PaymentProductRepository.class);
        payout = mock(PaymentProduct.class);
        bill = mock(PaymentProduct.class);
        when(products.findByProductCode("001")).thenReturn(Optional.of(payout));
        when(products.findByProductCode("002")).thenReturn(Optional.of(bill));
        service = new MenuRulesService(rules, config, products);
    }

    private static RoutePlan billPlan(boolean has, String... vendorNames) {
        List<RoutePlanVendor> vendors = new java.util.ArrayList<>();
        int p = 1;
        for (String n : vendorNames) {
            vendors.add(new RoutePlanVendor((long) p, n, p, null, null));
            p++;
        }
        return new RoutePlan(has, has ? 9L : null, "Bill Payment - Default", 3, 3, vendors, List.of());
    }

    // ---------------------------------------------------------------- the switch

    @Test
    void startsOff_soEveryAccountSeesEverything_andNothingIsRead() {
        assertEquals(MenuRulesSwitch.Mode.OFF, rules.mode());
        assertFalse(rules.applies(MERCHANT));
        assertEquals(EVERYTHING, service.myMenu(MERCHANT));
        verifyNoInteractions(config, products);
    }

    @Test
    void onlyThePilotAccountsAreCovered_andEveryoneWhenOn() {
        rules.save("PILOT", List.of(new TargetRef("MERCHANT", 2L)));

        assertTrue(rules.applies(MERCHANT));
        assertFalse(rules.applies(CustomerRef.merchant(3L)));      // not on the list
        assertFalse(rules.applies(FRANCHISE));

        rules.save("ON", List.of());
        assertTrue(rules.applies(CustomerRef.merchant(3L)));
        assertTrue(rules.applies(FRANCHISE));

        rules.save("OFF", List.of());
        assertFalse(rules.applies(MERCHANT));
    }

    @Test
    void theSwitchRefusesNonsense_andReadsBackItsValue() {
        assertThrows(IllegalArgumentException.class, () -> rules.save("MAYBE", List.of()));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> rules.save("PILOT", List.of()))
                .getMessage().contains("at least one account"));
        assertThrows(IllegalArgumentException.class,
                () -> rules.save("PILOT", List.of(new TargetRef("MERCHANT", 999L))));
        assertThrows(IllegalArgumentException.class,
                () -> rules.save("PILOT", List.of(new TargetRef("SHOP", 2L))));
        assertEquals(MenuRulesSwitch.Mode.OFF, rules.mode());                      // nothing changed

        when(settings.findById("menu.rules-mode"))
                .thenReturn(Optional.of(new PaymentRoutingSetting("menu.rules-mode", "pilot", "x")));
        when(settings.findById("menu.rules-accounts"))
                .thenReturn(Optional.of(new PaymentRoutingSetting("menu.rules-accounts", "MERCHANT:2, junk", "x")));
        rules.refresh();
        assertEquals(MenuRulesSwitch.Mode.PILOT, rules.mode());
        assertTrue(rules.applies(MERCHANT));

        assertEquals(MenuRulesSwitch.Mode.OFF, MenuRulesSwitch.parseMode("banana"));   // unreadable = off
        when(settings.findById(anyString())).thenThrow(new RuntimeException("db down"));
        rules.refresh();
        assertEquals(MenuRulesSwitch.Mode.PILOT, rules.mode());                    // the last value is kept
    }

    @Test
    void statusListsTheChosenAccountsByName() {
        rules.save("PILOT", List.of(new TargetRef("MERCHANT", 2L), new TargetRef("FRANCHISE", 4L)));

        var status = rules.status();

        assertEquals("PILOT", status.mode());
        assertEquals(2, status.accounts().size());
        assertTrue(status.accounts().stream().anyMatch(a -> "EXPLOREMORE".equals(a.name())));
        assertTrue(status.accounts().stream().anyMatch(a -> "AAMBIKA".equals(a.name())));
    }

    // ---------------------------------------------------------------- what an account sees

    @Test
    void aMerchantWithBothRoutings_seesPayout_andTheScreenOfItsFirstVendor() {
        rules.save("ON", List.of());
        when(config.hasActiveRouting(payout, "MERCHANT", 2L)).thenReturn(true);
        when(config.routePlan(eq(bill), eq("MERCHANT"), eq(2L), any(), eq("Credit Card")))
                .thenReturn(billPlan(true, "Bill Avenue", "SuivrePay"));

        assertEquals(new MyMenu(true, true, true, false), service.myMenu(MERCHANT));     // Bill Avenue first

        when(config.routePlan(eq(bill), eq("MERCHANT"), eq(2L), any(), eq("Credit Card")))
                .thenReturn(billPlan(true, "SuivrePay", "Bill Avenue"));
        assertEquals(new MyMenu(true, true, false, true), service.myMenu(MERCHANT));     // SuivrePay first
    }

    @Test
    void withoutAPayoutRouting_thePayoutMenuIsHidden_butTheRestStays() {
        rules.save("ON", List.of());
        when(config.hasActiveRouting(payout, "MERCHANT", 2L)).thenReturn(false);
        when(config.routePlan(eq(bill), eq("MERCHANT"), eq(2L), any(), any()))
                .thenReturn(billPlan(true, "Bill Avenue"));

        assertEquals(new MyMenu(true, false, true, false), service.myMenu(MERCHANT));
    }

    @Test
    void withoutABillPaymentRouting_bothCreditCardScreensAreHidden() {
        rules.save("ON", List.of());
        when(config.hasActiveRouting(payout, "MERCHANT", 2L)).thenReturn(true);
        when(config.routePlan(any(), any(), any(), any(), any())).thenReturn(billPlan(false));

        assertEquals(new MyMenu(true, true, false, false), service.myMenu(MERCHANT));
    }

    @Test
    void aVendorItDoesNotRecogniseOrNobodyAllowed_keepsBothScreens() {
        rules.save("ON", List.of());
        when(config.hasActiveRouting(payout, "MERCHANT", 2L)).thenReturn(true);

        when(config.routePlan(any(), any(), any(), any(), any())).thenReturn(billPlan(true, "Some Other Bank"));
        assertEquals(new MyMenu(true, true, true, true), service.myMenu(MERCHANT));

        when(config.routePlan(any(), any(), any(), any(), any())).thenReturn(billPlan(true));
        assertEquals(new MyMenu(true, true, true, true), service.myMenu(MERCHANT));
    }

    @Test
    void aFranchise_neverGetsBillPay_andKeepsCreditCardUnlessOnlyBillAvenueIsAllowed() {
        rules.save("ON", List.of());
        when(config.hasActiveRouting(payout, "FRANCHISE", 4L)).thenReturn(true);

        when(config.routePlan(eq(bill), eq("FRANCHISE"), eq(4L), any(), any()))
                .thenReturn(billPlan(true, "Bill Avenue", "SuivrePay"));
        assertEquals(new MyMenu(true, true, false, true), service.myMenu(FRANCHISE));

        when(config.routePlan(eq(bill), eq("FRANCHISE"), eq(4L), any(), any()))
                .thenReturn(billPlan(true, "Bill Avenue"));
        assertEquals(new MyMenu(true, true, false, false), service.myMenu(FRANCHISE));

        when(config.routePlan(eq(bill), eq("FRANCHISE"), eq(4L), any(), any())).thenReturn(billPlan(false));
        assertEquals(new MyMenu(true, true, false, false), service.myMenu(FRANCHISE));
    }

    @Test
    void anAccountTheRuleDoesNotCover_seesEverything_evenWithoutRoutings() {
        rules.save("PILOT", List.of(new TargetRef("MERCHANT", 2L)));

        assertEquals(EVERYTHING, service.myMenu(CustomerRef.merchant(3L)));
        verifyNoInteractions(config);
    }

    @Test
    void anyErrorOrMissingProduct_neverHidesAMenu() {
        rules.save("ON", List.of());

        when(config.hasActiveRouting(any(), any(), any())).thenThrow(new RuntimeException("db down"));
        assertEquals(EVERYTHING, service.myMenu(MERCHANT));

        reset(config);
        when(products.findByProductCode("001")).thenReturn(Optional.empty());
        when(products.findByProductCode("002")).thenReturn(Optional.empty());
        assertEquals(new MyMenu(true, true, true, true), service.myMenu(MERCHANT));
    }
}
