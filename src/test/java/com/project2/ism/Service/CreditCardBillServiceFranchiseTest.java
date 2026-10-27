package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.FetchBillRequest;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.PayBillRequest;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.PaymentResult;
import com.project2.ism.Exception.CreditCardBillException;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction.Status;
import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.FranchiseWallet;
import com.project2.ism.Model.Users.Franchise;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * A franchise pays a credit card bill from its OWN wallet and ledger (never the merchant ones),
 * and only when the account's switch is on.
 */
class CreditCardBillServiceFranchiseTest {

    private static final Long FRANCHISE_ID = 5L;
    private static final CustomerRef FRANCHISE = CustomerRef.franchise(FRANCHISE_ID);
    private static final String REQ = "SPFRANCHISE0001";

    private final ObjectMapper mapper = new ObjectMapper();

    private SuivrePayClient client;
    private CreditCardBillTransactionRepository repo;
    private MerchantWalletRepository merchantWalletRepo;
    private MerchantTransDetRepository merchantLedgerRepo;
    private FranchiseTransDetRepository franchiseLedgerRepo;
    private PaymentChargeService chargeService;
    private FeatureAccessService featureAccess;
    private CreditCardBillService service;

    private FranchiseWallet wallet;
    private CreditCardBillTransaction bill;

    @BeforeEach
    void setUp() {
        client = mock(SuivrePayClient.class);
        repo = mock(CreditCardBillTransactionRepository.class);
        merchantWalletRepo = mock(MerchantWalletRepository.class);
        MerchantRepository merchantRepo = mock(MerchantRepository.class);
        merchantLedgerRepo = mock(MerchantTransDetRepository.class);
        chargeService = mock(PaymentChargeService.class);
        FranchiseWalletRepository franchiseWalletRepo = mock(FranchiseWalletRepository.class);
        FranchiseRepository franchiseRepo = mock(FranchiseRepository.class);
        franchiseLedgerRepo = mock(FranchiseTransDetRepository.class);
        featureAccess = mock(FeatureAccessService.class);

        service = new CreditCardBillService(client, repo, merchantWalletRepo, merchantRepo, merchantLedgerRepo,
                chargeService, mock(TransactionEventService.class), mock(PlatformTransactionManager.class),
                franchiseWalletRepo, franchiseRepo, franchiseLedgerRepo, featureAccess);
        ReflectionTestUtils.setField(service, "billersPath", "/billers");
        ReflectionTestUtils.setField(service, "fetchBillPath", "/fetch");
        ReflectionTestUtils.setField(service, "payBillPath", "/pay");
        ReflectionTestUtils.setField(service, "duplicateWindowSeconds", 120L);
        ReflectionTestUtils.setField(service, "fetchReuseSeconds", 60L);
        ReflectionTestUtils.setField(service, "chargeModeCodes", "CREDIT_CARD");
        ReflectionTestUtils.setField(service, "chargeRequired", true);
        when(chargeService.calculateFranchiseCharges(any(), anyString(), eq(FRANCHISE_ID)))
                .thenReturn(new BigDecimal("5.00"));

        wallet = new FranchiseWallet();
        wallet.setAvailableBalance(new BigDecimal("10000.00"));
        when(franchiseWalletRepo.findByFranchiseIdForUpdate(FRANCHISE_ID)).thenReturn(Optional.of(wallet));
        when(franchiseRepo.findById(FRANCHISE_ID)).thenReturn(Optional.of(new Franchise()));
        when(franchiseLedgerRepo.save(any(FranchiseTransactionDetails.class))).thenAnswer(i -> i.getArgument(0));
        when(franchiseLedgerRepo.findById(any())).thenReturn(Optional.empty());

        when(client.mid()).thenReturn("AP503682");

        bill = new CreditCardBillTransaction();
        bill.setId(1L);
        bill.setCustomerType("FRANCHISE");
        bill.setMerchantId(FRANCHISE_ID);
        bill.setRequestId(REQ);
        bill.setBillerId("IDFC00000NATFQ");
        bill.setBillerName("IDFC First Bank Credit Card");
        bill.setCardLast4("3044");
        bill.setCustomerMobile("9250907790");
        bill.setBillAmount(new BigDecimal("1000.00"));
        bill.setStatus(Status.FETCHED);
        bill.setFetchedAt(LocalDateTime.now());

        when(repo.findByRequestIdForUpdate(REQ, "FRANCHISE", FRANCHISE_ID)).thenReturn(Optional.of(bill));
        when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(repo.save(any(CreditCardBillTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private VendorResponse resp(int http, String json) throws Exception {
        return new VendorResponse(http, mapper.readTree(json), json);
    }

    @Test
    void franchisePays_fromTheFranchiseWallet_andFranchiseLedger_notTheMerchantOnes() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200,
                "{\"status\":\"success\",\"response_code\":\"000\",\"data\":{\"wallet_txn_id\":\"SP999\"}}"));

        PaymentResult r = service.payBill(FRANCHISE, new PayBillRequest(REQ, null));

        assertEquals("SUCCESS", r.status());
        assertEquals(new BigDecimal("8995.00"), wallet.getAvailableBalance()); // 10000 - (1000 + charge 5)
        verifyNoInteractions(merchantWalletRepo, merchantLedgerRepo);

        ArgumentCaptor<FranchiseTransactionDetails> row = ArgumentCaptor.forClass(FranchiseTransactionDetails.class);
        verify(franchiseLedgerRepo).save(row.capture());
        assertEquals("CREDIT_CARD", row.getValue().getService());
        assertEquals("DEBIT", row.getValue().getActionOnBalance());
        assertEquals(new BigDecimal("5.00"), row.getValue().getCharge());
        assertEquals(new BigDecimal("-1005.00"), row.getValue().getAmount());
        assertEquals(new BigDecimal("-1000.00"), row.getValue().getNetAmount());
    }

    @Test
    void franchiseCharge_comesFromTheFranchiseChargeSetup_notTheMerchantOne() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(200,
                "{\"status\":\"success\",\"response_code\":\"000\",\"data\":{}}"));

