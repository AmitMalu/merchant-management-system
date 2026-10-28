package com.project2.ism.Service.AdminServices;

import com.project2.ism.DTO.ReportDTO.MerchantTransactionReportDTO;
import com.project2.ism.DTO.ReportDTO.TransactionReportDTO;
import com.project2.ism.Helper.ManualPayoutTransactionProcessor;
import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.request.ManualPayoutStatusUpdateRequest;
import com.project2.ism.request.ManualPayoutStatusUpdateRequest.ManualPayoutStatus;
import com.project2.ism.request.ManualPayoutUpdateResult;
import com.project2.ism.response.ManualPayoutStatusUpdateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UpdateTransactionServiceTest {

    private MerchantTransDetRepository merchantRepo;
    private FranchiseTransDetRepository franchiseRepo;
    private ManualPayoutTransactionProcessor processor;
    private UpdateTransactionService service;

    @BeforeEach
    void setUp() {
        merchantRepo = mock(MerchantTransDetRepository.class);
        franchiseRepo = mock(FranchiseTransDetRepository.class);
        processor = mock(ManualPayoutTransactionProcessor.class);
        service = new UpdateTransactionService(merchantRepo, franchiseRepo, processor);
    }

    private ManualPayoutStatusUpdateRequest request(String accountType, Long... ids) {
        ManualPayoutStatusUpdateRequest r = new ManualPayoutStatusUpdateRequest();
        r.setTransactionIds(List.of(ids));
        r.setStatus(ManualPayoutStatus.FAILED);
        r.setRemarks("bank rejected");
        r.setAccountType(accountType);
        return r;
    }

    private ManualPayoutUpdateResult ok(long id) {
        ManualPayoutUpdateResult r = new ManualPayoutUpdateResult();
        r.setTransactionId(id);
        r.setProcessed(true);
        r.setFinalStatus("FAILED");
        return r;
    }

    // ------------------------------------------------------------------ status update routing

    @Test
    void franchiseAccount_goesThroughTheFranchiseProcessor_only() {
        when(processor.processSingleFranchiseTransaction(anyLong(), any(), any())).thenAnswer(i -> ok(i.getArgument(0)));

        ManualPayoutStatusUpdateResponse r = service.updatePayoutTransactionStatuses(request("FRANCHISE", 10L, 11L, 10L));

        assertEquals(2, r.getTotalRequested());          // duplicate id removed
        assertEquals(2, r.getProcessedSuccessfully());
        verify(processor).processSingleFranchiseTransaction(10L, ManualPayoutStatus.FAILED, "bank rejected");
        verify(processor).processSingleFranchiseTransaction(11L, ManualPayoutStatus.FAILED, "bank rejected");
        verify(processor, never()).processSingleTransaction(anyLong(), any(), any());
    }

    @Test
    void noAccountType_orMerchant_behavesAsBefore() {
        when(processor.processSingleTransaction(anyLong(), any(), any())).thenAnswer(i -> ok(i.getArgument(0)));

        service.updatePayoutTransactionStatuses(request(null, 10L));
        service.updatePayoutTransactionStatuses(request("merchant", 11L));
        service.updatePayoutTransactionStatuses(request("  ", 12L));

        verify(processor, times(3)).processSingleTransaction(anyLong(), eq(ManualPayoutStatus.FAILED), eq("bank rejected"));
        verify(processor, never()).processSingleFranchiseTransaction(anyLong(), any(), any());
    }

    @Test
    void unknownAccountType_isRejected_beforeAnythingRuns() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updatePayoutTransactionStatuses(request("BANK", 10L)));

        verifyNoInteractions(processor);
    }

    @Test
    void oneFranchiseTransactionFailing_doesNotStopTheOthers() {
        when(processor.processSingleFranchiseTransaction(eq(10L), any(), any()))
                .thenThrow(new IllegalStateException("Only PENDING transactions can be manually updated."));
        when(processor.processSingleFranchiseTransaction(eq(11L), any(), any())).thenReturn(ok(11L));

        ManualPayoutStatusUpdateResponse r = service.updatePayoutTransactionStatuses(request("FRANCHISE", 10L, 11L));

        assertEquals(1, r.getProcessedSuccessfully());
        assertEquals(1, r.getFailedToProcess());
    }

    // ------------------------------------------------------------------ franchise report

    @Test
    void franchiseReport_listsPendingPayoutRowsInTheMerchantReportShape() {
        Franchise franchise = new Franchise();
        ReflectionTestUtils.setField(franchise, "franchiseName", "Sarthak communication");

        FranchiseTransactionDetails row = new FranchiseTransactionDetails();
        row.setFranchise(franchise);
        ReflectionTestUtils.setField(row, "transactionId", 77L);
        row.setService("PAYOUT");
        row.setTranStatus("PENDING");
        row.setActionOnBalance("DEBIT");
        row.setAmount(new BigDecimal("-1010.00"));
        row.setNetAmount(new BigDecimal("-1000.00"));
        row.setCharge(new BigDecimal("10.00"));
        row.setTransactionDate(LocalDateTime.of(2026, 10, 27, 12, 0));
        row.setBalBeforeTran(new BigDecimal("5000.00"));
        row.setBalAfterTran(new BigDecimal("3990.00"));
        row.setRemarks("Payout to Bene - 123456");

        when(franchiseRepo.findPayoutsByStatus(any(), any(), eq(3L), eq("PENDING"), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 50), 1));

        TransactionReportDTO.TransactionReportResponse<MerchantTransactionReportDTO> r =
                service.getFranchiseTransactionReport(
                        LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 28, 23, 59),
                        3L, " PENDING ", 0, 50);

        assertEquals(1, r.getTotalElements());
        MerchantTransactionReportDTO dto = r.getTransactions().get(0);
        assertEquals(77L, dto.getCustomTxnId());               // the franchise ledger id the update uses
        assertEquals("Sarthak communication", dto.getMerchantName());
        assertEquals("PENDING", dto.getState());
        assertEquals("PAYOUT", dto.getService());
        assertEquals(0, new BigDecimal("-1010.00").compareTo(dto.getTxnAmount()));
        assertEquals(0, new BigDecimal("3990.00").compareTo(dto.getBalAfterTran()));
        // status is trimmed, and a franchise id was passed through
        verify(franchiseRepo).findPayoutsByStatus(any(), any(), eq(3L), eq("PENDING"), any());
    }

    @Test
    void franchiseReport_allFranchises_whenNoIdIsGiven() {
        when(franchiseRepo.findPayoutsByStatus(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        service.getFranchiseTransactionReport(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 28, 23, 59), null, "PENDING", 0, 50);

        verify(franchiseRepo).findPayoutsByStatus(any(), any(), isNull(), eq("PENDING"), any());
    }

    @Test
    void franchiseReport_validatesTheDatesLikeTheMerchantReport() {
        assertThrows(IllegalArgumentException.class, () -> service.getFranchiseTransactionReport(
                LocalDateTime.of(2026, 10, 28, 0, 0), LocalDateTime.of(2026, 10, 1, 0, 0), null, "PENDING", 0, 50));
        assertThrows(IllegalArgumentException.class, () -> service.getFranchiseTransactionReport(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 28, 0, 0), null, " ", 0, 50));
        verifyNoInteractions(franchiseRepo);
    }
}
