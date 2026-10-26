package com.project2.ism.Service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payment.PaymentVendorCredentials;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The SuivrePay log lines must explain a failure, and must never contain secrets or card data. */
class SuivrePayClientLoggingTest {

    private HttpServer server;
    private SuivrePayClient client;
    private PaymentVendorCredentials creds;
    private CreditCardApiLogService apiLogService;
    private ListAppender<ILoggingEvent> appender;
    private int status = 404;
    private String responseBody = "<!DOCTYPE html><html><body>Page not found</body></html>";
    private String contentType = "text/html";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();

        PaymentVendor vendor = new PaymentVendor();
        vendor.setId(5L);
        PaymentVendorRepository vendorRepo = mock(PaymentVendorRepository.class);
        when(vendorRepo.findByVendorNameAndStatus("SuivrePay", true)).thenReturn(Optional.of(vendor));

        creds = new PaymentVendorCredentials();
        creds.setActiveEnvironment("PROD");
        creds.setBaseUrlProd("http://127.0.0.1:" + server.getAddress().getPort() + "/apipanel/apayout");
        creds.setUserIdProd("USER-SECRET-111");
        creds.setSaltKeyProd("PASS-SECRET-222");
        creds.setSecretKeyProd("sk_live_KEY-SECRET-333");
        creds.setEncryptDecryptKeyProd("AP503682");
        PaymentVendorCredentialsService credsService = mock(PaymentVendorCredentialsService.class);
        when(credsService.getActiveEnvironmentCredentials(5L)).thenReturn(creds);

        apiLogService = org.mockito.Mockito.mock(CreditCardApiLogService.class);
        client = new SuivrePayClient(vendorRepo, credsService, new ObjectMapper(), apiLogService);
        ReflectionTestUtils.setField(client, "vendorName", "SuivrePay");

        Logger logger = (Logger) LoggerFactory.getLogger(SuivrePayClient.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        ((Logger) LoggerFactory.getLogger(SuivrePayClient.class)).detachAppender(appender);
    }

    private String allLogs() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    @Test
    void html404_isExplained_inTheLog() {
        SuivrePayClient.VendorResponse r = client.post("/fetch_creditCard_billers.php", null);

        assertEquals(404, r.httpStatus());
        assertNull(r.body());
        String logs = allLogs();
        assertTrue(logs.contains("SuivrePay request"), logs);
        assertTrue(logs.contains("SuivrePay response"), logs);
        assertTrue(logs.contains("SuivrePay PROBLEM"), logs);
        assertTrue(logs.contains("does not exist on SuivrePay's server"), logs);
        assertTrue(logs.contains("/apipanel/apayout/fetch_creditCard_billers.php"), logs);
        assertTrue(logs.contains("Page not found"), "the response body should be shown: " + logs);
    }

    @Test
    void secretsAndCardData_areNeverLogged() {
        status = 401;
        contentType = "application/json";
        responseBody = "{\"status\":\"error\",\"message\":\"Authentication Failed: Missing headers.\"}";

        client.post("/fetch_cc_bill.php", Map.of(
                "MID", "AP503682",
                "customer_pan", "ABCDE1234F",
                "input_params", List.of(
                        Map.of("name", "Last 4 digits of Primary Credit Card Number", "value", "3044"),
                        Map.of("name", "Registered Mobile Number", "value", "9250907790"))));

        String logs = allLogs();
        assertFalse(logs.contains("USER-SECRET-111"), logs);
        assertFalse(logs.contains("PASS-SECRET-222"), logs);
        assertFalse(logs.contains("KEY-SECRET-333"), logs);
        assertFalse(logs.contains("9250907790"), "mobile must be masked: " + logs);
        assertFalse(logs.contains("ABCDE1234F"), "PAN must be masked: " + logs);
        assertFalse(logs.contains("\"3044\""), "card digits must be masked: " + logs);
        assertTrue(logs.contains("92******90"), logs);
        assertTrue(logs.contains("AP503682"), "the MID is not secret and helps debugging: " + logs);
        assertTrue(logs.contains("X-Auth-User / X-Auth-Pass / X-Auth-Key missing or wrong"), logs);
    }

    @Test
    void doubleSlashesInTheBaseUrl_areCollapsed() {
        creds.setBaseUrlProd("http://127.0.0.1:" + server.getAddress().getPort() + "//apipanel//billavenue/");

        client.post("/fetch_creditCard_billers", null);

        String logs = allLogs();
        assertTrue(logs.contains("POST http://127.0.0.1:" + server.getAddress().getPort()
                + "/apipanel/billavenue/fetch_creditCard_billers "), logs);
    }

    @Test
    void aFullUrlPath_isUsedAsItIs_ignoringTheBaseUrl() {
        client.post("http://127.0.0.1:" + server.getAddress().getPort() + "/apipanel/apayout/raise_complaint.php", null);

        String logs = allLogs();
        assertTrue(logs.contains("/apipanel/apayout/raise_complaint.php "), logs);
        assertFalse(logs.contains("billavenue"), logs);
    }

    // ---------------- admin "Credit Card Logs" rows ----------------

