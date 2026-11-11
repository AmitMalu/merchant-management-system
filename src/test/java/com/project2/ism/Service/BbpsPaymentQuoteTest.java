package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Model.Bbps.BillAvenueConfig;
import com.project2.ism.Repository.BbpsTransactionRepository;
import com.project2.ism.Repository.BillAvenueConfigRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.Repository.MerchantWalletRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.util.BillAvenueAgentDevice;
import com.project2.ism.util.BillAvenueApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The amount shown before paying is the amount the payment debits: bill amount + the service charge of
 * the bill's own category (payment mode BBPS_<CATEGORY>).
 */
class BbpsPaymentQuoteTest {

    private static final String CARD = "HDFC00000NAT01";
    private static final String ELEC = "ELEC00000NAT01";

    private PaymentChargeService charges;
    private MerchantWalletRepository wallets;
    private BillAvenueConfigRepository billers;
    private BbpsPaymentService service;

    @BeforeEach
    void setUp() {
        charges = mock(PaymentChargeService.class);
        wallets = mock(MerchantWalletRepository.class);
        billers = mock(BillAvenueConfigRepository.class);
        service = new BbpsPaymentService(mock(PaymentVendorRepository.class), billers,
                mock(BillAvenueApiClient.class), mock(PaymentVendorCredentialsService.class), charges, wallets,
                mock(MerchantRepository.class), mock(MerchantTransDetRepository.class),
                mock(BbpsTransactionRepository.class), new ObjectMapper(),
                mock(com.project2.ism.Service.Monitoring.TransactionEventService.class),
                new BillAvenueAgentDevice("AGT", "m", "203.0.113.5", false));
        biller(CARD, "Credit Card");
        biller(ELEC, "Electricity");
        when(charges.paymentModeExists("BBPS_CREDIT_CARD")).thenReturn(true);
        when(charges.paymentModeExists("BBPS_ELECTRICITY")).thenReturn(true);
    }

    private void biller(String id, String category) {
        BillAvenueConfig c = new BillAvenueConfig();
        c.setProviderId(id);
        c.setServiceName(category);
        when(billers.findByProviderId(id)).thenReturn(Optional.of(c));
    }

    @Test
    void quote_isBillAmountPlusServiceCharge_andTouchesNoWallet() {
        when(charges.calculateCharges(new BigDecimal("1000"), "BBPS_CREDIT_CARD", 5L)).thenReturn(new BigDecimal("12.50"));

        Map<String, Object> q = service.quote(5L, new BigDecimal("1000"), null, CARD);

        assertEquals(new BigDecimal("1000"), q.get("amount"));
        assertEquals(new BigDecimal("12.50"), q.get("charges"));
        assertEquals(new BigDecimal("0"), q.get("custConvFee"));
        assertEquals(new BigDecimal("1012.50"), q.get("totalDebit"));
        assertEquals("BBPS_CREDIT_CARD", q.get("chargeMode"));
        verifyNoInteractions(wallets);
    }

    @Test
    void quote_includesACustomerFee_andUsesTheSameFeeRulesAsThePayment() {
        when(charges.calculateCharges(any(), eq("BBPS_CREDIT_CARD"), eq(5L))).thenReturn(new BigDecimal("10"));

        assertEquals(new BigDecimal("1110"), service.quote(5L, new BigDecimal("1000"), "100", CARD).get("totalDebit"));
        assertThrows(IllegalArgumentException.class, () -> service.quote(5L, new BigDecimal("1000"), "-5", CARD));
    }

    @Test
    void quote_saysSoWhenNoChargeIsConfigured_orTheAmountIsBad() {
        when(charges.calculateCharges(any(), eq("BBPS_CREDIT_CARD"), eq(5L)))
                .thenThrow(new IllegalArgumentException("Amount 300000 does not fall in any configured slab"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.quote(5L, new BigDecimal("300000"), null, CARD));
        assertTrue(ex.getMessage().contains("not configured"));

        assertThrows(IllegalArgumentException.class, () -> service.quote(5L, BigDecimal.ZERO, null, CARD));
        assertThrows(IllegalArgumentException.class, () -> service.quote(5L, null, null, CARD));
        assertThrows(IllegalArgumentException.class, () -> service.quote(null, new BigDecimal("10"), null, CARD));
    }

    @Test
    void eachCategoryUsesItsOwnChargeMode() {
        when(charges.calculateCharges(new BigDecimal("1000"), "BBPS_CREDIT_CARD", 5L)).thenReturn(new BigDecimal("25"));
        when(charges.calculateCharges(new BigDecimal("1000"), "BBPS_ELECTRICITY", 5L)).thenReturn(new BigDecimal("7"));

        Map<String, Object> elec = service.quote(5L, new BigDecimal("1000"), null, ELEC);
        Map<String, Object> card = service.quote(5L, new BigDecimal("1000"), null, CARD);

        assertEquals(new BigDecimal("7"), elec.get("charges"));
        assertEquals("BBPS_ELECTRICITY", elec.get("chargeMode"));
        assertEquals(new BigDecimal("1007"), elec.get("totalDebit"));
        assertEquals(new BigDecimal("25"), card.get("charges"));
    }

    @Test
    void aCategoryWithoutItsOwnMode_isRefused_notChargedWithAnotherCategorysSlab() {
        biller("WATR00000NAT01", "Water");   // no BBPS_WATER mode

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.quote(5L, new BigDecimal("1000"), null, "WATR00000NAT01"));
        assertTrue(ex.getMessage().contains("Water"));
        // an unknown biller / no biller is refused too
        assertThrows(IllegalStateException.class, () -> service.quote(5L, new BigDecimal("1000"), null));
        verify(charges, never()).calculateCharges(any(), any(), any());
    }

    @Test
    void withTheFallbackOn_aCategoryWithoutItsOwnMode_usesTheDefaultMode() {
        ReflectionTestUtils.setField(service, "chargeDefaultFallback", true);
        biller("WATR00000NAT01", "Water");
        when(charges.calculateCharges(new BigDecimal("1000"), "BBPS_CREDIT_CARD", 5L)).thenReturn(new BigDecimal("25"));

        Map<String, Object> q = service.quote(5L, new BigDecimal("1000"), null, "WATR00000NAT01");

        assertEquals("BBPS_CREDIT_CARD", q.get("chargeMode"));
        assertEquals(new BigDecimal("25"), q.get("charges"));
    }

    @Test
    void categoryNamesBecomeModeCodes_ignoringCaseSpacesAndSymbols() {
        when(charges.paymentModeExists("BBPS_GAS_LPG")).thenReturn(true);
        assertEquals("BBPS_GAS_LPG", service.chargeModeFor(" Gas - LPG "));
        assertEquals("BBPS_CREDIT_CARD", service.chargeModeFor("credit card"));
        assertNull(service.chargeModeFor(null));
    }
}
