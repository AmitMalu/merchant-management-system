package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.BillPaymentRequest;
import com.project2.ism.DTO.TransactionStatusRequest;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.Payment.PaymentVendor;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bill Avenue in production: no payment without a configured service charge, a sane customer fee,
 * and the status lookup only for the merchant's own transactions.
 */
class BbpsPaymentRulesTest {

    private BillAvenueApiClient client;
    private PaymentChargeService charges;
    private MerchantWalletRepository wallets;
    private BbpsTransactionRepository txns;
    private BbpsPaymentService service;
    private MerchantRepository merchantRepo;
    private MerchantTransDetRepository ledger;
    private PaymentVendorCredentialsService credentials;

    @BeforeEach
    void setUp() {
        PaymentVendorRepository vendors = mock(PaymentVendorRepository.class);
        client = mock(BillAvenueApiClient.class);
        charges = mock(PaymentChargeService.class);
        wallets = mock(MerchantWalletRepository.class);
        txns = mock(BbpsTransactionRepository.class);

        PaymentVendor vendor = new PaymentVendor();
        ReflectionTestUtils.setField(vendor, "id", 7L);
        when(vendors.findByVendorNameAndStatus("Bill Avenue", true)).thenReturn(Optional.of(vendor));

        credentials = mock(PaymentVendorCredentialsService.class);
        merchantRepo = mock(MerchantRepository.class);
        ledger = mock(MerchantTransDetRepository.class);
        service = new BbpsPaymentService(vendors, mock(BillAvenueConfigRepository.class), client,
                credentials, charges, wallets,
                merchantRepo, ledger, txns,
                new ObjectMapper(), mock(com.project2.ism.Service.Monitoring.TransactionEventService.class),
                new BillAvenueAgentDevice("INT", "m", "203.0.113.5", false));
        // these tests use one charge mode for every bill; the per-category rules are in BbpsPaymentQuoteTest
        ReflectionTestUtils.setField(service, "chargeModeCode", "BBPS");
        ReflectionTestUtils.setField(service, "chargeDefaultFallback", true);
    }

    private BillPaymentRequest paymentOf(String amount, String custConvFee) {
        BillPaymentRequest r = new BillPaymentRequest();
        r.setMerchantId(5L);
        r.setBillerId("HDFC00000NAT01");
        BillPaymentRequest.AmountInfo a = new BillPaymentRequest.AmountInfo();
        a.setAmount(new BigDecimal(amount));
        a.setCustConvFee(custConvFee);
        r.setAmountInfo(a);
        BillPaymentRequest.PaymentMethod m = new BillPaymentRequest.PaymentMethod();
        m.setPaymentMode("Cash");
        r.setPaymentMethod(m);
        BillPaymentRequest.CustomerInfo customer = new BillPaymentRequest.CustomerInfo();
        customer.setCustomerMobile("9876543210");
        customer.setRemitterName("Ramesh Kumar");
        r.setCustomerInfo(customer);
        return r;
    }

    // ---------------- service charge is required

    @Test
    void noServiceChargeConfigured_refusesThePayment_andNothingIsDebited() {
        when(charges.calculateCharges(any(), eq("BBPS"), eq(5L)))
                .thenThrow(new IllegalStateException("No active charges configured"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.doBillPayment(paymentOf("1000", "0")));

        assertTrue(ex.getMessage().contains("not configured"), ex.getMessage());
        verifyNoInteractions(wallets);          // not even locked
        verify(txns, never()).save(any());
        verifyNoInteractions(client);           // never reached the vendor
    }

    @Test
    void chargeRequirementCanBeSwitchedOff_forADeliberateZeroChargeLaunch() {
        ReflectionTestUtils.setField(service, "chargeRequired", false);
        when(charges.calculateCharges(any(), eq("BBPS"), eq(5L)))
                .thenThrow(new IllegalStateException("No active charges configured"));
        when(wallets.findByMerchantIdForUpdate(5L)).thenReturn(Optional.empty());

        // gets past the charge step and stops at the wallet (which this test leaves empty)
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.doBillPayment(paymentOf("1000", "0")));
        assertTrue(ex.getMessage().contains("wallet"), ex.getMessage());
    }

