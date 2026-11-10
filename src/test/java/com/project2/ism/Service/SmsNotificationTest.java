package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Repository.BbpsTransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The SMS goes to the customer mobile saved with the payment, from the server, and never breaks a payment.
 */
class SmsNotificationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private RestTemplate answering(String json) throws Exception {
        RestTemplate rt = mock(RestTemplate.class);
        when(rt.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(mapper.readTree(json)));
        return rt;
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsToTheCustomersNumber_withTheKeyInAHeader_notInTheUrl() throws Exception {
        RestTemplate rt = answering("{\"return\":true}");
        SmsService sms = new SmsService(true, "secret-key", "q", rt);

        assertTrue(sms.send("+91 98765 43210", "hello"));

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rt).exchange(url.capture(), eq(HttpMethod.POST), entity.capture(), eq(JsonNode.class));
        assertFalse(url.getValue().contains("secret-key"));
        assertEquals("secret-key", entity.getValue().getHeaders().getFirst("authorization"));
        Map<String, Object> body = (Map<String, Object>) entity.getValue().getBody();
        assertEquals("9876543210", body.get("numbers"));
        assertEquals("hello", body.get("message"));
    }

    @Test
    void doesNothingWhenSwitchedOff_orNoKey_orBadNumber() throws Exception {
        RestTemplate rt = answering("{\"return\":true}");
        assertFalse(new SmsService(false, "k", "q", rt).send("9876543210", "x"));
        assertFalse(new SmsService(true, "", "q", rt).send("9876543210", "x"));
        assertFalse(new SmsService(true, "k", "q", rt).send("12345", "x"));
        assertFalse(new SmsService(true, "k", "q", rt).send(null, "x"));
        verifyNoInteractions(rt);
    }

    @Test
    void aRefusalOrAnErrorIsReportedAsNotSent_neverThrown() throws Exception {
        assertFalse(new SmsService(true, "k", "q", answering("{\"return\":false,\"message\":[\"bad\"]}"))
                .send("9876543210", "x"));

        RestTemplate failing = mock(RestTemplate.class);
        when(failing.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("timeout"));
        assertFalse(new SmsService(true, "k", "q", failing).send("9876543210", "x"));
    }

    @Test
    void paymentSms_goesToTheMobileOnTheBill_withTheDetails() {
        BbpsTransactionRepository repo = mock(BbpsTransactionRepository.class);
        SmsService sms = mock(SmsService.class);
        BbpsTransaction t = new BbpsTransaction();
        t.setTxnRefId("CC01TXN");
        t.setCustomerMobile("9822011111");
        t.setBillerName("ICICI Credit card");
        t.setConsumerNumber("4242");
        t.setAmount(new BigDecimal("1500"));
        t.setPaymentMode("WALLET");
        when(repo.findByTxnRefId("CC01TXN")).thenReturn(Optional.of(t));

        new BbpsNotificationService(repo, sms).sendPaymentSuccess("CC01TXN");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(sms).send(eq("9822011111"), text.capture());
        assertTrue(text.getValue().contains("Rs.1500.00"), text.getValue());
        assertTrue(text.getValue().contains("ICICI Credit card"));
        assertTrue(text.getValue().contains("CC01TXN"));
    }

    @Test
    void complaintSms_goesToTheSameCustomer_andAFailureIsSwallowed() {
        BbpsTransactionRepository repo = mock(BbpsTransactionRepository.class);
        SmsService sms = mock(SmsService.class);
        BbpsTransaction t = new BbpsTransaction();
        t.setTxnRefId("CC01TXN");
        t.setCustomerMobile("9822011111");
        when(repo.findByTxnRefId("CC01TXN")).thenReturn(Optional.of(t));
        BbpsNotificationService service = new BbpsNotificationService(repo, sms);

        service.sendComplaintRegistered("CC01TXN", "CMP9");
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(sms).send(eq("9822011111"), text.capture());
        assertTrue(text.getValue().contains("CMP9"));

        when(repo.findByTxnRefId("BOOM")).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.sendPaymentSuccess("BOOM"));
        assertDoesNotThrow(() -> service.sendPaymentSuccess(null));
    }
}