    private com.project2.ism.Model.CreditCard.CreditCardApiLog savedLog() {
        org.mockito.ArgumentCaptor<com.project2.ism.Model.CreditCard.CreditCardApiLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.project2.ism.Model.CreditCard.CreditCardApiLog.class);
        org.mockito.Mockito.verify(apiLogService).record(captor.capture());
        return captor.getValue();
    }

    private static SuivrePayClient.CallContext ctx(String action) {
        return new SuivrePayClient.CallContext(action, 7L, "IDFC00000NATFQ", "3044", null, null);
    }

    @Test
    void successfulCall_savesALogRow_withWhoWhatAndMaskedData() {
        status = 200;
        contentType = "application/json";
        responseBody = "{\"status\":\"success\",\"response_code\":\"000\",\"data\":{\"request_id\":\"SPREQ1\","
                + "\"customer_name\":\"RAJESH KUMAR\",\"customer_mobile\":\"9250907790\",\"amount\":5423.56}}";

        client.post("/fetch_cc_bill", Map.of("MID", "AP503682", "biller_id", "IDFC00000NATFQ",
                "input_params", List.of(Map.of("name", "Registered Mobile Number", "value", "9250907790"))),
                ctx("FETCH_BILL"));

        com.project2.ism.Model.CreditCard.CreditCardApiLog row = savedLog();
        assertEquals("FETCH_BILL", row.getAction());
        assertEquals(7L, row.getMerchantId());
        assertEquals("IDFC00000NATFQ", row.getBillerId());
        assertEquals("3044", row.getCardLast4());
        assertEquals("SPREQ1", row.getRequestId(), "request id is taken from the response");
        assertEquals("SUCCESS", row.getOutcome());
        assertEquals(200, row.getHttpStatus());
        assertEquals("success", row.getVendorStatus());
        assertEquals("000", row.getVendorResponseCode());
        assertTrue(row.getEndpointUrl().endsWith("/apipanel/apayout/fetch_cc_bill"), row.getEndpointUrl());
        assertNotNull(row.getTimeTakenMs());
        assertFalse(row.getRequestPayload().contains("9250907790"), row.getRequestPayload());
        assertFalse(row.getResponsePayload().contains("9250907790"), row.getResponsePayload());
        assertTrue(row.getResponsePayload().contains("RAJESH KUMAR"), "the name is kept for audit");
        String everything = row.getRequestPayload() + row.getResponsePayload() + row.getEndpointUrl();
        assertFalse(everything.contains("KEY-SECRET-333") || everything.contains("PASS-SECRET-222")
                || everything.contains("USER-SECRET-111"), "auth values must never be stored");
    }

    @Test
    void refusedCall_savesTheVendorErrorCodeAndMessage() {
        status = 200;
        contentType = "application/json";
        responseBody = "{\"status\":\"error\",\"response_code\":\"001\",\"error_code\":\"ERR033\","
                + "\"message\":\"Too many request for that Biller\"}";

        client.post("/fetch_cc_bill", Map.of("MID", "AP503682"), ctx("FETCH_BILL"));

        com.project2.ism.Model.CreditCard.CreditCardApiLog row = savedLog();
        assertEquals("REFUSED", row.getOutcome());
        assertEquals("ERR033", row.getVendorErrorCode());
        assertEquals("Too many request for that Biller", row.getVendorMessage());
    }

    @Test
    void payCall_savesRequestIdAndAmountFromTheRequest() {
        status = 200;
        contentType = "application/json";
        responseBody = "{\"status\":\"success\",\"response_code\":\"000\",\"data\":{}}";

        client.post("/pay_cc_bill", Map.of("MID", "AP503682", "amount", 1000.5, "request_id", "SPPAY1"),
                ctx("PAY_BILL"));

        com.project2.ism.Model.CreditCard.CreditCardApiLog row = savedLog();
        assertEquals("PAY_BILL", row.getAction());
        assertEquals("SPPAY1", row.getRequestId());
        assertEquals(new java.math.BigDecimal("1000.5"), row.getAmount());
    }

    @Test
    void noResponse_isLogged_asNoResponse_withTheReason() {
        server.stop(0);

        assertThrows(SuivrePayClient.VendorUnreachableException.class,
                () -> client.post("/pay_cc_bill", Map.of("MID", "AP503682"), ctx("PAY_BILL")));

        com.project2.ism.Model.CreditCard.CreditCardApiLog row = savedLog();
        assertEquals("NO_RESPONSE", row.getOutcome());
        assertNull(row.getHttpStatus());
        assertNotNull(row.getErrorMessage());
    }

    @Test
    void aProblemSavingTheLog_neverBreaksTheCall() {
        status = 200;
        contentType = "application/json";
        responseBody = "{\"status\":\"success\",\"data\":[]}";
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(apiLogService)
                .record(org.mockito.ArgumentMatchers.any());

        SuivrePayClient.VendorResponse r = client.post("/fetch_creditCard_billers", null, ctx("FETCH_BILLERS"));

        assertEquals(200, r.httpStatus());
    }

    @Test
    void connectionFailure_isExplained_andThrows() {
        server.stop(0);

        assertThrows(SuivrePayClient.VendorUnreachableException.class,
                () -> client.post("/fetch_cc_bill.php", null));

        String logs = allLogs();
        assertTrue(logs.contains("no usable response"), logs);
        assertTrue(logs.contains("hint="), logs);
    }

    @Test
    void goodResponse_logsOnlyStatus_notTheBody() {
        status = 200;
        contentType = "application/json";
        responseBody = "{\"status\":\"success\",\"data\":[{\"customer_name\":\"RAJESH KUMAR\"}]}";

        SuivrePayClient.VendorResponse r = client.post("/fetch_creditCard_billers.php", null);

        assertEquals(200, r.httpStatus());
        String logs = allLogs();
        assertTrue(logs.contains("vendorStatus=success"), logs);
        assertFalse(logs.contains("RAJESH"), "customer data must not be logged on success: " + logs);
        assertFalse(logs.contains("PROBLEM"), logs);
    }
}
