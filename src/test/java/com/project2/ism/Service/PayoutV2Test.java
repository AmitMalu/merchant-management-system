package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.PayoutDTO.PayoutRequest;
import com.project2.ism.DTO.PayoutDTO.PayoutResult;
import com.project2.ism.DTO.PayoutDTO.SimplePayoutRequest;
import com.project2.ism.Model.ContactPerson;
import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.FranchiseWallet;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.MerchantWallet;
import com.project2.ism.Model.Payment.VendorBank;
import com.project2.ism.Model.Payment.VendorState;
import com.project2.ism.Model.Payout.PaymentAttempt;
import com.project2.ism.Model.Payout.PayoutBanks;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import com.project2.ism.Repository.FranchiseWalletRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.Repository.MerchantWalletRepository;
import com.project2.ism.Repository.PaymentAttemptRepository;
import com.project2.ism.Repository.PaymentVendorResponseLogRepository;
import com.project2.ism.Repository.PayoutBankRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import com.project2.ism.Repository.VendorBankRepository;
import com.project2.ism.Repository.VendorStateRepository;
import com.project2.ism.Service.Monitoring.AlertService;
import com.project2.ism.Service.Monitoring.TransactionEventService;
import com.project2.ism.Service.PayoutVendorAdapter.AttemptResult;
import com.project2.ism.Service.PayoutVendorAdapter.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The new payout flow (V2): the debit is committed first, the vendor is called with no transaction and no wallet
 * lock, and the answer is applied afterwards. Every outcome must leave the wallet, the ledger, the payout and the
 * attempt in the right state, and a refund must happen once, never twice.
 */
class PayoutV2Test {

    private static final Long VENDOR = 1L;
    private static final Long MERCHANT_ID = 20L;

    private PayoutTransactionRepository payoutTxns;
    private MerchantWalletRepository merchantWallets;
    private MerchantTransDetRepository merchantLedger;
    private MerchantRepository merchants;
    private PaymentChargeService charges;
    private FeatureAccessService access;
    private PaymentAttemptRepository attempts;
    private PayoutVendorAdapter adapter;
    private PayoutBankRepository payoutBanks;
    private VendorStateRepository vendorStates;
    private VendorBankRepository vendorBanks;
    private FranchiseWalletRepository franchiseWallets;
    private FranchiseTransDetRepository franchiseLedger;
    private FranchiseRepository franchises;

    private PayoutService service;
    private MerchantWallet wallet;
    private PayoutTransaction savedPayout;
    private PaymentAttempt savedAttempt;
    private int ledgerRows;

