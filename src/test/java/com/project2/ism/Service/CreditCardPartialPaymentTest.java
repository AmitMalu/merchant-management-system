package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.FetchBillRequest;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.FetchedBill;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.PayBillRequest;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.PaymentResult;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.Quote;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.QuoteRequest;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.TransactionView;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction.Status;
import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.FranchiseWallet;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.MerchantWallet;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.CreditCardBillTransactionRepository;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import com.project2.ism.Repository.FranchiseWalletRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.Repository.MerchantWalletRepository;
import com.project2.ism.Service.Monitoring.TransactionEventService;
import com.project2.ism.Service.SuivrePayClient.VendorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Paying only a part of a credit card bill (for example 1000 of a 5000 bill).
 * The whole feature sits behind suivrepay.partial-payment-enabled, which is ON by default (SuivrePay
 * has confirmed a lower amount is accepted) and can be switched off with that setting.
 */
class CreditCardPartialPaymentTest {

    private static final Long MERCHANT = 7L;
    private static final CustomerRef WHO = CustomerRef.merchant(MERCHANT);
    private static final Long FRANCHISE_ID = 5L;
    private static final CustomerRef FRANCHISE = CustomerRef.franchise(FRANCHISE_ID);
    private static final String REQ = "SP20250115BP1234567890";

    private static final String PAY_OK = """
            {"status":"success","response_code":"000","message":"Credit Card Bill Payment Successful",
             "data":{"txn_id":"TXN1","wallet_txn_id":"SP999","api_ref_number":"APPRF1"}}""";

    private final ObjectMapper mapper = new ObjectMapper();

    private SuivrePayClient client;
    private CreditCardBillTransactionRepository repo;
    private PaymentChargeService chargeService;
    private MerchantTransDetRepository ledgerRepo;
    private CreditCardBillService service;

    private MerchantWallet wallet;
    private CreditCardBillTransaction bill;

