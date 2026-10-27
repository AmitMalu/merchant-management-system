package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.PayoutDTO.PayoutRequest;
import com.project2.ism.DTO.PayoutDTO.PayoutResult;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import com.project2.ism.Repository.FranchiseWalletRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.Repository.MerchantWalletRepository;
import com.project2.ism.Repository.PaymentVendorResponseLogRepository;
import com.project2.ism.Repository.PayoutBankRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import com.project2.ism.Repository.VendorBankRepository;
import com.project2.ism.Repository.VendorStateRepository;
import com.project2.ism.Service.Monitoring.AlertService;
import com.project2.ism.Service.Monitoring.TransactionEventService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** With the Payout switch off, a payout is refused before any wallet is touched or the vendor is called. */
class PayoutSwitchTest {

    @Test
    void payoutSwitchOff_returnsAFailedResult_andNothingMoves() {
        MerchantWalletRepository merchantWallets = mock(MerchantWalletRepository.class);
        FranchiseWalletRepository franchiseWallets = mock(FranchiseWalletRepository.class);
        MerchantTransDetRepository merchantLedger = mock(MerchantTransDetRepository.class);
        FranchiseTransDetRepository franchiseLedger = mock(FranchiseTransDetRepository.class);
        PayoutTransactionRepository payoutTxns = mock(PayoutTransactionRepository.class);
        VimoPayClientService vendor = mock(VimoPayClientService.class);
        FeatureAccessService access = mock(FeatureAccessService.class);
        doThrow(new IllegalStateException("Payout is not enabled for your account. Please contact the administrator."))
                .when(access).requirePayoutEnabled(any());

        PayoutService service = new PayoutService(payoutTxns, merchantWallets, franchiseWallets, merchantLedger,
                franchiseLedger, mock(MerchantRepository.class), mock(FranchiseRepository.class), vendor,
                mock(PaymentChargeService.class), mock(PayoutBankRepository.class), mock(VendorStateRepository.class),
                mock(VendorBankRepository.class), mock(PaymentVendorResponseLogRepository.class), new ObjectMapper(),
                merchantLedger, mock(TransactionEventService.class), mock(AlertService.class), access);

        PayoutRequest request = new PayoutRequest();
        request.setInitiatorType("FRANCHISE");
        request.setInitiatorId(3L);
        request.setAmount(new BigDecimal("1000"));
        request.setBeneficiaryName("RAMESH KUMAR");

        PayoutResult result = service.initiatePayout(request, 1L);

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("Payout is not enabled"), result.getMessage());
        verifyNoInteractions(merchantWallets, franchiseWallets, merchantLedger, franchiseLedger, payoutTxns, vendor);
    }
}
