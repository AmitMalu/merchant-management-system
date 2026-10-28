package com.project2.ism.Helper;

import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import com.project2.ism.Service.PayoutService;
import com.project2.ism.request.ManualPayoutStatusUpdateRequest.ManualPayoutStatus;
import com.project2.ism.request.ManualPayoutUpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Admin "Update Transaction" for payouts. A merchant payout is found by its MERCHANT ledger id
 * (unchanged); a franchise's own payout is found by its FRANCHISE ledger id. The two ledgers
 * are different tables with overlapping ids, so the wrong one must never be touched.
 */
class ManualPayoutTransactionProcessorTest {

    private PayoutTransactionRepository payoutRepo;
    private MerchantTransDetRepository merchantLedger;
    private FranchiseTransDetRepository franchiseLedger;
    private PayoutService payoutService;
    private ManualPayoutTransactionProcessor processor;

    @BeforeEach
    void setUp() {
        payoutRepo = mock(PayoutTransactionRepository.class);
        merchantLedger = mock(MerchantTransDetRepository.class);
        franchiseLedger = mock(FranchiseTransDetRepository.class);
        payoutService = mock(PayoutService.class);
        processor = new ManualPayoutTransactionProcessor(payoutRepo, merchantLedger, franchiseLedger, payoutService);

        when(payoutRepo.save(any(PayoutTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private PayoutTransaction franchisePayout(PayoutTransaction.PayoutStatus status) {
        PayoutTransaction p = new PayoutTransaction();
        p.setId(900L);
        p.setInitiatorType("FRANCHISE");
        p.setInitiatorId(5L);
        p.setStatus(status);
        p.setMerchantRefId("FRA-77");
        p.setTotalDeducted(new BigDecimal("1010.00"));
        p.setLedgerFranchiseTxnId(77L);
        return p;
    }

    private PayoutTransaction merchantPayout() {
        PayoutTransaction p = new PayoutTransaction();
        p.setId(901L);
        p.setInitiatorType("MERCHANT");
        p.setInitiatorId(8L);
        p.setStatus(PayoutTransaction.PayoutStatus.PENDING);
        p.setMerchantRefId("MER-77");
        p.setTotalDeducted(new BigDecimal("505.00"));
        p.setLedgerMerchantTxnId(77L);
        return p;
    }

    // ------------------------------------------------------------------ franchise

    @Test
    void franchiseSuccess_marksPayoutAndFranchiseLedger_withoutRefund() {
        PayoutTransaction p = franchisePayout(PayoutTransaction.PayoutStatus.PENDING);
        FranchiseTransactionDetails ledger = new FranchiseTransactionDetails();
        ledger.setTranStatus("PENDING");
        when(payoutRepo.findByFranchiseLedgerIdForManualUpdate(77L)).thenReturn(Optional.of(p));
        when(franchiseLedger.findById(77L)).thenReturn(Optional.of(ledger));

        ManualPayoutUpdateResult r = processor.processSingleFranchiseTransaction(77L, ManualPayoutStatus.SUCCESS, "paid at bank");

        assertTrue(r.isProcessed());
        assertEquals("SUCCESS", r.getFinalStatus());
        assertFalse(r.isWalletRefunded());
        assertEquals(PayoutTransaction.PayoutStatus.SUCCESS, p.getStatus());
        assertEquals("SUCCESS", ledger.getTranStatus());
        verify(payoutService, never()).refundPayoutForManualFailure(any());
        verify(payoutRepo, never()).findByIdForManualUpdate(any()); // the merchant ledger is not consulted
        verifyNoInteractions(merchantLedger);
    }

    @Test
    void franchiseFailed_refundsTheFranchiseWallet_andMarksTheLedgerFailed() {
        PayoutTransaction p = franchisePayout(PayoutTransaction.PayoutStatus.PENDING);
        FranchiseTransactionDetails ledger = new FranchiseTransactionDetails();
        ledger.setTranStatus("PENDING");
        when(payoutRepo.findByFranchiseLedgerIdForManualUpdate(77L)).thenReturn(Optional.of(p));
        when(franchiseLedger.findById(77L)).thenReturn(Optional.of(ledger));

        ManualPayoutUpdateResult r = processor.processSingleFranchiseTransaction(77L, ManualPayoutStatus.FAILED, "rejected by bank");

        assertTrue(r.isProcessed());
        assertTrue(r.isWalletRefunded());
        assertEquals("FAILED", r.getFinalStatus());
        assertEquals(PayoutTransaction.PayoutStatus.FAILED, p.getStatus());
        assertEquals("FAILED", ledger.getTranStatus());
        assertTrue(ledger.getFailureRemarks().contains("rejected by bank"));
        verify(payoutService, times(1)).refundPayoutForManualFailure(p); // refund once, for this payout
        verifyNoInteractions(merchantLedger);
    }

    @Test
    void franchiseAlreadyFinal_isRefused_andNothingIsRefundedTwice() {
        PayoutTransaction p = franchisePayout(PayoutTransaction.PayoutStatus.FAILED);
        when(payoutRepo.findByFranchiseLedgerIdForManualUpdate(77L)).thenReturn(Optional.of(p));

        assertThrows(IllegalStateException.class,
                () -> processor.processSingleFranchiseTransaction(77L, ManualPayoutStatus.FAILED, "again"));

        verify(payoutService, never()).refundPayoutForManualFailure(any());
        verify(franchiseLedger, never()).save(any());
    }

    @Test
    void franchiseIdThatDoesNotExist_isReported() {
        when(payoutRepo.findByFranchiseLedgerIdForManualUpdate(5L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.processSingleFranchiseTransaction(5L, ManualPayoutStatus.SUCCESS, "x"));

        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void franchisePath_refusesAPayoutThatIsNotAFranchiseOne() {
        PayoutTransaction notFranchise = merchantPayout();
        notFranchise.setLedgerFranchiseTxnId(77L); // should never happen, but the money must not move
        when(payoutRepo.findByFranchiseLedgerIdForManualUpdate(77L)).thenReturn(Optional.of(notFranchise));

        assertThrows(IllegalStateException.class,
                () -> processor.processSingleFranchiseTransaction(77L, ManualPayoutStatus.FAILED, "x"));

        verify(payoutService, never()).refundPayoutForManualFailure(any());
    }

    // ------------------------------------------------------------------ merchant (unchanged)

    @Test
    void merchantPath_stillUsesTheMerchantLedger() {
        PayoutTransaction p = merchantPayout();
        MerchantTransactionDetails ledger = new MerchantTransactionDetails();
        ledger.setTranStatus("PENDING");
        when(payoutRepo.findByIdForManualUpdate(77L)).thenReturn(Optional.of(p));
        when(merchantLedger.findById(77L)).thenReturn(Optional.of(ledger));

        ManualPayoutUpdateResult r = processor.processSingleTransaction(77L, ManualPayoutStatus.FAILED, "rejected");

        assertTrue(r.isProcessed());
        assertTrue(r.isWalletRefunded());
        assertEquals("FAILED", ledger.getTranStatus());
        verify(payoutService).refundPayoutForManualFailure(p);
        verify(payoutRepo, never()).findByFranchiseLedgerIdForManualUpdate(any()); // franchise ledger not consulted
        verifyNoInteractions(franchiseLedger);
    }
}
