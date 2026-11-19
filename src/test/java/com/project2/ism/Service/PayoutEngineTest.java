package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.TargetRef;
import com.project2.ism.DTO.PayoutDTO.PayoutRequest;
import com.project2.ism.DTO.PayoutDTO.PayoutResult;
import com.project2.ism.DTO.PayoutDTO.SimplePayoutRequest;
import com.project2.ism.Model.Routing.PaymentRoutingSetting;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PaymentAttemptRepository;
import com.project2.ism.Repository.PaymentRoutingAuditRepository;
import com.project2.ism.Repository.PaymentRoutingSettingRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Which payout flow runs. With the switch on OLD (the default) the controller gets exactly what it got before;
 * the new flow is used only for pilot accounts, or for everyone when the switch is ON, and only when it is ready.
 */
class PayoutEngineTest {

    private static final Long VENDOR = 1L;

    private PaymentRoutingSettingRepository settings;
    private MerchantRepository merchants;
    private PayoutEngineSwitch engineSwitch;
    private PayoutService payoutService;
    private PayoutEngine engine;

    @BeforeEach
    void setUp() {
        settings = mock(PaymentRoutingSettingRepository.class);
        merchants = mock(MerchantRepository.class);
        engineSwitch = new PayoutEngineSwitch(settings, mock(PaymentRoutingAuditRepository.class), merchants,
                mock(FranchiseRepository.class), mock(PaymentAttemptRepository.class),
                mock(PayoutTransactionRepository.class), mock(PaymentVendorRepository.class));
        payoutService = mock(PayoutService.class);
        engine = new PayoutEngine(payoutService, engineSwitch);

        when(settings.findById(anyString())).thenReturn(Optional.empty());
        when(settings.save(any())).thenAnswer(i -> i.getArgument(0));
        Merchant aurum = mock(Merchant.class);
        when(aurum.getId()).thenReturn(20L);
        when(aurum.getBusinessName()).thenReturn("AURUM MULTI SERVICES");
        when(merchants.findAllById(any())).thenReturn(List.of(aurum));
        when(payoutService.v2Available(VENDOR)).thenReturn(true);
        when(payoutService.initiatePayout(any(), any())).thenReturn(PayoutResult.failed("OLD", "old flow"));
        when(payoutService.initiateSimplePayout(any(), any())).thenReturn(PayoutResult.failed("OLD", "old flow"));
        when(payoutService.initiatePayoutV2(any(), any())).thenReturn(PayoutResult.failed("V2", "new flow"));
    }

    private static PayoutRequest request(Long merchantId) {
        PayoutRequest r = new PayoutRequest();
        r.setInitiatorType("MERCHANT");
        r.setInitiatorId(merchantId);
        r.setAmount(new BigDecimal("100"));
        return r;
    }

    private static SimplePayoutRequest simple() {
        SimplePayoutRequest s = new SimplePayoutRequest();
        s.setPayoutBankId(9L);
        s.setAmount(new BigDecimal("100"));
        return s;
    }

    // ---------------------------------------------------------------- the default

    @Test
    void theSwitchStartsOnOld_andNothingUsesTheNewFlow() {
        assertEquals(PayoutEngineSwitch.Mode.OLD, engineSwitch.mode());
        assertFalse(engineSwitch.useV2("MERCHANT", 20L));

        assertEquals("OLD", engine.initiate(request(20L), VENDOR).getMerchantRefId());
        assertEquals("OLD", engine.initiateSimple(simple(), VENDOR).getMerchantRefId());

        verify(payoutService, never()).initiatePayoutV2(any(), any());
        verify(payoutService, never()).toPayoutRequest(any());
        verify(payoutService, never()).savedBankOwner(any());     // OLD does not even look at the account
    }

    @Test
    void anUnreadableSettingMeansTheOldFlow() {
        when(settings.findById("payout.engine-mode"))
                .thenReturn(Optional.of(new PaymentRoutingSetting("payout.engine-mode", "banana", "x")));

        engineSwitch.refresh();

        assertEquals(PayoutEngineSwitch.Mode.OLD, engineSwitch.mode());
    }

