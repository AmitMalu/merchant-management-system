package com.project2.ism.DTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The website sends merchantId with a bill payment. The server must read it (the ownership check
 * depends on it) and must never pass it on to Bill Avenue.
 */
class BillPaymentRequestJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void merchantIdFromTheWebsite_isRead() throws Exception {
        BillPaymentRequest request = mapper.readValue(
                "{\"merchantId\":42,\"billerId\":\"ICIC00000NATSI\",\"amountInfo\":{\"amount\":100,\"currency\":\"356\",\"custConvFee\":\"0\"}}",
                BillPaymentRequest.class);

        assertEquals(42L, request.getMerchantId());
        assertEquals("ICIC00000NATSI", request.getBillerId());
    }

    @Test
    void merchantId_isNeverWrittenOut_soItDoesNotGoToBillAvenue() {
        BillPaymentRequest request = new BillPaymentRequest();
        request.setMerchantId(42L);
        request.setBillerId("ICIC00000NATSI");

        JsonNode sent = mapper.valueToTree(request);

        assertFalse(sent.has("merchantId"), sent.toString());
        assertEquals("ICIC00000NATSI", sent.get("billerId").asText());
    }
}