    @BeforeEach
    void setUp() {
        payoutTxns = mock(PayoutTransactionRepository.class);
        merchantWallets = mock(MerchantWalletRepository.class);
        merchantLedger = mock(MerchantTransDetRepository.class);
        merchants = mock(MerchantRepository.class);
        charges = mock(PaymentChargeService.class);
        access = mock(FeatureAccessService.class);
        attempts = mock(PaymentAttemptRepository.class);
        adapter = mock(PayoutVendorAdapter.class);
        payoutBanks = mock(PayoutBankRepository.class);
        vendorStates = mock(VendorStateRepository.class);
        vendorBanks = mock(VendorBankRepository.class);
        franchiseWallets = mock(FranchiseWalletRepository.class);
        franchiseLedger = mock(FranchiseTransDetRepository.class);
        franchises = mock(FranchiseRepository.class);

        service = new PayoutService(payoutTxns, merchantWallets, franchiseWallets, merchantLedger,
                franchiseLedger, merchants, franchises,
                mock(VimoPayClientService.class), charges, payoutBanks, vendorStates, vendorBanks,
                mock(PaymentVendorResponseLogRepository.class), new ObjectMapper(), merchantLedger,
                mock(TransactionEventService.class), mock(AlertService.class), access);
        ReflectionTestUtils.setField(service, "payoutAdapters", List.of(adapter));
        ReflectionTestUtils.setField(service, "attemptRepo", attempts);
        ReflectionTestUtils.setField(service, "txManager", mock(PlatformTransactionManager.class));

        when(adapter.supports(VENDOR)).thenReturn(true);

        Merchant merchant = mock(Merchant.class);
        when(merchant.getId()).thenReturn(MERCHANT_ID);
        when(merchant.getPayout()).thenReturn(true);
        when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));

        wallet = new MerchantWallet();
        wallet.setAvailableBalance(new BigDecimal("10000"));
        wallet.setCutOfAmount(BigDecimal.ZERO);
        when(merchantWallets.findByMerchantId(MERCHANT_ID)).thenReturn(Optional.of(wallet));
        when(merchantWallets.findByMerchantIdForUpdate(MERCHANT_ID)).thenReturn(Optional.of(wallet));

        when(charges.calculateCharges(any(), any(), eq(MERCHANT_ID))).thenReturn(new BigDecimal("20"));

        ledgerRows = 0;
        when(merchantLedger.save(any())).thenAnswer(i -> {
            MerchantTransactionDetails row = i.getArgument(0);
            if (row.getTransactionId() == null) {            // a NEW ledger row (a save of an existing one is an update)
                ledgerRows++;
                ReflectionTestUtils.setField(row, "transactionId", 5000L + ledgerRows);
                when(merchantLedger.findById(row.getTransactionId())).thenReturn(Optional.of(row));
            }
            return row;
        });

        when(payoutTxns.save(any())).thenAnswer(i -> {
            PayoutTransaction p = i.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L);
            }
            savedPayout = p;
            return p;
        });
        when(payoutTxns.findByIdForUpdate(100L)).thenAnswer(i -> Optional.of(savedPayout));

        when(attempts.save(any())).thenAnswer(i -> {
            PaymentAttempt a = i.getArgument(0);
            if (a.getId() == null) {
                ReflectionTestUtils.setField(a, "id", 7L);
            }
            savedAttempt = a;
            return a;
        });
        when(attempts.findById(7L)).thenAnswer(i -> Optional.of(savedAttempt));
    }

    private PayoutRequest request() {
        PayoutRequest r = new PayoutRequest();
        r.setInitiatorType("MERCHANT");
        r.setInitiatorId(MERCHANT_ID);
        r.setAmount(new BigDecimal("1000"));
        r.setPaymentMode("IMPS");
        r.setPaymentPurpose("004");
        r.setBeneficiaryName("RAMESH KUMAR");
        r.setBeneficiaryAccountNumber("13948100018021");
        r.setBeneficiaryIFSC("BARB0INDAHM");
        r.setBeneficiaryBank("067");
        r.setBeneficiaryLocation("MH");
        return r;
    }

    private static AttemptResult answer(Outcome outcome) {
        return new AttemptResult(outcome, "VTX1", "RRN1", "message", null, null);
    }

    // ---------------------------------------------------------------- the outcomes

    @Test
    void success_debitsOnce_andMarksEverythingSuccessful() {
        when(adapter.submit(eq(VENDOR), any(), any())).thenReturn(answer(Outcome.SUCCESS));

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("OK", result.getStatus());
        assertEquals(new BigDecimal("8980"), wallet.getAvailableBalance());       // 10000 - 1000 - 20, once
        assertEquals(PayoutTransaction.PayoutStatus.SUCCESS, savedPayout.getStatus());
        assertEquals(PaymentAttempt.SUCCESS, savedAttempt.getStatus());
        assertEquals("VTX1", savedAttempt.getVendorTxnId());
        assertNotNull(savedAttempt.getCompletedAt());
        assertEquals(1, ledgerRows);                                              // the debit only: no refund row
        assertNull(savedPayout.getLedgerRefundMerchantTxnId());
    }

    @Test
    void clearFailure_refundsTheFullAmount_exactlyOnce() {
        when(adapter.submit(eq(VENDOR), any(), any())).thenReturn(answer(Outcome.FAILED));

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("FAILED", result.getStatus());
        assertEquals(new BigDecimal("10000"), wallet.getAvailableBalance());      // amount AND charge came back
        assertEquals(PayoutTransaction.PayoutStatus.FAILED, savedPayout.getStatus());
        assertEquals(PaymentAttempt.FAILED, savedAttempt.getStatus());
        assertEquals(2, ledgerRows);                                              // debit + refund
        assertNotNull(savedPayout.getLedgerRefundMerchantTxnId());

        // a second refund of the same payout (manual update, repeated callback...) must do nothing
        service.refundPayoutForManualFailure(savedPayout);
        assertEquals(new BigDecimal("10000"), wallet.getAvailableBalance());
        assertEquals(2, ledgerRows);
    }

    @Test
    void pending_keepsTheDebit_andWaitsForTheCallback() {
        when(adapter.submit(eq(VENDOR), any(), any())).thenReturn(answer(Outcome.PENDING));

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("PENDING", result.getStatus());
        assertEquals(new BigDecimal("8980"), wallet.getAvailableBalance());
        assertEquals(PayoutTransaction.PayoutStatus.PENDING, savedPayout.getStatus());
        assertEquals(PaymentAttempt.PENDING, savedAttempt.getStatus());
        assertNull(savedAttempt.getCompletedAt());
        assertEquals(1, ledgerRows);
    }

    @Test
    void aBrokenVendorCall_leavesTheDebitAndAPendingPayout_forACheck_neverARefund() {
        when(adapter.submit(eq(VENDOR), any(), any())).thenThrow(new IllegalStateException("connection reset"));

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("PENDING", result.getStatus());                              // not "FAILED": nobody knows
        assertTrue(result.getMessage().contains("being processed"), result.getMessage());
        assertEquals(new BigDecimal("8980"), wallet.getAvailableBalance());       // money is held, not refunded
        assertEquals(PayoutTransaction.PayoutStatus.PENDING, savedPayout.getStatus());
        assertEquals(PaymentAttempt.UNKNOWN, savedAttempt.getStatus());
        assertEquals(1, ledgerRows);
    }

    // ---------------------------------------------------------------- a franchise pays out too

    private FranchiseWallet franchiseSetup() {
        Franchise franchise = mock(Franchise.class);
        when(franchise.getId()).thenReturn(3L);
        when(franchises.findById(3L)).thenReturn(Optional.of(franchise));
        FranchiseWallet fw = new FranchiseWallet();
        fw.setAvailableBalance(new BigDecimal("10000"));
        when(franchiseWallets.findByFranchiseIdForUpdate(3L)).thenReturn(Optional.of(fw));
        when(charges.calculateCharges(any(), any(), eq(3L))).thenReturn(new BigDecimal("20"));
        franchiseLedgerRows = 0;
        when(franchiseLedger.save(any())).thenAnswer(i -> {
            FranchiseTransactionDetails row = i.getArgument(0);
            if (row.getTransactionId() == null) {
                franchiseLedgerRows++;
                ReflectionTestUtils.setField(row, "transactionId", 9000L + franchiseLedgerRows);
                when(franchiseLedger.findById(row.getTransactionId())).thenReturn(Optional.of(row));
            }
            return row;
        });
        return fw;
    }

    private int franchiseLedgerRows;

    private PayoutRequest franchiseRequest() {
        PayoutRequest r = request();
        r.setInitiatorType("FRANCHISE");
        r.setInitiatorId(3L);
        return r;
    }

    @Test
    void aFranchisePayout_success_debitsTheFranchiseWalletOnce() {
        FranchiseWallet fw = franchiseSetup();
        when(adapter.submit(eq(VENDOR), any(), any())).thenReturn(answer(Outcome.SUCCESS));

        PayoutResult result = service.initiatePayoutV2(franchiseRequest(), VENDOR);

        assertEquals("OK", result.getStatus());
        assertEquals(new BigDecimal("8980"), fw.getAvailableBalance());
        assertEquals(1, franchiseLedgerRows);
        assertTrue(savedPayout.getMerchantRefId().startsWith("FRA"), savedPayout.getMerchantRefId());
        assertEquals(PayoutTransaction.PayoutStatus.SUCCESS, savedPayout.getStatus());
        assertEquals(new BigDecimal("10000"), wallet.getAvailableBalance());      // a merchant wallet is never touched
    }

    @Test
    void aFranchisePayout_clearFailure_refundsOnce() {
        FranchiseWallet fw = franchiseSetup();
        when(adapter.submit(eq(VENDOR), any(), any())).thenReturn(answer(Outcome.FAILED));

        PayoutResult result = service.initiatePayoutV2(franchiseRequest(), VENDOR);

        assertEquals("FAILED", result.getStatus());
        assertEquals(new BigDecimal("10000"), fw.getAvailableBalance());
        assertEquals(2, franchiseLedgerRows);                                     // debit + refund
        assertNotNull(savedPayout.getLedgerRefundFranchiseTxnId());

        service.refundPayoutForManualFailure(savedPayout);                        // a second refund does nothing
        assertEquals(new BigDecimal("10000"), fw.getAvailableBalance());
        assertEquals(2, franchiseLedgerRows);
    }

    // ---------------------------------------------------------------- nothing moves

    @Test
    void notEnoughBalance_refusesBeforeAnythingIsSavedOrSent() {
        wallet.setAvailableBalance(new BigDecimal("500"));

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("FAILED", result.getStatus());
        assertEquals(new BigDecimal("500"), wallet.getAvailableBalance());
        verify(payoutTxns, never()).save(any());
        verify(adapter, never()).submit(any(), any(), any());
        assertEquals(0, ledgerRows);
    }

    @Test
    void anAmountOutsideTheVendorLimits_isRefusedBeforeAnythingMoves() {
        for (String amount : new String[]{"99", "14", "0", "100000", "250000"}) {
            PayoutRequest r = request();
            r.setAmount(new BigDecimal(amount));

            PayoutResult result = service.initiatePayoutV2(r, VENDOR);

            assertEquals("FAILED", result.getStatus(), amount);
            assertTrue(result.getMessage().contains("between Rs 100 and Rs 99999"), result.getMessage());
        }
        assertEquals(new BigDecimal("10000"), wallet.getAvailableBalance());
        verify(payoutTxns, never()).save(any());
        verify(adapter, never()).submit(any(), any(), any());
        assertEquals(0, ledgerRows);
    }

    @Test
    void theLimitsThemselvesAreAllowed() {
        assertDoesNotThrow(() -> service.validateAmountLimits(new BigDecimal("100")));
        assertDoesNotThrow(() -> service.validateAmountLimits(new BigDecimal("99999")));
        assertThrows(IllegalArgumentException.class, () -> service.validateAmountLimits(null));
    }

    // ---------------------------------------------------------------- routing and retries (Phase 4)

    private PayoutRoutingPlanner routeTo(Long vendorId, int tries) {
        PayoutRoutingPlanner planner = mock(PayoutRoutingPlanner.class);
        when(planner.choose(any(), any(), any(), any(), any()))
                .thenReturn(new PayoutRoutingPlanner.Choice(vendorId, tries, 0, true));
        ReflectionTestUtils.setField(service, "routingPlanner", planner);
        return planner;
    }

    /** Every attempt row that was created, in order (a row is saved several times; it is one row). */
    private List<PaymentAttempt> attemptRows() {
        org.mockito.ArgumentCaptor<PaymentAttempt> captor = org.mockito.ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(attempts, atLeastOnce()).save(captor.capture());
        List<PaymentAttempt> rows = new java.util.ArrayList<>();
        for (PaymentAttempt a : captor.getAllValues()) {
            if (rows.stream().noneMatch(r -> r == a)) {
                rows.add(a);
            }
        }
        return rows;
    }

    @Test
    void aTryTheVendorDidNotTake_isRepeated_andTheDebitHappensOnlyOnce() {
        routeTo(VENDOR, 3);
        when(adapter.submit(eq(VENDOR), any(), any()))
                .thenReturn(answer(Outcome.NOT_PROCESSED), answer(Outcome.NOT_PROCESSED), answer(Outcome.SUCCESS));

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("OK", result.getStatus());
        verify(adapter, times(3)).submit(eq(VENDOR), any(), any());
        assertEquals(new BigDecimal("8980"), wallet.getAvailableBalance());       // debited once, not three times
        assertEquals(1, ledgerRows);
        List<PaymentAttempt> rows = attemptRows();
        assertEquals(3, rows.size());
        assertEquals(List.of(1, 2, 3), rows.stream().map(PaymentAttempt::getAttemptNo).toList());
        assertEquals(List.of(PaymentAttempt.FAILED, PaymentAttempt.FAILED, PaymentAttempt.SUCCESS),
                rows.stream().map(PaymentAttempt::getStatus).toList());
        assertEquals(1, rows.stream().map(PaymentAttempt::getVendorReference).distinct().count());   // same reference
    }

    @Test
    void whenEveryTryIsNotTaken_theTriesAreUsedUp_andThePayoutIsRefundedOnce() {
        routeTo(VENDOR, 3);
        when(adapter.submit(eq(VENDOR), any(), any())).thenReturn(answer(Outcome.NOT_PROCESSED));

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("FAILED", result.getStatus());
        verify(adapter, times(3)).submit(eq(VENDOR), any(), any());
        assertEquals(new BigDecimal("10000"), wallet.getAvailableBalance());      // all of it came back
        assertEquals(PayoutTransaction.PayoutStatus.FAILED, savedPayout.getStatus());
        assertEquals(2, ledgerRows);                                              // debit + one refund
        assertEquals(3, attemptRows().size());
        assertTrue(attemptRows().stream().allMatch(a -> PaymentAttempt.FAILED.equals(a.getStatus())));
    }

    @Test
    void aFinalFailure_aPendingAnswerAndAnUnknownOne_areNeverRepeated() {
        routeTo(VENDOR, 3);
        for (Outcome o : new Outcome[]{Outcome.FAILED, Outcome.PENDING, Outcome.UNKNOWN}) {
            reset(adapter);
            when(adapter.supports(VENDOR)).thenReturn(true);
            when(adapter.submit(eq(VENDOR), any(), any())).thenReturn(answer(o));
            wallet.setAvailableBalance(new BigDecimal("10000"));

            service.initiatePayoutV2(request(), VENDOR);

            verify(adapter, times(1)).submit(eq(VENDOR), any(), any());       // a duplicate payment is the worst case
        }
    }

    @Test
    void withoutRouting_aNotTakenTryIsNotRepeated() {
        when(adapter.submit(eq(VENDOR), any(), any())).thenReturn(answer(Outcome.NOT_PROCESSED));

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("FAILED", result.getStatus());
        verify(adapter, times(1)).submit(eq(VENDOR), any(), any());
        assertEquals(new BigDecimal("10000"), wallet.getAvailableBalance());
    }

    @Test
    void theVendorRoutingChoosesIsTheOneUsed_andIsRecordedOnThePayoutAndTheAttempt() {
        PayoutVendorAdapter other = mock(PayoutVendorAdapter.class);
        when(other.supports(2L)).thenReturn(true);
        when(other.submit(eq(2L), any(), any())).thenReturn(answer(Outcome.SUCCESS));
        ReflectionTestUtils.setField(service, "payoutAdapters", List.of(adapter, other));
        routeTo(2L, 3);

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("OK", result.getStatus());
        verify(other).submit(eq(2L), any(), any());
        verify(adapter, never()).submit(any(), any(), any());
        assertEquals(2L, savedPayout.getVendorId());
        assertEquals(2L, savedAttempt.getVendorId());
    }

    @Test
    void whenRoutingFindsNoVendor_thePayoutIsRefusedBeforeAnythingIsDebited() {
        PayoutRoutingPlanner planner = mock(PayoutRoutingPlanner.class);
        when(planner.choose(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Payout is not available right now. Please try again later."));
        ReflectionTestUtils.setField(service, "routingPlanner", planner);

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("FAILED", result.getStatus());
        assertEquals("Payout is not available right now. Please try again later.", result.getMessage());
        assertEquals(new BigDecimal("10000"), wallet.getAvailableBalance());
        verify(payoutTxns, never()).save(any());
        verify(adapter, never()).submit(any(), any(), any());
        assertEquals(0, ledgerRows);
    }

    @Test
    void payoutSwitchOff_refusesBeforeAnythingMoves() {
        doThrow(new IllegalStateException("Payout is not enabled for your account. Please contact the administrator."))
                .when(access).requirePayoutEnabled(any());

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("not enabled"), result.getMessage());
        assertEquals(new BigDecimal("10000"), wallet.getAvailableBalance());
        verify(payoutTxns, never()).save(any());
        verify(adapter, never()).submit(any(), any(), any());
    }

    @Test
    void aVendorWithoutAnAdapter_isRefusedWithoutTouchingMoney() {
        PayoutResult result = service.initiatePayoutV2(request(), 99L);

        assertEquals("FAILED", result.getStatus());
        assertEquals(new BigDecimal("10000"), wallet.getAvailableBalance());
        verify(payoutTxns, never()).save(any());
        assertFalse(service.v2Available(99L));
        assertTrue(service.v2Available(VENDOR));
    }

    // ---------------------------------------------------------------- a callback can arrive first

    @Test
    void aCallbackThatAlreadySettledThePayout_isNeverOverwrittenByTheImmediateAnswer() {
        when(adapter.submit(eq(VENDOR), any(), any())).thenAnswer(i -> {
            // while the vendor call is in flight, the vendor's callback settles the payout
            savedPayout.setStatus(PayoutTransaction.PayoutStatus.SUCCESS);
            return answer(Outcome.PENDING);
        });

        PayoutResult result = service.initiatePayoutV2(request(), VENDOR);

        assertEquals(PayoutTransaction.PayoutStatus.SUCCESS, savedPayout.getStatus());   // not pushed back to PENDING
        assertEquals("OK", result.getStatus());
        assertEquals(new BigDecimal("8980"), wallet.getAvailableBalance());
        assertEquals(1, ledgerRows);
        // the try agrees with the payout, not with the PENDING answer that was ignored
        assertEquals(PaymentAttempt.SUCCESS, savedAttempt.getStatus());
        assertNotNull(savedAttempt.getCompletedAt());
    }

    // ---------------------------------------------------------------- the attempt follows the callback

    @Test
    void aCallbackThatSettlesAPendingPayout_alsoClosesItsAttempt() {
        when(adapter.submit(eq(VENDOR), any(), any())).thenReturn(answer(Outcome.PENDING));
        service.initiatePayoutV2(request(), VENDOR);
        assertEquals(PaymentAttempt.PENDING, savedAttempt.getStatus());
        when(attempts.findBySourceTypeAndSourceIdOrderByAttemptNoAsc(PaymentAttempt.SOURCE_PAYOUT, 100L))
                .thenReturn(List.of(savedAttempt));
        when(payoutTxns.findByMerchantRefIdForUpdate(savedPayout.getMerchantRefId()))
                .thenReturn(Optional.of(savedPayout));

        com.project2.ism.DTO.PayoutDTO.PayoutCallback callback = new com.project2.ism.DTO.PayoutDTO.PayoutCallback();
        callback.setMerchantRefId(savedPayout.getMerchantRefId());
        callback.setTxnStatus("Success");
        callback.setTxnStatusCode("000");
        callback.setTxnId("VTX9");
        callback.setResponseMessage("Transaction successful");
        service.handleCallback(callback);

        assertEquals(PayoutTransaction.PayoutStatus.SUCCESS, savedPayout.getStatus());
        assertEquals(PaymentAttempt.SUCCESS, savedAttempt.getStatus());
        assertNotNull(savedAttempt.getCompletedAt());
        assertEquals("Transaction successful", savedAttempt.getMessage());
        assertEquals(new BigDecimal("8980"), wallet.getAvailableBalance());       // the money is untouched
    }

    @Test
    void closingAttempts_touchesOnlyOpenOnes_andNeverThrows() {
        PaymentAttempt earlier = new PaymentAttempt();
        earlier.setStatus(PaymentAttempt.FAILED);
        earlier.setMessage("not taken");
        PaymentAttempt open = new PaymentAttempt();
        open.setStatus(PaymentAttempt.PENDING);
        when(attempts.findBySourceTypeAndSourceIdOrderByAttemptNoAsc(PaymentAttempt.SOURCE_PAYOUT, 100L))
                .thenReturn(List.of(earlier, open));

        service.closeAttempts(100L, PaymentAttempt.SUCCESS, "ok", "000", "VTX1");

        assertEquals(PaymentAttempt.FAILED, earlier.getStatus());                 // a finished try is left alone
        assertEquals("not taken", earlier.getMessage());
        assertEquals(PaymentAttempt.SUCCESS, open.getStatus());
        assertEquals("VTX1", open.getVendorTxnId());

        when(attempts.findBySourceTypeAndSourceIdOrderByAttemptNoAsc(any(), any()))
                .thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.closeAttempts(100L, PaymentAttempt.FAILED, null, null, null));
    }

    // ---------------------------------------------------------------- the saved-bank payout

    @Test
    void aSavedBankIsConvertedWithVendorCodesAndPlainNames() {
        PayoutBanks bank = mock(PayoutBanks.class);
        when(bank.getCustomerType()).thenReturn("MERCHANT");
        when(bank.getCustomerId()).thenReturn(MERCHANT_ID);
        when(bank.getBankName()).thenReturn("Bank of Baroda");
        when(bank.getStateName()).thenReturn("Maharashtra");
        when(bank.getAccountNumber()).thenReturn("13948100018021");
        when(bank.getIfscCode()).thenReturn("BARB0INDAHM");
        when(bank.getHolderName()).thenReturn("DINESH KIRAN ROKADE");
        when(payoutBanks.findByIdAndVerifiedTrue(9L)).thenReturn(Optional.of(bank));
        VendorState state = mock(VendorState.class);
        when(state.getStateCode()).thenReturn("MH");
        when(vendorStates.findByStateName("Maharashtra")).thenReturn(Optional.of(state));
        VendorBank vendorBank = mock(VendorBank.class);
        when(vendorBank.getBankCode()).thenReturn("067");
        when(vendorBanks.findByBankName("Bank of Baroda")).thenReturn(Optional.of(vendorBank));
        Merchant merchant = merchants.findById(MERCHANT_ID).get();
        ContactPerson contact = mock(ContactPerson.class);
        when(contact.getPhoneNumber()).thenReturn("9689571574");
        when(merchant.getContactPerson()).thenReturn(contact);

        SimplePayoutRequest simple = new SimplePayoutRequest();
        simple.setPayoutBankId(9L);
        simple.setAmount(new BigDecimal("8500"));
        simple.setPaymentMode("imps");

        PayoutRequest full = service.toPayoutRequest(simple);

        assertEquals("067", full.getBeneficiaryBank());                  // ViMoPay's code, as before
        assertEquals("MH", full.getBeneficiaryLocation());
        assertEquals("004", full.getPaymentPurpose());
        assertEquals("Bank of Baroda", full.getBeneficiaryBankName());   // and the plain words, for a later vendor
        assertEquals("Maharashtra", full.getBeneficiaryStateName());
        assertEquals(new CustomerRef("MERCHANT", MERCHANT_ID), service.savedBankOwner(9L));
        assertNull(service.savedBankOwner(404L));
    }
}
