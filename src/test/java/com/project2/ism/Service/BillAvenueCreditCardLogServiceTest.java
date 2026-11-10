package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Model.CreditCard.CreditCardApiLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Every Bill Avenue call lands in the admin CCBP log, with the sensitive values masked.
 */
class BillAvenueCreditCardLogServiceTest {

    private CreditCardApiLogService sink;
    private CallerIdentityService identity;
    private BillAvenueCreditCardLogService service;

    private static final String PAY_REQUEST = "{\"agentId\":\"CC01RS86AGTBBN885495\",\"billerId\":\"ICIC00000NATSI\","
            + "\"customerInfo\":{\"customerMobile\":\"8070476666\",\"customerEmail\":\"a@b.com\",\"customerPan\":\"ABCDE1234F\"},"
            + "\"inputParams\":{\"input\":[{\"paramName\":\"Last 4 digits of Credit Card Number\",\"paramValue\":\"1448\"},"
            + "{\"paramName\":\"Registered Mobile Number\",\"paramValue\":\"8070476666\"}]},"
            + "\"amountInfo\":{\"amount\":\"500\",\"currency\":\"356\",\"custConvFee\":\"0\"}}";

    @BeforeEach
    void setUp() {
        sink = mock(CreditCardApiLogService.class);
        identity = mock(CallerIdentityService.class);
        service = new BillAvenueCreditCardLogService(sink, identity, new ObjectMapper());
    }

    private CreditCardApiLog logged() {
        ArgumentCaptor<CreditCardApiLog> captor = ArgumentCaptor.forClass(CreditCardApiLog.class);
        verify(sink).record(captor.capture());
        return captor.getValue();
    }

    @Test
    void aSuccessfulPayment_isLoggedWithMerchantBillerCardAmountAndOutcome() {
        when(identity.currentMerchantIdOrNull()).thenReturn(5L);

        service.record("BILL_PAYMENT", "REQ123", PAY_REQUEST, 200,
                "{\"responseCode\":\"000\",\"responseReason\":\"Successful\",\"txnRefId\":\"CC01X\"}", 640L);

        CreditCardApiLog row = logged();
        assertEquals("BILLAVENUE", row.getVendor());
        assertEquals("PAY_BILL", row.getAction());
        assertEquals("SUCCESS", row.getOutcome());
        assertEquals(5L, row.getMerchantId());
        assertEquals("MERCHANT", row.getCustomerType());
        assertEquals("ICIC00000NATSI", row.getBillerId());
        assertEquals("1448", row.getCardLast4());
        assertEquals(new BigDecimal("5.00"), row.getAmount());      // 500 paise
        assertEquals("REQ123", row.getRequestId());
        assertEquals("000", row.getVendorResponseCode());
        assertEquals("Successful", row.getVendorMessage());
        assertEquals(640L, row.getTimeTakenMs());
    }

    @Test
    void sensitiveValuesAreMaskedInWhatIsStored() {
        service.record("BILL_PAYMENT", "REQ123", PAY_REQUEST, 200, "{\"responseCode\":\"000\"}", 1L);

        String stored = logged().getRequestPayload();
        assertFalse(stored.contains("8070476666"), stored);
        assertFalse(stored.contains("ABCDE1234F"), stored);
        assertFalse(stored.contains("a@b.com"), stored);
        assertFalse(stored.contains("\"paramValue\":\"1448\""), stored);
        assertTrue(stored.contains("ICIC00000NATSI"));              // the rest stays readable
    }

    @Test
    void aRefusal_isLoggedWithTheReason_inAnyOfBillAvenuesErrorShapes() {
        service.record("BILL_PAYMENT", "R", PAY_REQUEST, 200,
                "{\"responseCode\":\"001\",\"vErrorRootVO\":{\"error\":[{\"errorCode\":\"E077\",\"errorMessage\":\"bad mode\"},"
                        + "{\"errorCode\":\"E044\",\"errorMessage\":\"invalid category\"}]}}", 1L);

        CreditCardApiLog row = logged();
        assertEquals("REFUSED", row.getOutcome());
        assertEquals("E077", row.getVendorErrorCode());
        assertEquals("[E077] bad mode | [E044] invalid category", row.getVendorMessage());
    }

    @Test
    void anErrorTextCarryingBillAvenuesJson_isStillARefusal_notNoResponse() {
        service.record("BILLER_INFO", "R", "{\"billerId\":[]}", 200,
                "Received JSON error response from Bill Avenue: {\"responseCode\":\"205\",\"biller\":[],"
                        + "\"errorInfo\":[{\"error\":{\"errorCode\":\"DE001\",\"errorMessage\":\"Invalid ENC request\"}}]}", 1L);

        CreditCardApiLog row = logged();
        assertEquals("REFUSED", row.getOutcome());
        assertEquals("FETCH_BILLERS", row.getAction());
        assertEquals("DE001", row.getVendorErrorCode());
    }

    @Test
    void noAnswerAtAll_isLoggedAsNoResponse() {
        service.record("BILL_PAYMENT", "R", PAY_REQUEST, 500, "I/O error on POST request: Read timed out", 8000L);

        CreditCardApiLog row = logged();
        assertEquals("NO_RESPONSE", row.getOutcome());
        assertTrue(row.getErrorMessage().contains("timed out"));
    }

    @Test
    void everyBillAvenueCall_hasItsOwnAction_andACallWithoutAMerchantIsMarkedSystem() {
        assertEquals("FETCH_BILL", BillAvenueCreditCardLogService.actionFor("Bill Fetch Details"));
        assertEquals("FETCH_BILL", BillAvenueCreditCardLogService.actionFor("BILL_FETCH"));
        assertEquals("RAISE_COMPLAINT", BillAvenueCreditCardLogService.actionFor("COMPLAINT_REGISTER"));
        assertEquals("COMPLAINT_STATUS", BillAvenueCreditCardLogService.actionFor("COMPLAINT_TRACK"));
        assertEquals("TXN_STATUS", BillAvenueCreditCardLogService.actionFor("TRANSACTION_STATUS"));

        when(identity.currentMerchantIdOrNull()).thenReturn(null);
        service.record("BILLER_INFO", null, "{\"billerId\":[\"X\"]}", 200, "{\"responseCode\":\"000\"}", 1L);
        assertEquals("SYSTEM", logged().getCustomerType());
        assertNull(logged().getMerchantId());
    }

    @Test
    void aProblemWhileLogging_neverEscapes() {
        doThrow(new RuntimeException("db down")).when(sink).record(any());
        assertDoesNotThrow(() -> service.record("BILL_PAYMENT", "R", PAY_REQUEST, 200, "{\"responseCode\":\"000\"}", 1L));
        assertDoesNotThrow(() -> service.record(null, null, null, null, null, null));
    }
}
