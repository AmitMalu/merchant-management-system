package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.FetchBillRequest;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.FetchedBill;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.PayBillRequest;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.PaymentResult;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction.Status;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.MerchantWallet;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.CreditCardBillTransactionRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.Repository.MerchantWalletRepository;
import com.project2.ism.Service.Monitoring.TransactionEventService;
import com.project2.ism.Service.SuivrePayClient.VendorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Money-path checks for the credit card bill payment: what happens to the
 * merchant wallet for success, a definite vendor refusal, and "we don't know".
 */
class CreditCardBillServiceTest {

    private static final Long MERCHANT = 7L;
    private static final String REQ = "SP20250115BP1234567890";

    private final ObjectMapper mapper = new ObjectMapper();

    private SuivrePayClient client;
    private CreditCardBillTransactionRepository repo;
    private PaymentChargeService chargeService;
    private CreditCardBillService service;

    private MerchantWallet wallet;
    private CreditCardBillTransaction bill;

    @BeforeEach
    void setUp() {
        client = mock(SuivrePayClient.class);
        repo = mock(CreditCardBillTransactionRepository.class);
        MerchantWalletRepository walletRepo = mock(MerchantWalletRepository.class);
        MerchantRepository merchantRepo = mock(MerchantRepository.class);
        MerchantTransDetRepository ledgerRepo = mock(MerchantTransDetRepository.class);
        chargeService = mock(PaymentChargeService.class);
        TransactionEventService events = mock(TransactionEventService.class);

        service = new CreditCardBillService(client, repo, walletRepo, merchantRepo, ledgerRepo,
                chargeService, events, mock(PlatformTransactionManager.class));
        ReflectionTestUtils.setField(service, "billersPath", "/billers");
        ReflectionTestUtils.setField(service, "fetchBillPath", "/fetch");
        ReflectionTestUtils.setField(service, "payBillPath", "/pay");
        ReflectionTestUtils.setField(service, "raiseComplaintPath", "/complaint");
        ReflectionTestUtils.setField(service, "complaintStatusPath", "/complaint-status");
        ReflectionTestUtils.setField(service, "complaintServiceName", "BBPS");
        ReflectionTestUtils.setField(service, "duplicateWindowSeconds", 120L);
        ReflectionTestUtils.setField(service, "fetchReuseSeconds", 60L);

        wallet = new MerchantWallet();
        wallet.setAvailableBalance(new BigDecimal("10000.00"));
        when(walletRepo.findByMerchantIdForUpdate(MERCHANT)).thenReturn(Optional.of(wallet));
        when(merchantRepo.findById(MERCHANT)).thenReturn(Optional.of(new Merchant()));
        when(ledgerRepo.save(any(MerchantTransactionDetails.class))).thenAnswer(i -> i.getArgument(0));
        when(ledgerRepo.findById(any())).thenReturn(Optional.empty());

        when(chargeService.calculateCharges(any(), anyString(), anyLong())).thenReturn(new BigDecimal("5.00"));

        when(client.mid()).thenReturn("AP503682");

        bill = new CreditCardBillTransaction();
        bill.setId(1L);
        bill.setMerchantId(MERCHANT);
        bill.setRequestId(REQ);
        bill.setBillerId("IDFC00000NATFQ");
        bill.setBillerName("IDFC First Bank Credit Card");
        bill.setCardLast4("3044");
        bill.setCustomerMobile("9250907790");
        bill.setBillAmount(new BigDecimal("1000.00"));
        bill.setStatus(Status.FETCHED);
        bill.setFetchedAt(LocalDateTime.now());

        when(repo.findByRequestIdAndMerchantIdForUpdate(REQ, MERCHANT)).thenReturn(Optional.of(bill));
        when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(repo.save(any(CreditCardBillTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private VendorResponse resp(int http, String json) throws Exception {
        return new VendorResponse(http, mapper.readTree(json), json);
    }

    private static final String PAY_OK = """
            {"status":"success","response_code":"000","message":"Credit Card Bill Payment Successful",
             "data":{"txn_id":"TXN1","wallet_txn_id":"SP999","api_ref_number":"APPRF1"}}""";

    @Test
    void success_keepsMoneyDeducted_andMarksSuccess() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        PaymentResult r = service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        assertEquals("SUCCESS", r.status());
        assertEquals(new BigDecimal("8995.00"), wallet.getAvailableBalance()); // 10000 - (1000 + 5)
        assertEquals("SP999", bill.getWalletTxnId());
        assertEquals(Status.SUCCESS, bill.getStatus());
    }

    @Test
    void amountSentToVendor_comesFromStoredBill_notFromClient() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        verify(client).post(eq("/pay"), argThat(b -> {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) b;
            return new BigDecimal("1000.00").equals(m.get("amount")) && REQ.equals(m.get("request_id"))
                    && "AP503682".equals(m.get("MID"))
                    // card details are repeated from the stored bill, like SuivrePay's sample request
                    && m.get("input_params") instanceof java.util.List<?> l && l.size() == 2;
        }), argThat(ctx -> "PAY_BILL".equals(ctx.action()) && MERCHANT.equals(ctx.merchantId())
                && "IDFC00000NATFQ".equals(ctx.billerId()) && "3044".equals(ctx.cardLast4())
                && REQ.equals(ctx.requestId())));
    }

    @Test
    void definiteRefusal_refundsWallet_andHidesVendorInternals() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(402,
                "{\"status\":\"error\",\"message\":\"Insufficient Balance. Required: 5435.36 (Credit Wallet: 1.00)\"}"));

