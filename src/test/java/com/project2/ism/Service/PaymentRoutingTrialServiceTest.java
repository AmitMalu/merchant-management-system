package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.Decision;
import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Routing.PaymentRoutingSetting;
import com.project2.ism.Model.Routing.PaymentRoutingTrial;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PaymentProductRepository;
import com.project2.ism.Repository.PaymentRoutingAuditRepository;
import com.project2.ism.Repository.PaymentRoutingSettingRepository;
import com.project2.ism.Repository.PaymentRoutingTrialRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The routing trial run: it records what routing WOULD choose next to the vendor really used, and it can never
 * affect a payment: nothing is thrown to it, and its own thread is the only one that touches the database.
 */
class PaymentRoutingTrialServiceTest {

    private PaymentRoutingConfigService config;
    private PaymentRoutingTrialRepository trials;
    private PaymentRoutingSettingRepository settings;
    private PaymentRoutingAuditRepository audits;
    private PaymentProductRepository products;
    private PaymentVendorRepository vendors;
    private MerchantRepository merchants;
    private FranchiseRepository franchises;
    private List<Runnable> queued;
    private PaymentRoutingTrialService service;

    private PaymentProduct payout;

    @BeforeEach
    void setUp() {
        config = mock(PaymentRoutingConfigService.class);
        trials = mock(PaymentRoutingTrialRepository.class);
        settings = mock(PaymentRoutingSettingRepository.class);
        audits = mock(PaymentRoutingAuditRepository.class);
        products = mock(PaymentProductRepository.class);
        vendors = mock(PaymentVendorRepository.class);
        merchants = mock(MerchantRepository.class);
        franchises = mock(FranchiseRepository.class);
        queued = new ArrayList<>();
        Executor executor = queued::add;          // holds the work, like a separate thread would
        service = new PaymentRoutingTrialService(executor, config, trials, settings, audits, products, vendors,
                merchants, franchises);

        payout = mock(PaymentProduct.class);
        when(payout.getId()).thenReturn(1L);
        when(payout.getProductName()).thenReturn("Payout");
        when(products.findByProductCode("001")).thenReturn(Optional.of(payout));
        when(products.findByProductCode("002")).thenReturn(Optional.of(payout));
        when(settings.findById(anyString())).thenReturn(Optional.empty());
        when(settings.save(any())).thenAnswer(i -> i.getArgument(0));
        when(audits.save(any())).thenAnswer(i -> i.getArgument(0));
        when(trials.save(any())).thenAnswer(i -> i.getArgument(0));

        PaymentVendor vimo = mock(PaymentVendor.class);
        when(vimo.getId()).thenReturn(1L);
        when(vimo.getVendorName()).thenReturn("Vimo Pay");
        when(vendors.findById(1L)).thenReturn(Optional.of(vimo));
    }

    private void runQueued() {
        queued.forEach(Runnable::run);
        queued.clear();
    }