    // ---------------------------------------------------------------- the pilot

    @Test
    void pilot_sendsOnlyThePilotAccountToTheNewFlow() {
        engineSwitch.save("PILOT", List.of(new TargetRef("MERCHANT", 20L)));

        assertEquals("V2", engine.initiate(request(20L), VENDOR).getMerchantRefId());        // AURUM: new flow
        assertEquals("OLD", engine.initiate(request(21L), VENDOR).getMerchantRefId());       // anyone else: old flow
    }

    @Test
    void pilot_savedBankPayouts_followTheAccountThatOwnsTheBank() {
        engineSwitch.save("PILOT", List.of(new TargetRef("MERCHANT", 20L)));
        when(payoutService.savedBankOwner(9L)).thenReturn(new CustomerRef("MERCHANT", 20L));
        when(payoutService.toPayoutRequest(any())).thenReturn(request(20L));

        assertEquals("V2", engine.initiateSimple(simple(), VENDOR).getMerchantRefId());

        when(payoutService.savedBankOwner(9L)).thenReturn(new CustomerRef("MERCHANT", 21L));
        assertEquals("OLD", engine.initiateSimple(simple(), VENDOR).getMerchantRefId());
    }

    @Test
    void anUnknownBank_goesToTheOldFlow_whichReportsItInItsOwnWords() {
        engineSwitch.save("PILOT", List.of(new TargetRef("MERCHANT", 20L)));
        when(payoutService.savedBankOwner(9L)).thenReturn(null);

        assertEquals("OLD", engine.initiateSimple(simple(), VENDOR).getMerchantRefId());
        verify(payoutService, never()).initiatePayoutV2(any(), any());
    }

    @Test
    void aFailedConversionIsAnswerdLikeTheOldFlowDoes() {
        engineSwitch.save("PILOT", List.of(new TargetRef("MERCHANT", 20L)));
        when(payoutService.savedBankOwner(9L)).thenReturn(new CustomerRef("MERCHANT", 20L));
        when(payoutService.toPayoutRequest(any())).thenThrow(new IllegalArgumentException("Invalid state: XX"));

        PayoutResult result = engine.initiateSimple(simple(), VENDOR);

        assertEquals("FAILED", result.getStatus());
        assertEquals("Invalid state: XX", result.getMessage());
        verify(payoutService, never()).initiatePayoutV2(any(), any());
    }

    // ---------------------------------------------------------------- on for everyone, and the safety net

    @Test
    void on_everyonePaysThroughTheNewFlow() {
        engineSwitch.save("ON", List.of());

        assertEquals("V2", engine.initiate(request(20L), VENDOR).getMerchantRefId());
        assertEquals("V2", engine.initiate(request(21L), VENDOR).getMerchantRefId());
    }

    @Test
    void whenTheNewFlowIsNotReady_theOldFlowIsUsedEvenIfTheSwitchIsOn() {
        engineSwitch.save("ON", List.of());
        when(payoutService.v2Available(VENDOR)).thenReturn(false);

        assertEquals("OLD", engine.initiate(request(20L), VENDOR).getMerchantRefId());
        verify(payoutService, never()).initiatePayoutV2(any(), any());
    }

    // ---------------------------------------------------------------- saving the switch