        PaymentResult r = service.payBill(FRANCHISE, new PayBillRequest(REQ, null));

        assertEquals(new BigDecimal("5.00"), r.charges());
        verify(chargeService).calculateFranchiseCharges(any(), eq("CREDIT_CARD"), eq(FRANCHISE_ID));
        // the merchant method looks the id up as a MERCHANT, which would pick the wrong account
        verify(chargeService, never()).calculateCharges(any(), anyString(), any());
    }

    @Test
    void franchiseWithNoChargeConfigured_isRefused_andNothingIsDebited() {
        when(chargeService.calculateFranchiseCharges(any(), anyString(), anyLong()))
                .thenThrow(new IllegalStateException("No active charge configured"));

        assertThrows(CreditCardBillException.class,
                () -> service.payBill(FRANCHISE, new PayBillRequest(REQ, null)));

        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
        verify(client, never()).post(eq("/pay"), any(), any());
    }

    @Test
    void refusedPayment_refundsTheFranchiseWallet_andWritesAFranchiseRefundRow() throws Exception {
        when(client.post(eq("/pay"), any(), any())).thenReturn(resp(400,
                "{\"status\":\"error\",\"message\":\"This bill has already been paid.\"}"));

        PaymentResult r = service.payBill(FRANCHISE, new PayBillRequest(REQ, null));

        assertEquals("FAILED", r.status());
        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
        ArgumentCaptor<FranchiseTransactionDetails> rows = ArgumentCaptor.forClass(FranchiseTransactionDetails.class);
        verify(franchiseLedgerRepo, times(2)).save(rows.capture()); // the debit, then the refund
        assertEquals("CREDIT_CARD_REFUND", rows.getAllValues().get(1).getService());
        verifyNoInteractions(merchantWalletRepo, merchantLedgerRepo);
    }

    @Test
    void timeout_keepsTheFranchiseDebit_pending() throws Exception {
        when(client.post(eq("/pay"), any(), any()))
                .thenThrow(new SuivrePayClient.VendorUnreachableException("timeout", new RuntimeException()));

        PaymentResult r = service.payBill(FRANCHISE, new PayBillRequest(REQ, null));

        assertEquals("PENDING", r.status());
        assertEquals(new BigDecimal("8995.00"), wallet.getAvailableBalance());
    }

    @Test
    void aMerchantWithTheSameId_cannotPayAFranchisesBill() {
        when(repo.findByRequestIdForUpdate(REQ, "MERCHANT", FRANCHISE_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.payBill(CustomerRef.merchant(FRANCHISE_ID), new PayBillRequest(REQ, null)));

        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
    }

    @Test
    void insufficientFranchiseBalance_isRejected_beforeTheVendorIsCalled() {
        wallet.setAvailableBalance(new BigDecimal("100.00"));

        assertThrows(IllegalStateException.class,
                () -> service.payBill(FRANCHISE, new PayBillRequest(REQ, null)));

        assertEquals(Status.FETCHED, bill.getStatus());
        verify(client, never()).post(eq("/pay"), any(), any());
    }

    @Test
    void fetchBill_storesTheBillAgainstTheFranchise() throws Exception {
        when(client.post(eq("/fetch"), any(), any())).thenReturn(resp(200,
                "{\"status\":\"success\",\"response_code\":\"000\",\"data\":{\"request_id\":\"SPNEWF\","
                        + "\"biller_name\":\"IDFC\",\"amount\":2000.00}}"));

        service.fetchBill(FRANCHISE, new FetchBillRequest("IDFC00000NATFQ", "3044", "9250907790"));

        verify(repo).save(argThat(t -> "FRANCHISE".equals(t.getCustomerType())
                && FRANCHISE_ID.equals(t.getMerchantId()) && "SPNEWF".equals(t.getRequestId())));
    }

    // ------------------------------------------------------------------
    // The admin switch
    // ------------------------------------------------------------------

    private void switchOff() {
        doThrow(new CreditCardBillException(HttpStatus.FORBIDDEN, "not enabled"))
                .when(featureAccess).requireCreditCardEnabled(any());
    }

    @Test
    void switchOff_billersAreRefused_withoutCallingTheVendor() {
        switchOff();

        CreditCardBillException ex = assertThrows(CreditCardBillException.class, () -> service.getBillers(FRANCHISE));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(client);
    }

    @Test
    void switchOff_fetchBillIsRefused() {
        switchOff();

        assertThrows(CreditCardBillException.class,
                () -> service.fetchBill(FRANCHISE, new FetchBillRequest("IDFC00000NATFQ", "3044", "9250907790")));

        verifyNoInteractions(client);
    }

    @Test
    void switchOff_payIsRefused_andNothingIsDebited() {
        switchOff();

        assertThrows(CreditCardBillException.class,
                () -> service.payBill(FRANCHISE, new PayBillRequest(REQ, null)));

        assertEquals(new BigDecimal("10000.00"), wallet.getAvailableBalance());
        assertEquals(Status.FETCHED, bill.getStatus());
        verify(client, never()).post(anyString(), any(), any());
        verifyNoInteractions(merchantWalletRepo);
    }

    @Test
    void switchOff_pastPaymentsStayVisible() {
        switchOff();
        when(repo.findHistory(eq("FRANCHISE"), eq(FRANCHISE_ID), eq(Status.FETCHED), any())).thenReturn(List.of());

        assertNotNull(service.getHistory(FRANCHISE));
    }
}