    private PaymentRoutingTrial savedRow() {
        ArgumentCaptor<PaymentRoutingTrial> captor = ArgumentCaptor.forClass(PaymentRoutingTrial.class);
        verify(trials).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- off by default

    @Test
    void whenSwitchedOff_nothingIsQueuedOrRecorded() {
        service.recordPayout("MERCHANT", 5L, new BigDecimal("1000"), 1L, "MER1");

        assertTrue(queued.isEmpty());
        verifyNoInteractions(trials, config);
    }

    // ---------------------------------------------------------------- recording

    @Test
    void thePaymentThreadNeverTouchesTheDatabase_theWorkIsHandedOver() {
        service.setEnabled(true);
        clearInvocations(trials, settings, products, vendors, config, merchants, franchises);

        service.recordPayout("MERCHANT", 5L, new BigDecimal("1000"), 1L, "MER1");

        // on the payment's own thread: only the hand-over, no repository was used
        assertEquals(1, queued.size());
        verifyNoInteractions(trials, settings, products, vendors, config, merchants, franchises);
    }

    @Test
    void sameVendor_isRecordedAsAMatch() {
        service.setEnabled(true);
        when(config.decide(eq(payout), eq("MERCHANT"), eq(5L), any(), any()))
                .thenReturn(new Decision(10L, "Payout - Default", 1L, "Vimo Pay", false, "1. Vimo Pay: can serve"));

        service.recordPayout("MERCHANT", 5L, new BigDecimal("1000"), 1L, "MER1");
        runQueued();

        PaymentRoutingTrial row = savedRow();
        assertTrue(row.getMatched());
        assertFalse(row.getNoRouting());
        assertEquals("PAYOUT", row.getSource());
        assertEquals("Vimo Pay", row.getUsedVendorName());
        assertEquals("Vimo Pay", row.getChosenVendorName());
        assertEquals("MER1", row.getReference());
    }

    @Test
    void differentVendor_isRecordedAsADifference() {
        service.setEnabled(true);
        when(config.decide(any(), any(), anyLong(), any(), any()))
                .thenReturn(new Decision(10L, "Bill Payment - Default", 2L, "Bill Avenue", false,
                        "1. Bill Avenue: can serve; 2. SuivrePay: can serve"));
        PaymentVendor suivre = mock(PaymentVendor.class);
        when(suivre.getId()).thenReturn(3L);
        when(suivre.getVendorName()).thenReturn("SuivrePay");
        when(vendors.findByVendorNameAndStatus("SuivrePay", true)).thenReturn(Optional.of(suivre));

        service.recordSuivrePay("MERCHANT", 20L, new BigDecimal("50019"), "REQ1");
        runQueued();

        PaymentRoutingTrial row = savedRow();
        assertFalse(row.getMatched());                       // routing says Bill Avenue, the payment used SuivrePay
        assertEquals("SuivrePay", row.getUsedVendorName());
        assertEquals(3L, row.getUsedVendorId());
        assertEquals("Bill Avenue", row.getChosenVendorName());
        assertEquals("Credit Card", row.getCategory());
        assertEquals("SUIVREPAY", row.getSource());
    }

    @Test
    void anAccountWithNoRouting_isRecordedAsSuch() {
        service.setEnabled(true);
        when(config.decide(any(), any(), anyLong(), any(), any()))
                .thenReturn(new Decision(null, null, null, null, true, "No routing assigned to this account"));

        service.recordPayout("FRANCHISE", 3L, new BigDecimal("500"), 1L, "FRA1");
        runQueued();

        PaymentRoutingTrial row = savedRow();
        assertTrue(row.getNoRouting());
        assertFalse(row.getMatched());
    }

    // ---------------------------------------------------------------- it can never hurt a payment

    @Test
    void anErrorInsideTheWork_isSwallowed() {
        service.setEnabled(true);
        when(config.decide(any(), any(), anyLong(), any(), any())).thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> {
            service.recordPayout("MERCHANT", 5L, new BigDecimal("1000"), 1L, "MER1");
            runQueued();
        });
        verify(trials, never()).save(any());
    }

    @Test
    void aFullOrBrokenQueue_isSwallowed() {
        PaymentRoutingTrialService broken = new PaymentRoutingTrialService(r -> {
            throw new java.util.concurrent.RejectedExecutionException("queue full");
        }, config, trials, settings, audits, products, vendors, merchants, franchises);
        broken.setEnabled(true);

        assertDoesNotThrow(() -> broken.recordBillAvenue(5L, new BigDecimal("100"), "Credit Card", 2L, "REQ"));
    }

    // ---------------------------------------------------------------- the switch and the clean-up

    @Test
    void theSwitchIsReadFromTheDatabaseInTheBackground() {
        when(settings.findById("routing.trial-enabled"))
                .thenReturn(Optional.of(new PaymentRoutingSetting("routing.trial-enabled", "true", "admin")));
        assertFalse(service.isEnabled());

        service.refreshFlag();
        assertTrue(service.isEnabled());

        when(settings.findById("routing.trial-enabled"))
                .thenReturn(Optional.of(new PaymentRoutingSetting("routing.trial-enabled", "false", "admin")));
        service.refreshFlag();
        assertFalse(service.isEnabled());
    }

    @Test
    void ifTheSwitchCannotBeRead_theLastValueIsKept() {
        service.setEnabled(true);
        when(settings.findById(anyString())).thenThrow(new RuntimeException("db down"));

        service.refreshFlag();

        assertTrue(service.isEnabled());
    }

    @Test
    void rowsOlderThanThreeHoursAreDeleted() {
        LocalDateTime before = LocalDateTime.now();

        service.purge();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(trials).purgeOlderThan(cutoff.capture());
        assertTrue(cutoff.getValue().isAfter(before.minusHours(3).minusMinutes(1)), cutoff.getValue().toString());
        assertTrue(cutoff.getValue().isBefore(LocalDateTime.now().minusHours(3).plusMinutes(1)), cutoff.getValue().toString());
    }

    @Test
    void aFailingCleanUpDoesNotThrow() {
        when(trials.purgeOlderThan(any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.purge());
    }
}