    @Test
    void theSwitchRefusesNonsense() {
        assertThrows(IllegalArgumentException.class, () -> engineSwitch.save("MAYBE", List.of()));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> engineSwitch.save("PILOT", List.of()))
                .getMessage().contains("at least one pilot account"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> engineSwitch.save("PILOT", List.of(new TargetRef("MERCHANT", 999L))))
                .getMessage().contains("No account found"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> engineSwitch.save("PILOT", List.of(new TargetRef("SHOP", 20L))))
                .getMessage().contains("MERCHANT or FRANCHISE"));

        assertEquals(PayoutEngineSwitch.Mode.OLD, engineSwitch.mode());    // nothing changed
    }

    @Test
    void theStoredPilotListIsReadBack_andBadEntriesAreIgnored() {
        assertEquals(java.util.Set.of("MERCHANT:20", "FRANCHISE:3"),
                PayoutEngineSwitch.parsePilot(" merchant:20, FRANCHISE:3 , junk, MERCHANT:x, :5 "));
        assertEquals(PayoutEngineSwitch.Mode.PILOT, PayoutEngineSwitch.parseMode("pilot"));
        assertEquals(PayoutEngineSwitch.Mode.OLD, PayoutEngineSwitch.parseMode(null));
    }

    // ---------------------------------------------------------------- the attempts list

    @Test
    void anOpenAttemptOnASettledPayout_isShownWithThePayoutsResult() {
        com.project2.ism.Model.Payout.PaymentAttempt pending = new com.project2.ism.Model.Payout.PaymentAttempt();
        pending.setStatus("PENDING");
        com.project2.ism.Model.Payout.PayoutTransaction done = new com.project2.ism.Model.Payout.PayoutTransaction();

        done.setStatus(com.project2.ism.Model.Payout.PayoutTransaction.PayoutStatus.SUCCESS);
        assertEquals("SUCCESS", PayoutEngineSwitch.shownStatus(pending, done));
        done.setStatus(com.project2.ism.Model.Payout.PayoutTransaction.PayoutStatus.FAILED);
        assertEquals("FAILED", PayoutEngineSwitch.shownStatus(pending, done));
        done.setStatus(com.project2.ism.Model.Payout.PayoutTransaction.PayoutStatus.PENDING);
        assertEquals("PENDING", PayoutEngineSwitch.shownStatus(pending, done));      // still really pending
        assertEquals("PENDING", PayoutEngineSwitch.shownStatus(pending, null));

        com.project2.ism.Model.Payout.PaymentAttempt failedTry = new com.project2.ism.Model.Payout.PaymentAttempt();
        failedTry.setStatus("FAILED");
        done.setStatus(com.project2.ism.Model.Payout.PayoutTransaction.PayoutStatus.SUCCESS);
        assertEquals("FAILED", PayoutEngineSwitch.shownStatus(failedTry, done));     // a finished try is never changed
    }

    // ---------------------------------------------------------------- "use routing for payouts"

    @Test
    void routingForPayouts_startsOff_andIsOnlyChangedWhenTheAdminSaysSo() {
        assertFalse(engineSwitch.routingEnabled());
        assertFalse(engineSwitch.status().useRouting());

        engineSwitch.save("ON", List.of(), true);
        assertTrue(engineSwitch.routingEnabled());
        assertTrue(engineSwitch.status().useRouting());

        engineSwitch.save("ON", List.of());                  // saving the flow alone leaves it as it was
        assertTrue(engineSwitch.routingEnabled());

        engineSwitch.save("ON", List.of(), false);
        assertFalse(engineSwitch.routingEnabled());
    }

    @Test
    void routingForPayouts_unreadableMeansOff_andIsReadBackFromTheSetting() {
        assertFalse(PayoutEngineSwitch.parseFlag(null));
        assertFalse(PayoutEngineSwitch.parseFlag("banana"));
        assertTrue(PayoutEngineSwitch.parseFlag(" TRUE "));

        when(settings.findById("payout.routing-enabled"))
                .thenReturn(Optional.of(new PaymentRoutingSetting("payout.routing-enabled", "true", "x")));
        engineSwitch.refresh();
        assertTrue(engineSwitch.routingEnabled());
    }

    @Test
    void ifTheSwitchCannotBeRead_theLastValueIsKept() {
        engineSwitch.save("ON", List.of());
        when(settings.findById(anyString())).thenThrow(new RuntimeException("db down"));

        engineSwitch.refresh();

        assertEquals(PayoutEngineSwitch.Mode.ON, engineSwitch.mode());
    }
}