    @BeforeEach
    void setUp() {
        client = mock(SuivrePayClient.class);
        repo = mock(CreditCardBillTransactionRepository.class);
        MerchantWalletRepository walletRepo = mock(MerchantWalletRepository.class);
        MerchantRepository merchantRepo = mock(MerchantRepository.class);
        ledgerRepo = mock(MerchantTransDetRepository.class);
        chargeService = mock(PaymentChargeService.class);

        service = new CreditCardBillService(client, repo, walletRepo, merchantRepo, ledgerRepo,
                chargeService, mock(TransactionEventService.class), mock(PlatformTransactionManager.class),
                mock(FranchiseWalletRepository.class), mock(FranchiseRepository.class),
                mock(FranchiseTransDetRepository.class), mock(FeatureAccessService.class));
        configure(service);

        wallet = new MerchantWallet();
        wallet.setAvailableBalance(new BigDecimal("10000.00"));
        when(walletRepo.findByMerchantIdForUpdate(MERCHANT)).thenReturn(Optional.of(wallet));
        when(merchantRepo.findById(MERCHANT)).thenReturn(Optional.of(new Merchant()));
        when(ledgerRepo.save(any(MerchantTransactionDetails.class))).thenAnswer(i -> i.getArgument(0));
        when(ledgerRepo.findById(any())).thenReturn(Optional.empty());

        // the charge depends on the amount paid, like a slab: 1% of it
        when(chargeService.calculateCharges(any(), anyString(), anyLong()))
                .thenAnswer(i -> ((BigDecimal) i.getArgument(0)).movePointLeft(2)
                        .setScale(2, java.math.RoundingMode.HALF_UP));

        when(client.mid()).thenReturn("AP503682");

        bill = newBill("MERCHANT", MERCHANT, "5000.00");
        when(repo.findByRequestIdForUpdate(REQ, "MERCHANT", MERCHANT)).thenReturn(Optional.of(bill));
        when(repo.findOwnedByRequestId(REQ, "MERCHANT", MERCHANT)).thenReturn(Optional.of(bill));
        when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(repo.save(any(CreditCardBillTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static void configure(CreditCardBillService s) {
        ReflectionTestUtils.setField(s, "billersPath", "/billers");
        ReflectionTestUtils.setField(s, "fetchBillPath", "/fetch");
        ReflectionTestUtils.setField(s, "payBillPath", "/pay");
        ReflectionTestUtils.setField(s, "duplicateWindowSeconds", 120L);
        ReflectionTestUtils.setField(s, "fetchReuseSeconds", 60L);
        ReflectionTestUtils.setField(s, "chargeModeCodes", "CREDIT_CARD");
        ReflectionTestUtils.setField(s, "chargeRequired", true);
        ReflectionTestUtils.setField(s, "partialMinAmount", new BigDecimal("100"));
    }

    private static CreditCardBillTransaction newBill(String type, Long customerId, String amount) {
        CreditCardBillTransaction b = new CreditCardBillTransaction();
        b.setId(1L);
        b.setCustomerType(type);
        b.setMerchantId(customerId);
        b.setRequestId(REQ);
        b.setBillerId("IDFC00000NATFQ");
        b.setBillerName("IDFC First Bank Credit Card");
        b.setCardLast4("3044");
        b.setCustomerMobile("9250907790");
        b.setBillAmount(new BigDecimal(amount));
        b.setStatus(Status.FETCHED);
        b.setFetchedAt(LocalDateTime.now());
        return b;
    }

    private void partialOn() {
        ReflectionTestUtils.setField(service, "partialPaymentEnabled", true);
    }

    private void partialOff() {
        ReflectionTestUtils.setField(service, "partialPaymentEnabled", false);
    }

    @Test
    void partialPaymentIsOnByDefault_andCanBeSwitchedOff() {
        // no setting at all: a fresh service allows a part payment
        assertEquals(new BigDecimal("1000.00"), service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("1000"))).amount());

        partialOff();
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("1000"))));
    }

    private VendorResponse resp(int http, String json) throws Exception {
        return new VendorResponse(http, mapper.readTree(json), json);
    }

    private PaymentResult pay(String amount) {
        return service.payBill(WHO, new PayBillRequest(REQ, null, amount == null ? null : new BigDecimal(amount)));
    }

    private void assertNothingHappened() {
        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
        assertEquals(Status.FETCHED, bill.getStatus());
        verify(client, never()).post(eq("/pay"), any(), any());
        verify(ledgerRepo, never()).save(any());
    }

    // ------------------------------------------------------------------ the switch

    @Test
    void switchOff_aPartAmountIsRefused_andNothingIsDebited() {
        partialOff();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> pay("1000"));

        assertTrue(e.getMessage().contains("not available"), e.getMessage());
        assertNothingHappened();
    }

    @Test
    void switchOff_theWholeBillStillWorks_asBefore() throws Exception {
        partialOff();
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        PaymentResult r = pay(null);

        assertEquals("SUCCESS", r.status());
        assertEquals(new BigDecimal("5000.00"), r.billAmount());
        assertEquals(new BigDecimal("4950.00"), wallet.getAvailableBalance());   // 10000 - (5000 + 50)
        verify(client).post(eq("/pay"), argThat(b -> new BigDecimal("5000.00").equals(((Map<?, ?>) b).get("amount"))), any());
    }

    @Test
    void asking_forTheWholeBillAmount_worksEvenWhenTheSwitchIsOff() throws Exception {
        partialOff();
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        PaymentResult r = pay("5000");        // the same as the bill, typed by hand

        assertEquals("SUCCESS", r.status());
        assertEquals(new BigDecimal("5000.00"), r.billAmount());
    }

    // ------------------------------------------------------------------ paying a part

    @Test
    void part_payment_sendsOnlyTheChosenAmount_andDeductsThatPlusTheCharge() throws Exception {
        partialOn();
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        PaymentResult r = pay("1000");

        assertEquals("SUCCESS", r.status());
        assertEquals(new BigDecimal("1000.00"), r.billAmount());
        assertEquals(new BigDecimal("10.00"), r.charges());                       // 1% of what is paid, not of 5000
        assertEquals(new BigDecimal("1010.00"), r.totalDeducted());
        assertEquals(new BigDecimal("8990.00"), wallet.getAvailableBalance());    // 10000 - 1010

        // SuivrePay is asked for the chosen amount, not the bill
        verify(client).post(eq("/pay"), argThat(b -> {
            Map<?, ?> m = (Map<?, ?>) b;
            return new BigDecimal("1000.00").equals(m.get("amount")) && REQ.equals(m.get("request_id"));
        }), any());

        // the bill keeps what was due; the row records what was paid
        assertEquals(new BigDecimal("5000.00"), bill.getBillAmount());
        assertEquals(new BigDecimal("1000.00"), bill.getPaidAmount());
        assertEquals(Status.SUCCESS, bill.getStatus());
    }

    @Test
    void part_payment_isBookedInTheLedgerForTheAmountPaid() throws Exception {
        partialOn();
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        pay("1000");

        ArgumentCaptor<MerchantTransactionDetails> row = ArgumentCaptor.forClass(MerchantTransactionDetails.class);
        verify(ledgerRepo).save(row.capture());
        assertEquals(new BigDecimal("-1010.00"), row.getValue().getAmount());     // what left the wallet
        assertEquals(new BigDecimal("-1000.00"), row.getValue().getNetAmount());  // the part of the bill paid
        assertEquals(new BigDecimal("10.00"), row.getValue().getCharge());
        assertEquals("CREDIT_CARD", row.getValue().getService());
    }

    @Test
    void part_payment_thatFails_refundsTheChosenAmountPlusCharge() throws Exception {
        partialOn();
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(400,
                "{\"status\":\"error\",\"message\":\"Invalid Amount (must match the bill amount)\"}"));

        PaymentResult r = pay("1000");

        assertEquals("FAILED", r.status());
        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());   // 1010 deducted, 1010 back
        assertEquals(Status.FAILED, bill.getStatus());
    }

    @Test
    void part_payment_whoseOutcomeIsUnknown_keepsTheMoneyDeducted_andPending() throws Exception {
        partialOn();
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, "{\"status\":\"weird\"}"));

        PaymentResult r = pay("1000");

        assertEquals("PENDING", r.status());
        assertEquals(new BigDecimal("8990.00"), wallet.getAvailableBalance());
    }

    @Test
    void amountsWithOneDecimal_orTrailingZeros_areAccepted() throws Exception {
        partialOn();
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        assertEquals(new BigDecimal("1000.50"), pay("1000.5").billAmount());
    }

    // ------------------------------------------------------------------ amounts that are refused

    @Test
    void amountAboveTheBill_isRefused() {
        partialOn();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> pay("5000.01"));

        assertTrue(e.getMessage().contains("cannot be more"), e.getMessage());
        assertNothingHappened();
    }

    @Test
    void zeroNegativeAndTooManyDecimals_areRefused() {
        partialOn();

        for (String bad : new String[]{"0", "0.00", "-5", "100.555", "1000.001"}) {
            assertThrows(IllegalArgumentException.class, () -> pay(bad), bad);
        }
        assertNothingHappened();
    }

    @Test
    void belowTheMinimum_isRefused_andTheMinimumItselfIsAccepted() throws Exception {
        partialOn();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> pay("99.99"));
        assertTrue(e.getMessage().contains("minimum"), e.getMessage());
        assertNothingHappened();

        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));
        assertEquals("SUCCESS", pay("100").status());
    }

    @Test
    void panRule_usesTheAmountPaid_notTheBill() throws Exception {
        partialOn();
        wallet.setAvailableBalance(new BigDecimal("200000.00"));
        bill.setBillAmount(new BigDecimal("60000.00"));
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        // 10,000 of a 60,000 bill: under the 50,000 limit, so no PAN is needed
        assertEquals("SUCCESS", pay("10000").status());

        // paying 55,000 of it does need a PAN
        bill.setStatus(Status.FETCHED);
        bill.setPaidAmount(null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> pay("55000"));
        assertTrue(e.getMessage().contains("PAN"), e.getMessage());
    }

    @Test
    void aBillThatIsAlreadyProcessed_cannotBePaidAgain_withAnAmount() {
        partialOn();
        bill.setStatus(Status.SUCCESS);

        assertThrows(IllegalArgumentException.class, () -> pay("1000"));
        verify(client, never()).post(eq("/pay"), any(), any());
    }

    // ------------------------------------------------------------------ franchise

    @Test
    void aFranchise_canPayAPart_fromItsOwnWalletAndLedger() throws Exception {
        FranchiseWalletRepository fWalletRepo = mock(FranchiseWalletRepository.class);
        FranchiseTransDetRepository fLedger = mock(FranchiseTransDetRepository.class);
        FranchiseRepository fRepo = mock(FranchiseRepository.class);
        CreditCardBillService fService = new CreditCardBillService(client, repo, mock(MerchantWalletRepository.class),
                mock(MerchantRepository.class), mock(MerchantTransDetRepository.class), chargeService,
                mock(TransactionEventService.class), mock(PlatformTransactionManager.class),
                fWalletRepo, fRepo, fLedger, mock(FeatureAccessService.class));
        configure(fService);
        ReflectionTestUtils.setField(fService, "partialPaymentEnabled", true);

        FranchiseWallet fWallet = new FranchiseWallet();
        fWallet.setAvailableBalance(new BigDecimal("10000.00"));
        when(fWalletRepo.findByFranchiseIdForUpdate(FRANCHISE_ID)).thenReturn(Optional.of(fWallet));
        when(fRepo.findById(FRANCHISE_ID)).thenReturn(Optional.of(new Franchise()));
        when(fLedger.save(any(FranchiseTransactionDetails.class))).thenAnswer(i -> i.getArgument(0));
        when(fLedger.findById(any())).thenReturn(Optional.empty());
        when(chargeService.calculateFranchiseCharges(any(), anyString(), eq(FRANCHISE_ID)))
                .thenReturn(new BigDecimal("7.00"));
        CreditCardBillTransaction fBill = newBill("FRANCHISE", FRANCHISE_ID, "5000.00");
        when(repo.findByRequestIdForUpdate(REQ, "FRANCHISE", FRANCHISE_ID)).thenReturn(Optional.of(fBill));
        when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(fBill));
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        PaymentResult r = fService.payBill(FRANCHISE, new PayBillRequest(REQ, null, new BigDecimal("1000")));

        assertEquals("SUCCESS", r.status());
        assertEquals(new BigDecimal("8993.00"), fWallet.getAvailableBalance());   // 10000 - (1000 + 7)
        ArgumentCaptor<FranchiseTransactionDetails> row = ArgumentCaptor.forClass(FranchiseTransactionDetails.class);
        verify(fLedger).save(row.capture());
        assertEquals(new BigDecimal("-1007.00"), row.getValue().getAmount());
        assertEquals(new BigDecimal("-1000.00"), row.getValue().getNetAmount());
        assertEquals(new BigDecimal("1000.00"), fBill.getPaidAmount());
    }

    // ------------------------------------------------------------------ quote (what it would cost)

    @Test
    void quote_forAPart_showsTheChargeAndTotal_withoutChangingAnything() {
        partialOn();

        Quote q = service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("1000")));

        assertEquals(new BigDecimal("1000.00"), q.amount());
        assertEquals(new BigDecimal("10.00"), q.charges());
        assertEquals(new BigDecimal("1010.00"), q.totalPayable());
        assertFalse(q.panRequired());
        assertNothingHappened();
        verify(repo, never()).save(any());
    }

    @Test
    void quote_withoutAnAmount_isTheWholeBill() {
        Quote q = service.quote(WHO, new QuoteRequest(REQ, null));

        assertEquals(new BigDecimal("5000.00"), q.amount());
        assertEquals(new BigDecimal("50.00"), q.charges());
        assertEquals(new BigDecimal("5050.00"), q.totalPayable());
    }

    @Test
    void quote_appliesTheSameRulesAsPaying() {
        partialOff();
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("1000"))));

        partialOn();
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("5000.01"))));
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("99"))));
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("0"))));
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("100.555"))));
    }

    @Test
    void quote_isRefusedForAnUnknownExpiredOrProcessedBill() {
        partialOn();

        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest("SPNOPE", new BigDecimal("1000"))));
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest(" ", new BigDecimal("1000"))));
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, null));

        bill.setFetchedAt(LocalDateTime.now().minusHours(2));
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("1000"))));

        bill.setFetchedAt(LocalDateTime.now());
        bill.setStatus(Status.SUCCESS);
        assertThrows(IllegalArgumentException.class, () -> service.quote(WHO, new QuoteRequest(REQ, new BigDecimal("1000"))));
    }

    @Test
    void quote_neverShowsAnotherAccountsBill() {
        partialOn();
        // the repository is asked for this account's bill only; someone else's request id finds nothing
        when(repo.findOwnedByRequestId(REQ, "MERCHANT", 99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.quote(CustomerRef.merchant(99L), new QuoteRequest(REQ, new BigDecimal("1000"))));
    }

    // ------------------------------------------------------------------ what the screen is told

    private FetchedBill fetch(String amount) throws Exception {
        when(client.post(eq("/fetch"), any(), any())).thenReturn(resp(200,
                "{\"status\":\"success\",\"response_code\":\"000\",\"data\":{\"request_id\":\"SPNEW1\","
                        + "\"biller_id\":\"IDFC00000NATFQ\",\"biller_name\":\"IDFC\",\"amount\":" + amount + "}}"));
        return service.fetchBill(WHO, new FetchBillRequest("IDFC00000NATFQ", "3044", "9250907790"));
    }

    @Test
    void fetchedBill_saysWhetherAPartCanBePaid() throws Exception {
        // switch off: no
        partialOff();
        FetchedBill off = fetch("5000.00");
        assertFalse(off.partialAllowed());
        assertEquals(new BigDecimal("100"), off.minPartialAmount());

        // switch on and the bill is more than the minimum: yes
        partialOn();
        assertTrue(fetch("5000.00").partialAllowed());

        // switch on but the bill is no more than the minimum: nothing smaller to pay
        assertFalse(fetch("100.00").partialAllowed());
        assertFalse(fetch("80.00").partialAllowed());
    }

    // ------------------------------------------------------------------ history

    @Test
    void history_showsWhatWasPaid_andWhatWasDue() {
        CreditCardBillTransaction part = newBill("MERCHANT", MERCHANT, "5000.00");
        part.setStatus(Status.SUCCESS);
        part.setPaidAmount(new BigDecimal("1000.00"));
        CreditCardBillTransaction old = newBill("MERCHANT", MERCHANT, "800.00");   // before part-payment existed
        old.setId(2L);
        old.setStatus(Status.SUCCESS);
        when(repo.findHistory(eq("MERCHANT"), eq(MERCHANT), eq(Status.FETCHED), any())).thenReturn(List.of(part, old));

        List<TransactionView> views = service.getHistory(WHO);

        assertEquals(new BigDecimal("1000.00"), views.get(0).billAmount());
        assertEquals(new BigDecimal("5000.00"), views.get(0).billDueAmount());
        assertEquals(new BigDecimal("800.00"), views.get(1).billAmount());        // old row: it paid the whole bill
        assertEquals(new BigDecimal("800.00"), views.get(1).billDueAmount());
    }
}