    // ---------------- customer convenience fee

    @Test
    void negativeCustomerFee_isRefusedBeforeAnythingElse() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.doBillPayment(paymentOf("1000", "-50")));
        assertTrue(ex.getMessage().contains("custConvFee"));
        verifyNoInteractions(wallets);
    }

    @Test
    void customerFeeThatIsNotANumber_isRefusedWithAClearMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.doBillPayment(paymentOf("1000", "abc")));
        assertTrue(ex.getMessage().contains("custConvFee"));
    }

    // ---------------- status: own transactions, by transaction id

    private BbpsTransaction txnOf(long merchantId, String txnRefId) {
        BbpsTransaction t = new BbpsTransaction();
        t.setMerchantId(merchantId);
        t.setTxnRefId(txnRefId);
        return t;
    }

    private TransactionStatusRequest status(String type, String value) {
        TransactionStatusRequest r = new TransactionStatusRequest();
        r.setTrackingType(type);
        r.setTrackingValue(value);
        return r;
    }

    @Test
    void status_ofAnotherMerchantsTransaction_looksLikeNoSuchTransaction_andNeverCallsTheVendor() throws Exception {
        when(txns.findByTxnRefId("CC01ABC")).thenReturn(Optional.of(txnOf(99L, "CC01ABC")));
        when(txns.findByTxnRefId("NOPE")).thenReturn(Optional.empty());

        IllegalArgumentException other = assertThrows(IllegalArgumentException.class,
                () -> service.fetchTransactionStatus(status("TRANS_REF_ID", "CC01ABC"), 5L));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> service.fetchTransactionStatus(status("TRANS_REF_ID", "NOPE"), 5L));

        assertEquals("No transaction found for that ID", other.getMessage());
        assertEquals(other.getMessage(), unknown.getMessage());   // same answer: no way to probe ids
        verifyNoInteractions(client);
    }

    @Test
    void status_bySomethingOtherThanTransactionId_isRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fetchTransactionStatus(status("MOBILE_NO", "9822090369"), 5L));
        assertThrows(IllegalArgumentException.class,
                () -> service.fetchTransactionStatus(status("REQUEST_ID", "X"), 5L));
        verifyNoInteractions(client);
    }

    @Test
    void status_ofOwnTransaction_asksBillAvenueByTransactionIdOnly() throws Exception {
        when(txns.findByTxnRefId("CC01ABC")).thenReturn(Optional.of(txnOf(5L, "CC01ABC")));
        when(client.call(eq(7L), eq(BillAvenueApiType.TRANSACTION_STATUS), anyString(), anyString()))
                .thenReturn("{\"responseCode\":\"000\"}");

        TransactionStatusRequest req = status(null, " CC01ABC ");
        req.setFromDate("2020-01-01");
        req.setToDate("2020-12-31");
        Object result = service.fetchTransactionStatus(req, 5L);

        assertNotNull(result);
        verify(client).call(eq(7L), eq(BillAvenueApiType.TRANSACTION_STATUS), anyString(),
                eq("{\"trackingType\":\"TRANS_REF_ID\",\"trackingValue\":\"CC01ABC\"}"));
    }

    @Test
    void status_withoutAnId_isRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fetchTransactionStatus(status("TRANS_REF_ID", " "), 5L));
        assertThrows(IllegalArgumentException.class,
                () -> service.fetchTransactionStatus(null, 5L));
    }

    // ---------------- the reason Bill Avenue gives is shown whatever shape it comes in

    @Test
    void vendorErrorReason_isFoundInEveryShapeBillAvenueUses() throws Exception {
        java.lang.reflect.Method m = BbpsPaymentService.class.getDeclaredMethod("extractErrorMessage",
                com.fasterxml.jackson.databind.JsonNode.class);
        m.setAccessible(true);
        ObjectMapper om = new ObjectMapper();

        assertEquals("[E1] Bad amount", m.invoke(service, om.readTree(
                "{\"responseCode\":\"001\",\"errorInfo\":{\"error\":[{\"errorCode\":\"E1\",\"errorMessage\":\"Bad amount\"}]}}")));
        assertEquals("[E2] Agent blocked", m.invoke(service, om.readTree(
                "{\"responseCode\":\"001\",\"errorInfo\":[{\"error\":{\"errorCode\":\"E2\",\"errorMessage\":\"Agent blocked\"}}]}")));
        assertEquals("[E3] Two | [E4] Reasons", m.invoke(service, om.readTree(
                "{\"responseCode\":\"001\",\"errorInfo\":{\"error\":[{\"errorCode\":\"E3\",\"errorMessage\":\"Two\"},"
                        + "{\"errorCode\":\"E4\",\"errorMessage\":\"Reasons\"}]}}")));
        assertEquals("Biller error (code 001)", m.invoke(service, om.readTree("{\"responseCode\":\"001\"}")));
    }

    // ---------------- the customer's payment mode is set by us (AGT channel: Cash), not by the website

    @Test
    void theModeSentToBillAvenue_isTheConfiguredOne_whateverTheWebsiteSent() throws Exception {
        com.project2.ism.Model.MerchantWallet wallet = new com.project2.ism.Model.MerchantWallet();
        wallet.setAvailableBalance(new BigDecimal("1000"));
        when(wallets.findByMerchantIdForUpdate(5L)).thenReturn(Optional.of(wallet));
        when(charges.calculateCharges(any(), eq("BBPS"), eq(5L))).thenReturn(new BigDecimal("1"));
        when(txns.save(any())).thenAnswer(i -> i.getArgument(0));
        when(merchantRepo.findById(5L)).thenReturn(Optional.of(new com.project2.ism.Model.Users.Merchant()));
        when(ledger.save(any())).thenAnswer(i -> i.getArgument(0));
        when(credentials.getBillAvenueCredentials(7L))
                .thenReturn(new com.project2.ism.DTO.BillAvenueCredentials("u", "AGENT01", "s", "k", "i"));
        when(client.call(eq(7L), eq(BillAvenueApiType.BILL_PAYMENT), any(), anyString()))
                .thenReturn("{\"responseCode\":\"001\",\"vErrorRootVO\":{\"error\":[{\"errorCode\":\"E077\",\"errorMessage\":\"bad mode\"}]}}");

        BillPaymentRequest request = paymentOf("100", "0");
        request.getPaymentMethod().setPaymentMode("WALLET");          // what the old website sent
        request.setRequestId("FETCHREQ0123456789ABCDEFGHIJKLMNO");     // the id used for the bill fetch

        var result = service.doBillPayment(request);

        org.mockito.ArgumentCaptor<String> sent = org.mockito.ArgumentCaptor.forClass(String.class);
        // the payment goes to Bill Avenue under the SAME request id as the fetch
        verify(client).call(eq(7L), eq(BillAvenueApiType.BILL_PAYMENT), eq("FETCHREQ0123456789ABCDEFGHIJKLMNO"), sent.capture());
        assertTrue(sent.getValue().contains("\"paymentMode\":\"Cash\""), sent.getValue());
        assertFalse(sent.getValue().contains("WALLET"));
        // Remitter info in the shape of Bill Avenue's UAT samples (AGT channel, Cash)
        assertTrue(sent.getValue().matches("(?s).*\"paymentRefId\":\"[A-Za-z0-9]{10,27}\".*"), sent.getValue());
        assertTrue(sent.getValue().contains("\"infoName\":\"Payment Account Info\",\"infoValue\":\"Cash\""),
                sent.getValue());
        assertFalse(sent.getValue().contains("PaymentRefId\","), sent.getValue());   // not an entry in the list
        assertTrue(result.getMessage().contains("[E077] bad mode"), result.getMessage());
    }

    // ---------------- the payer's name (REMITTER_NAME) is mandatory

    @Test
    void thePayersName_isRequired_andRefusedBeforeAnyMoneyMoves() {
        BillPaymentRequest noName = paymentOf("100", "0");
        noName.getCustomerInfo().setRemitterName(null);
        BillPaymentRequest tooShort = paymentOf("100", "0");
        tooShort.getCustomerInfo().setRemitterName("A");
        BillPaymentRequest digits = paymentOf("100", "0");
        digits.getCustomerInfo().setRemitterName("12345");
        BillPaymentRequest noCustomer = paymentOf("100", "0");
        noCustomer.setCustomerInfo(null);

        for (BillPaymentRequest r : java.util.List.of(noName, tooShort, digits, noCustomer)) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.doBillPayment(r));
            assertTrue(ex.getMessage().contains("payer's name"), ex.getMessage());
        }
        verifyNoInteractions(wallets);
        verifyNoInteractions(client);
    }

    @Test
    void thePayersName_goesToBillAvenueAsREMITTER_NAME() throws Exception {
        com.project2.ism.Model.MerchantWallet wallet = new com.project2.ism.Model.MerchantWallet();
        wallet.setAvailableBalance(new BigDecimal("1000"));
        when(wallets.findByMerchantIdForUpdate(5L)).thenReturn(Optional.of(wallet));
        when(charges.calculateCharges(any(), eq("BBPS"), eq(5L))).thenReturn(new BigDecimal("1"));
        when(txns.save(any())).thenAnswer(i -> i.getArgument(0));
        when(merchantRepo.findById(5L)).thenReturn(Optional.of(new com.project2.ism.Model.Users.Merchant()));
        when(ledger.save(any())).thenAnswer(i -> i.getArgument(0));
        when(credentials.getBillAvenueCredentials(7L))
                .thenReturn(new com.project2.ism.DTO.BillAvenueCredentials("u", "AGENT01", "s", "k", "i"));
        when(client.call(eq(7L), eq(BillAvenueApiType.BILL_PAYMENT), any(), anyString()))
                .thenReturn("{\"responseCode\":\"001\"}");

        BillPaymentRequest request = paymentOf("100", "0");
        request.getCustomerInfo().setRemitterName("  Ramesh   Kumar ");
        service.doBillPayment(request);

        org.mockito.ArgumentCaptor<String> sent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client).call(eq(7L), eq(BillAvenueApiType.BILL_PAYMENT), any(), sent.capture());
        assertTrue(sent.getValue().contains("\"REMITTER_NAME\":\"Ramesh Kumar\""), sent.getValue());
        assertFalse(sent.getValue().contains("remitterName"), sent.getValue());
    }

    // ---------------- cash payments of Rs.50,000 or more need the customer's PAN

    @Test
    void largeCashPayment_withoutAValidPan_isRefused_beforeAnyMoneyMoves() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.doBillPayment(paymentOf("50000", "0")));
        assertTrue(ex.getMessage().contains("PAN"), ex.getMessage());

        BillPaymentRequest badPan = paymentOf("75000", "0");
        BillPaymentRequest.CustomerInfo info = new BillPaymentRequest.CustomerInfo();
        info.setCustomerPan("12345");
        badPan.setCustomerInfo(info);
        assertThrows(IllegalArgumentException.class, () -> service.doBillPayment(badPan));

        verifyNoInteractions(wallets);
        verifyNoInteractions(client);
    }

    @Test
    void smallCashPayment_doesNotNeedAPan() {
        when(charges.calculateCharges(any(), eq("BBPS"), eq(5L))).thenThrow(new IllegalStateException("none"));
        // 49,999 gets past the PAN rule and stops at the charge check instead
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.doBillPayment(paymentOf("49999", "0")));
        assertTrue(ex.getMessage().contains("not configured"));
    }

    @Test
    void aRequestIdThatWasAlreadyUsed_isRefused_beforeAnyMoneyMoves() {
        when(txns.findByRequestId("USEDREQ")).thenReturn(Optional.of(new BbpsTransaction()));
        BillPaymentRequest request = paymentOf("100", "0");
        request.setRequestId("USEDREQ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.doBillPayment(request));

        assertTrue(ex.getMessage().contains("fetch the bill again"), ex.getMessage());
        verifyNoInteractions(wallets);
        verifyNoInteractions(client);
    }
}
