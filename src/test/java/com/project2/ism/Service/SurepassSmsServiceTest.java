package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.VendorCredentialDTO;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.Sms.SmsLog;
import com.project2.ism.Model.Sms.SmsTemplate;
import com.project2.ism.Repository.BbpsTransactionRepository;
import com.project2.ism.Repository.CreditCardBillTransactionRepository;
import com.project2.ism.Repository.SmsLogRepository;
import com.project2.ism.Repository.SmsTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bill Payment Success through Surepass: off by default, only sends when the template is ready,
 * fills the six variables, logs every attempt with the number masked, never sends twice and never throws.
 */
class SurepassSmsServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private SmsTemplateRepository templates;
    private SmsLogRepository logs;
    private PaymentVendorCredentialsService credentials;
    private RestTemplate http;

    @BeforeEach
    void setUp() throws Exception {
        templates = mock(SmsTemplateRepository.class);
        logs = mock(SmsLogRepository.class);
        credentials = mock(PaymentVendorCredentialsService.class);
        http = mock(RestTemplate.class);

        VendorCredentialDTO vendor = new VendorCredentialDTO();
        vendor.setBaseUrl("https://surepass.example/");
        vendor.setToken("token-123");
        when(credentials.getSurepassCredentials()).thenReturn(vendor);
        when(http.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(mapper.readTree("{\"success\":true,\"status_code\":200}")));
    }

    private SurepassSmsService service(boolean enabled) {
        return new SurepassSmsService(templates, logs, credentials, enabled, "/api/v1/sms/send-sms", http);
    }

    private void readyTemplate() {
        SmsTemplate t = new SmsTemplate();
        t.setTemplateKey(SurepassSmsService.BILL_AVENUE_PAYMENT);
        t.setSenderId("SMCRSO");
        t.setTemplateId("1777179568172116977");
        t.setEnabled(true);
        when(templates.findByTemplateKey(SurepassSmsService.BILL_AVENUE_PAYMENT)).thenReturn(Optional.of(t));
    }

    private boolean pay(SurepassSmsService s) {
        return s.sendPaymentSuccess(SurepassSmsService.BILL_AVENUE_PAYMENT, "+91 98220 11111", new BigDecimal("1500"), "ICICI Credit card",
                "XXXX4242", "CC01TXN", LocalDateTime.of(2026, 11, 26, 15, 30), "WALLET", "BBPS:CC01TXN");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsTheRegisteredTemplate_withSixVariables_toTheTenDigitNumber() {
        readyTemplate();

        assertTrue(pay(service(true)));

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).exchange(url.capture(), eq(HttpMethod.POST), entity.capture(), eq(JsonNode.class));
        assertEquals("https://surepass.example/api/v1/sms/send-sms", url.getValue());
        assertEquals("Bearer token-123", entity.getValue().getHeaders().getFirst("Authorization"));

        Map<String, Object> body = (Map<String, Object>) entity.getValue().getBody();
        assertEquals("1777179568172116977", body.get("template_id"));
        assertEquals("SMCRSO", body.get("sender_id"));
        assertEquals("9822011111", body.get("mobile"));
        Map<String, String> vars = (Map<String, String>) body.get("variables");
        assertEquals(List.of("1500.00", "ICICI Credit card", "XXXX4242", "CC01TXN", "26-11-2026 15:30", "WALLET"),
                List.copyOf(vars.values()));
        assertEquals(List.of("var1", "var2", "var3", "var4", "var5", "var6"), List.copyOf(vars.keySet()));
    }

    @Test
    void logsTheAttempt_withTheNumberMasked() {
        readyTemplate();
        pay(service(true));

        ArgumentCaptor<SmsLog> row = ArgumentCaptor.forClass(SmsLog.class);
        verify(logs).save(row.capture());
        assertEquals(SmsLog.SENT, row.getValue().getStatus());
        assertEquals("BBPS:CC01TXN", row.getValue().getRefId());
        assertEquals("******1111", row.getValue().getMobileMasked());
    }

    @Test
    void doesNothing_whenTheMasterSwitchIsOff() {
        readyTemplate();

        assertFalse(pay(service(false)));
        assertFalse(service(false).isActive(SurepassSmsService.BILL_AVENUE_PAYMENT));
        verifyNoInteractions(http);
    }

    @Test
    void doesNothing_whenTheTemplateIsSwitchedOff_orHasNoTemplateId_orIsMissing() {
        SmsTemplate t = new SmsTemplate();
        t.setTemplateKey(SurepassSmsService.BILL_AVENUE_PAYMENT);
        t.setSenderId("SMCRSO");
        when(templates.findByTemplateKey(SurepassSmsService.BILL_AVENUE_PAYMENT)).thenReturn(Optional.of(t));
        assertFalse(pay(service(true)));                       // off, no id

        t.setEnabled(true);
        assertFalse(pay(service(true)));                       // on, but no template id yet
        assertFalse(service(true).isActive(SurepassSmsService.BILL_AVENUE_PAYMENT));

        when(templates.findByTemplateKey(SurepassSmsService.BILL_AVENUE_PAYMENT)).thenReturn(Optional.empty());
        assertFalse(pay(service(true)));                       // no row at all
        verifyNoInteractions(http);
    }

    @Test
    void isActive_onlyWhenSwitchOn_andRowEnabled_andTemplateIdSet() {
        readyTemplate();
        assertTrue(service(true).isActive(SurepassSmsService.BILL_AVENUE_PAYMENT));
    }

    @Test
    void refusesBadNumbers_withoutCallingSurepass() {
        readyTemplate();
        SurepassSmsService s = service(true);

        assertFalse(s.send(SurepassSmsService.BILL_AVENUE_PAYMENT, "12345", List.of("a"), "R1"));
        assertFalse(s.send(SurepassSmsService.BILL_AVENUE_PAYMENT, null, List.of("a"), "R1"));
        verifyNoInteractions(http);
    }

    @Test
    void sendsTheSameMessageOnlyOnce() {
        readyTemplate();
        when(logs.existsByTemplateKeyAndRefIdAndStatus(SurepassSmsService.BILL_AVENUE_PAYMENT, "BBPS:CC01TXN", SmsLog.SENT))
                .thenReturn(true);

        assertFalse(pay(service(true)));
        verifyNoInteractions(http);
    }

    @Test
    void aRefusalIsLoggedAsFailed_andAnErrorIsSwallowed() throws Exception {
        readyTemplate();
        when(http.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(mapper.readTree("{\"success\":false,\"message\":\"bad template\"}")));
        assertFalse(pay(service(true)));
        ArgumentCaptor<SmsLog> row = ArgumentCaptor.forClass(SmsLog.class);
        verify(logs).save(row.capture());
        assertEquals(SmsLog.FAILED, row.getValue().getStatus());

        when(http.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("timeout"));
        assertDoesNotThrow(() -> pay(service(true)));
        assertFalse(pay(service(true)));
    }

    @Test
    void missingSurepassCredentials_isReportedNotThrown() {
        readyTemplate();
        when(credentials.getSurepassCredentials()).thenThrow(new RuntimeException("SUREPASS vendor not found"));

        assertDoesNotThrow(() -> pay(service(true)));
        assertFalse(pay(service(true)));
        verifyNoInteractions(http);
    }

    @Test
    void variableValues_areCleanedAndCutToThirtyCharacters() {
        assertEquals("-", SurepassSmsService.value(null));
        assertEquals("-", SurepassSmsService.value("   "));
        assertEquals("a b c", SurepassSmsService.value("  a \n b\t c "));
        assertEquals(30, SurepassSmsService.value("x".repeat(80)).length());
    }

    // ---- the two payment channels use it ----

    @Test
    void billAvenuePayment_usesSurepassWhenActive_andTheOldSenderOtherwise() {
        BbpsTransactionRepository repo = mock(BbpsTransactionRepository.class);
        SmsService fast2sms = mock(SmsService.class);
        SurepassSmsService surepass = mock(SurepassSmsService.class);
        BbpsTransaction t = new BbpsTransaction();
        t.setTxnRefId("CC01TXN");
        t.setCustomerMobile("9822011111");
        t.setBillerName("ICICI Credit card");
        t.setConsumerNumber("4242");
        t.setAmount(new BigDecimal("1500"));
        t.setPaymentMode("WALLET");
        when(repo.findByTxnRefId("CC01TXN")).thenReturn(Optional.of(t));
        BbpsNotificationService service = new BbpsNotificationService(repo, fast2sms, surepass);

        when(surepass.isEnabled(SurepassSmsService.BILL_AVENUE_PAYMENT)).thenReturn(true);
        when(surepass.isActive(SurepassSmsService.BILL_AVENUE_PAYMENT)).thenReturn(true);
        service.sendPaymentSuccess("CC01TXN");
        verify(surepass).sendPaymentSuccess(eq(SurepassSmsService.BILL_AVENUE_PAYMENT), eq("9822011111"), eq(new BigDecimal("1500")), eq("ICICI Credit card"),
                eq("4242"), eq("CC01TXN"), any(), eq("WALLET"), eq("BBPS:CC01TXN"));
        verifyNoInteractions(fast2sms);

        when(surepass.isActive(SurepassSmsService.BILL_AVENUE_PAYMENT)).thenReturn(false);
        service.sendPaymentSuccess("CC01TXN");
        verify(fast2sms).send(eq("9822011111"), contains("CC01TXN"));
    }

    @Test
    void creditCardPayment_sendsTheCardAsXXXXLast4_andOnlyForASuccessfulPayment() {
        CreditCardBillTransactionRepository repo = mock(CreditCardBillTransactionRepository.class);
        SurepassSmsService surepass = mock(SurepassSmsService.class);
        when(surepass.isActive(SurepassSmsService.SUIVREPAY_PAYMENT)).thenReturn(true);
        CreditCardBillTransaction t = new CreditCardBillTransaction();
        t.setCustomerMobile("9822011111");
        t.setBillerName("HDFC Credit Card");
        t.setCardLast4("1234");
        t.setBillAmount(new BigDecimal("2000"));
        t.setPaidAmount(new BigDecimal("1500"));
        t.setRequestId("REQ-1");
        t.setApiRefNumber("API-77");
        t.setStatus(CreditCardBillTransaction.Status.SUCCESS);
        when(repo.findById(57L)).thenReturn(Optional.of(t));
        CreditCardNotificationService service = new CreditCardNotificationService(repo, surepass);

        service.sendPaymentSuccess(57L);
        verify(surepass).sendPaymentSuccess(eq(SurepassSmsService.SUIVREPAY_PAYMENT), eq("9822011111"), eq(new BigDecimal("1500")), eq("HDFC Credit Card"),
                eq("XXXX1234"), eq("API-77"), any(), eq("WALLET"), eq("CC:57"));

        t.setStatus(CreditCardBillTransaction.Status.FAILED);
        clearInvocations(surepass);
        service.sendPaymentSuccess(57L);
        verify(surepass, never()).sendPaymentSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());

        when(repo.findById(99L)).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.sendPaymentSuccess(99L));
        assertDoesNotThrow(() -> service.sendPaymentSuccess(null));
    }

    // ---- the admin's on/off switch ----

    private BbpsTransactionRepository oneBillAvenuePayment() {
        BbpsTransactionRepository repo = mock(BbpsTransactionRepository.class);
        BbpsTransaction t = new BbpsTransaction();
        t.setTxnRefId("CC01TXN");
        t.setCustomerMobile("9822011111");
        t.setAmount(new BigDecimal("1500"));
        when(repo.findByTxnRefId("CC01TXN")).thenReturn(Optional.of(t));
        return repo;
    }

    @Test
    void adminSwitchOff_stopsEverySender_forPaymentAndComplaint() {
        SmsService fast2sms = mock(SmsService.class);
        SurepassSmsService surepass = mock(SurepassSmsService.class);
        BbpsNotificationService service = new BbpsNotificationService(oneBillAvenuePayment(), fast2sms, surepass);
        when(surepass.isEnabled(anyString())).thenReturn(false);
        when(surepass.isActive(anyString())).thenReturn(true);

        service.sendPaymentSuccess("CC01TXN");
        service.sendComplaintRegistered("CC01TXN", "CMP9");

        verifyNoInteractions(fast2sms);
        verify(surepass, never()).sendPaymentSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void oneServiceOff_doesNotSwitchOffTheOthers() {
        SmsService fast2sms = mock(SmsService.class);
        SurepassSmsService surepass = mock(SurepassSmsService.class);
        BbpsNotificationService service = new BbpsNotificationService(oneBillAvenuePayment(), fast2sms, surepass);
        when(surepass.isEnabled(SurepassSmsService.BILL_AVENUE_PAYMENT)).thenReturn(false);
        when(surepass.isEnabled(SurepassSmsService.BILL_AVENUE_COMPLAINT)).thenReturn(true);

        service.sendPaymentSuccess("CC01TXN");
        verifyNoInteractions(fast2sms);

        service.sendComplaintRegistered("CC01TXN", "CMP9");
        verify(fast2sms).send(eq("9822011111"), contains("CMP9"));
    }

    @Test
    void isEnabled_isOnWhenThereIsNoRow_orTheReadFails_andOffOnlyWhenAnAdminSwitchedItOff() {
        SmsTemplate t = new SmsTemplate();
        t.setTemplateKey(SurepassSmsService.BILL_AVENUE_PAYMENT);
        t.setEnabled(false);
        when(templates.findByTemplateKey(SurepassSmsService.BILL_AVENUE_PAYMENT)).thenReturn(Optional.of(t));
        assertFalse(service(true).isEnabled(SurepassSmsService.BILL_AVENUE_PAYMENT));

        t.setEnabled(true);
        assertTrue(service(true).isEnabled(SurepassSmsService.BILL_AVENUE_PAYMENT));

        assertTrue(service(true).isEnabled("NO_SUCH_ROW"));

        when(templates.findByTemplateKey("BROKEN")).thenThrow(new RuntimeException("db down"));
        assertTrue(service(true).isEnabled("BROKEN"));
    }

    @Test
    void seedingCreatesTheThreeRowsOn_andKeepsAnAdminsChoiceWhenRunAgain() {
        SurepassSmsService s = service(true);
        when(templates.findByTemplateKey(anyString())).thenReturn(Optional.empty());
        s.seedTemplates();
        ArgumentCaptor<SmsTemplate> rows = ArgumentCaptor.forClass(SmsTemplate.class);
        verify(templates, times(3)).save(rows.capture());
        assertEquals(List.of(SurepassSmsService.BILL_AVENUE_PAYMENT, SurepassSmsService.SUIVREPAY_PAYMENT,
                        SurepassSmsService.BILL_AVENUE_COMPLAINT),
                rows.getAllValues().stream().map(SmsTemplate::getTemplateKey).toList());
        assertTrue(rows.getAllValues().stream().allMatch(SmsTemplate::isEnabled));
        assertTrue(rows.getAllValues().stream().allMatch(r -> r.getTemplateId() == null));

        SmsTemplate off = new SmsTemplate();
        off.setTemplateKey(SurepassSmsService.BILL_AVENUE_PAYMENT);
        off.setLabel("x");
        off.setEnabled(false);
        reset(templates);
        when(templates.findByTemplateKey(anyString())).thenReturn(Optional.of(off));
        s.seedTemplates();
        verify(templates, never()).save(any());
        assertFalse(off.isEnabled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void complaintSms_sendsTheTxnIdAndTheComplaintId_asTwoVariables() {
        SmsTemplate t = new SmsTemplate();
        t.setTemplateKey(SurepassSmsService.BILL_AVENUE_COMPLAINT);
        t.setSenderId("SMCRSO");
        t.setTemplateId("1777000000000000001");
        t.setEnabled(true);
        when(templates.findByTemplateKey(SurepassSmsService.BILL_AVENUE_COMPLAINT)).thenReturn(Optional.of(t));

        assertTrue(service(true).sendComplaintRegistered("9822011111", "CC01TXN", "CMP9", "BBPS-COMPLAINT:CMP9"));

        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).exchange(anyString(), eq(HttpMethod.POST), entity.capture(), eq(JsonNode.class));
        Map<String, Object> body = (Map<String, Object>) entity.getValue().getBody();
        assertEquals("1777000000000000001", body.get("template_id"));
        assertEquals(Map.of("var1", "CC01TXN", "var2", "CMP9"), body.get("variables"));
    }

    @Test
    void complaint_usesSurepassWhenReady_andTheOldSenderOtherwise() {
        SmsService fast2sms = mock(SmsService.class);
        SurepassSmsService surepass = mock(SurepassSmsService.class);
        BbpsNotificationService service = new BbpsNotificationService(oneBillAvenuePayment(), fast2sms, surepass);
        when(surepass.isEnabled(SurepassSmsService.BILL_AVENUE_COMPLAINT)).thenReturn(true);

        when(surepass.isActive(SurepassSmsService.BILL_AVENUE_COMPLAINT)).thenReturn(true);
        service.sendComplaintRegistered("CC01TXN", "CMP9");
        verify(surepass).sendComplaintRegistered("9822011111", "CC01TXN", "CMP9", "BBPS-COMPLAINT:CMP9");
        verifyNoInteractions(fast2sms);

        when(surepass.isActive(SurepassSmsService.BILL_AVENUE_COMPLAINT)).thenReturn(false);
        service.sendComplaintRegistered("CC01TXN", "CMP9");
        verify(fast2sms).send(eq("9822011111"), contains("CMP9"));
    }
}
