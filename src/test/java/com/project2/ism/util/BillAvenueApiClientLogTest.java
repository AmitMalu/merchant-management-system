package com.project2.ism.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import com.project2.ism.Service.PaymentVendorCredentialsService;
import com.project2.ism.WebConfig.BillAvenuceProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The vendor log columns are TEXT (about 64 KB). The full biller list is far bigger, so what is logged
 * must be shortened - otherwise the log insert fails and can roll back the whole biller sync.
 */
class BillAvenueApiClientLogTest {

    @Test
    void aHugeAnswer_isShortenedInTheLog_andASmallOneIsKept() {
        VendorResponseLogWriter repo = mock(VendorResponseLogWriter.class);
        BillAvenueApiClient client = new BillAvenueApiClient(mock(RestTemplate.class),
                mock(PaymentVendorCredentialsService.class), mock(BillAvenuceProperties.class), repo,
                mock(com.project2.ism.Service.BillAvenueCreditCardLogService.class), new ObjectMapper());

        String huge = "x".repeat(3_000_000);
        client.saveLog(7L, "BILLER_INFO", "{}", "enc", huge, 200, huge);
        client.saveLog(7L, "BILL_PAYMENT", "{\"a\":1}", "enc", "small", 200, "{\"ok\":true}");

        ArgumentCaptor<PaymentVendorResponseLog> saved = ArgumentCaptor.forClass(PaymentVendorResponseLog.class);
        verify(repo, times(2)).write(saved.capture());

        PaymentVendorResponseLog big = saved.getAllValues().get(0);
        assertTrue(big.getDecryptedResponse().length() < 20_000);
        assertTrue(big.getEncryptedResponse().length() < 20_000);
        assertTrue(big.getDecryptedResponse().contains("shortened"));

        PaymentVendorResponseLog small = saved.getAllValues().get(1);
        assertEquals("{\"ok\":true}", small.getDecryptedResponse());
        assertEquals("small", small.getEncryptedResponse());
    }
}
