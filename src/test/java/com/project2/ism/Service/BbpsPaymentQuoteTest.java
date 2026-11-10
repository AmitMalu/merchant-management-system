package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The amount shown before paying is the amount the payment debits: bill amount + service charge.
 */
class BbpsPaymentQuoteTest {

    private PaymentChargeService charges;
    private MerchantWalletRepository wallets;
    private BbpsPaymentService service;

    @BeforeEach
    void setUp() {
        charges = mock(PaymentChargeService.class);
        wallets = mock(MerchantWalletRepository.class);
        service = new BbpsPaymentService(mock(PaymentVendorRepository.class), mock(BillAvenueConfigRepository.class),
                mock(BillAvenueApiClient.class), mock(PaymentVendorCredentialsService.class), charges, wallets,
                mock(MerchantRepository.class), mock(MerchantTransDetRepository.class),
                mock(BbpsTransactionRepository.class), new ObjectMapper(),
                mock(com.project2.ism.Service.Monitoring.TransactionEventService.class),
                new BillAvenueAgentDevice("AGT", "m", "203.0.113.5", false));
    }

    @Test
    void quote_isBillAmountPlusServiceCharge_andTouchesNoWallet() {
        when(charges.calculateCharges(new BigDecimal("1000"), "BBPS", 5L)).thenReturn(new BigDecimal("12.50"));

        Map<String, Object> q = service.quote(5L, new BigDecimal("1000"), null);

        assertEquals(new BigDecimal("1000"), q.get("amount"));
        assertEquals(new BigDecimal("12.50"), q.get("charges"));
        assertEquals(new BigDecimal("0"), q.get("custConvFee"));
        assertEquals(new BigDecimal("1012.50"), q.get("totalDebit"));
        verifyNoInteractions(wallets);
    }

    @Test
    void quote_includesACustomerFee_andUsesTheSameFeeRulesAsThePayment() {
        when(charges.calculateCharges(any(), eq("BBPS"), eq(5L))).thenReturn(new BigDecimal("10"));

        assertEquals(new BigDecimal("1110"), service.quote(5L, new BigDecimal("1000"), "100").get("totalDebit"));
        assertThrows(IllegalArgumentException.class, () -> service.quote(5L, new BigDecimal("1000"), "-5"));
    }

    @Test
    void quote_saysSoWhenNoChargeIsConfigured_orTheAmountIsBad() {
        when(charges.calculateCharges(any(), eq("BBPS"), eq(5L)))
                .thenThrow(new IllegalArgumentException("Amount 300000 does not fall in any configured slab"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.quote(5L, new BigDecimal("300000"), null));
        assertTrue(ex.getMessage().contains("not configured"));

        assertThrows(IllegalArgumentException.class, () -> service.quote(5L, BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class, () -> service.quote(5L, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.quote(null, new BigDecimal("10"), null));
    }
}