        PaymentResult r = service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        assertEquals("FAILED", r.status());
        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance()); // refunded in full
        assertFalse(r.message().contains("5435"), "vendor wallet details must not reach the merchant");
    }

    @Test
    void vendorSaysAlreadyPaid_refunds_andShowsVendorMessage() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(400,
                "{\"status\":\"error\",\"message\":\"This bill has already been paid.\"}"));

        PaymentResult r = service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        assertEquals("FAILED", r.status());
        assertEquals("This bill has already been paid.", r.message());
        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
    }

    @Test
    void timeout_keepsMoneyDeducted_andStaysPending_noRefund() throws Exception {
        when(client.post(eq("/pay"), any(), any()))
                .thenThrow(new SuivrePayClient.VendorUnreachableException("timeout", new RuntimeException()));

        PaymentResult r = service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        assertEquals("PENDING", r.status());
        assertEquals(Status.PENDING, bill.getStatus());
        assertEquals(new BigDecimal("8995.00"), wallet.getAvailableBalance()); // NOT refunded
    }

    @Test
    void serverError500_isTreatedAsUnknown_notRefunded() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(500,
                "{\"status\":\"error\",\"message\":\"API Connection Error: x\"}"));

        PaymentResult r = service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        assertEquals("PENDING", r.status());
        assertEquals(new BigDecimal("8995.00"), wallet.getAvailableBalance());
    }

    @Test
    void docs_http500_paymentFailedRefunded_isADefiniteFailure_andRefunds() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(500,
                "{\"status\":\"error\",\"message\":\"Payment failed. Rs.5435.36 refunded. API Connection Error: timeout\"}"));

        PaymentResult r = service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        assertEquals("FAILED", r.status());
        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
        assertTrue(r.message().contains("No amount has been deducted"), r.message());
        assertFalse(r.message().contains("5435"), "SuivrePay's amount/fee must not be shown: " + r.message());
    }

    @Test
    void docs_response001_paymentFailedRefunded_showsTheBillersReason() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200,
                "{\"status\":\"error\",\"response_code\":\"001\","
                        + "\"message\":\"Payment failed. Rs.5435.36 refunded. Customer account is blocked\"}"));

        PaymentResult r = service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        assertEquals("FAILED", r.status());
        assertTrue(r.message().contains("Customer account is blocked"), r.message());
        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
    }

    @Test
    void docs_plain500DatabaseError_isUnknown_andStaysPending() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(500,
                "{\"status\":\"error\",\"message\":\"Database connection failed.\"}"));

        PaymentResult r = service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        assertEquals("PENDING", r.status());
        assertEquals(new BigDecimal("8995.00"), wallet.getAvailableBalance());
    }

    @Test
    void docs_fetchBill_BFR001_isShownToTheMerchant_withoutTheInternalCode() throws Exception {
        when(client.post(eq("/fetch"), any(), any())).thenReturn(resp(200,
                "{\"status\":\"error\",\"response_code\":\"001\","
                        + "\"message\":\"BFR001 - Incorrect / invalid Customer account (Card details or mobile number is wrong)\"}"));

        com.project2.ism.Exception.CreditCardBillException ex = assertThrows(
                com.project2.ism.Exception.CreditCardBillException.class,
                () -> service.fetchBill(MERCHANT, new FetchBillRequest("IDFC00000NATFQ", "3044", "9250907790")));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().startsWith("Incorrect / invalid Customer account"), ex.getMessage());
        assertFalse(ex.getMessage().contains("BFR001"), ex.getMessage());
    }

    @Test
    void alreadyProcessedBill_isRejected_andWalletUntouched() {
        bill.setStatus(Status.PENDING);

        assertThrows(IllegalArgumentException.class,
                () -> service.payBill(MERCHANT, new PayBillRequest(REQ, null)));

        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
        verify(client, never()).post(eq("/pay"), any(), any());
    }

    @Test
    void sameAmountSameCardWithin120Seconds_isBlocked_beforeAnyMoneyMoves() {
        CreditCardBillTransaction earlier = new CreditCardBillTransaction();
        earlier.setId(99L);
        earlier.setStatus(Status.SUCCESS);
        earlier.setPaidAt(LocalDateTime.now().minusSeconds(30));
        when(repo.findRecentSimilarPayments(anyLong(), anyString(), anyString(), any(), any(), any(), any(), anyLong()))
                .thenReturn(java.util.List.of(earlier));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.payBill(MERCHANT, new PayBillRequest(REQ, null)));

        assertTrue(ex.getMessage().contains("wait"), ex.getMessage());
        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
        assertEquals(Status.FETCHED, bill.getStatus());
        verify(client, never()).post(eq("/pay"), any(), any());
    }

    @Test
    void duplicateCheck_looksOnlyAtPendingAndSuccess_withinTheWindow() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200, PAY_OK));

        service.payBill(MERCHANT, new PayBillRequest(REQ, null));

        verify(repo).findRecentSimilarPayments(eq(MERCHANT), eq("IDFC00000NATFQ"), eq("3044"), any(),
                eq(new BigDecimal("1000.00")),
                eq(java.util.List.of(Status.PENDING, Status.SUCCESS)),
                argThat(since -> since.isAfter(LocalDateTime.now().minusSeconds(125))
                        && since.isBefore(LocalDateTime.now().minusSeconds(115))),
                eq(1L));
    }

    @Test
    void expiredBill_isRejected() {
        bill.setFetchedAt(LocalDateTime.now().minusMinutes(31));

        assertThrows(IllegalArgumentException.class,
                () -> service.payBill(MERCHANT, new PayBillRequest(REQ, null)));

        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
    }

    @Test
    void unknownRequestId_orOtherMerchantsBill_isRejected() {
        when(repo.findByRequestIdAndMerchantIdForUpdate("SPOTHER", MERCHANT)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.payBill(MERCHANT, new PayBillRequest("SPOTHER", null)));
    }

    @Test
    void largeAmount_requiresPan() {
        bill.setBillAmount(new BigDecimal("50000.00"));

        assertThrows(IllegalArgumentException.class,
                () -> service.payBill(MERCHANT, new PayBillRequest(REQ, null)));
        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
    }

    @Test
    void invalidPan_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.payBill(MERCHANT, new PayBillRequest(REQ, "BAD123")));
    }

    @Test
    void insufficientBalance_isRejected_beforeAnythingChanges() {
        wallet.setAvailableBalance(new BigDecimal("100.00"));

        assertThrows(IllegalStateException.class,
                () -> service.payBill(MERCHANT, new PayBillRequest(REQ, null)));

        assertEquals(Status.FETCHED, bill.getStatus());
        assertEquals(new BigDecimal("100.00"), wallet.getAvailableBalance());
        verify(client, never()).post(eq("/pay"), any(), any());
    }

    @Test
    void fetchBill_storesOnlyLast4_andReturnsAmountAndCharges() throws Exception {
        when(client.post(eq("/fetch"), any(), any())).thenReturn(resp(200, """
                {"status":"success","response_code":"000","data":{"request_id":"SPNEW1",
                 "biller_id":"IDFC00000NATFQ","biller_name":"IDFC First Bank Credit Card","amount":5423.56,
                 "bill_date":"2025-01-10","due_date":"2025-02-05","customer_name":"RAJESH KUMAR","bill_number":"CC1"}}"""));

        FetchedBill f = service.fetchBill(MERCHANT, new FetchBillRequest("IDFC00000NATFQ", "3044", "9250907790"));

        assertEquals("SPNEW1", f.requestId());
        assertEquals(new BigDecimal("5423.56"), f.billAmount());
        assertEquals(new BigDecimal("5428.56"), f.totalPayable());
        assertFalse(f.panRequired());

        verify(repo).save(argThat(t -> "3044".equals(t.getCardLast4()) && t.getStatus() == Status.FETCHED
                && new BigDecimal("5423.56").equals(t.getBillAmount())));

        // the request carries the mobile at top level too, as in SuivrePay's sample
        verify(client).post(eq("/fetch"), argThat(b -> "9250907790".equals(((java.util.Map<?, ?>) b).get("mobile"))
                && "IDFC00000NATFQ".equals(((java.util.Map<?, ?>) b).get("biller_id"))),
                argThat(ctx -> "FETCH_BILL".equals(ctx.action()) && MERCHANT.equals(ctx.merchantId())));
    }

    @Test
    void fetchBill_tooManyRequestsForBiller_ERR033_showsAFriendlyMessage() throws Exception {
        when(client.post(eq("/fetch"), any(), any())).thenReturn(resp(200,
                "{\"status\":\"error\",\"response_code\":\"001\",\"error_code\":\"ERR033\","
                        + "\"message\":\"Too many request for that Biller\"}"));

        com.project2.ism.Exception.CreditCardBillException ex = assertThrows(
                com.project2.ism.Exception.CreditCardBillException.class,
                () -> service.fetchBill(MERCHANT, new FetchBillRequest("ICIC00000NATSI", "1003", "7506821018")));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("wait a few minutes"), ex.getMessage());
    }

    @Test
    void fetchBill_sameCardFetchedMomentsAgo_reusesTheBill_andDoesNotCallTheBillerAgain() {
        when(repo.findFirstByMerchantIdAndBillerIdAndCardLast4AndCustomerMobileAndStatusAndFetchedAtAfterOrderByFetchedAtDesc(
                eq(MERCHANT), eq("IDFC00000NATFQ"), eq("3044"), eq("9250907790"), eq(Status.FETCHED), any()))
                .thenReturn(Optional.of(bill));

        FetchedBill f = service.fetchBill(MERCHANT, new FetchBillRequest("IDFC00000NATFQ", "3044", "9250907790"));

        assertEquals(REQ, f.requestId());
        assertEquals(new BigDecimal("1000.00"), f.billAmount());
        assertEquals(new BigDecimal("1005.00"), f.totalPayable());
        verify(client, never()).post(eq("/fetch"), any(), any());
    }

    @Test
    void fetchBill_rejectsBadCardDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> service.fetchBill(MERCHANT, new FetchBillRequest("X", "30445", "9250907790")));
        assertThrows(IllegalArgumentException.class,
                () -> service.fetchBill(MERCHANT, new FetchBillRequest("X", "3044", "12345")));
    }
}
