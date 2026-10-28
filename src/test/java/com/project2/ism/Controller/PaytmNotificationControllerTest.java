package com.project2.ism.Controller;

import com.project2.ism.Service.PaytmNotificationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Paytm URL must answer 200 "OK" whatever Paytm sends (JSON, form, nothing). */
class PaytmNotificationControllerTest {

    private final PaytmNotificationLogService logService = mock(PaytmNotificationLogService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaytmNotificationController(logService)).build();

    @Test
    void json_isAcknowledged() throws Exception {
        mvc.perform(post("/paytm/notification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"ORD1\",\"status\":\"TXN_SUCCESS\"}"))
                .andExpect(status().isOk()).andExpect(content().string("OK"));
    }

    @Test
    void formEncoded_isAcknowledged() throws Exception {
        mvc.perform(post("/paytm/notification").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("ORDERID=ORD1&STATUS=TXN_SUCCESS&CHECKSUMHASH=abc"))
                .andExpect(status().isOk()).andExpect(content().string("OK"));
    }

    @Test
    void emptyBody_isAcknowledged() throws Exception {
        mvc.perform(post("/paytm/notification"))
                .andExpect(status().isOk()).andExpect(content().string("OK"));
    }

    @Test
    void hugeBody_isAcknowledged_andDoesNotBlowUp() throws Exception {
        mvc.perform(post("/paytm/notification").contentType(MediaType.TEXT_PLAIN).content("x".repeat(200_000)))
                .andExpect(status().isOk());
    }

    @Test
    void get_worksForReachabilityCheck() throws Exception {
        mvc.perform(get("/paytm/notification"))
                .andExpect(status().isOk()).andExpect(content().string("OK"));

        // a reachability check is not a notification, so nothing is stored for it
        verify(logService, never()).record(any(), any(), any(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void everyNotification_isStoredForTheAdminLogs() throws Exception {
        String body = "{\"orderId\":\"ORD1\",\"status\":\"TXN_SUCCESS\"}";

        mvc.perform(post("/paytm/notification").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(logService).record(any(), any(), anyString(), anyString(), eq(body), eq(body.length()), eq(false));
    }

    @Test
    void emptyBody_isStoredToo() throws Exception {
        mvc.perform(post("/paytm/notification")).andExpect(status().isOk());

        verify(logService).record(any(), any(), any(), anyString(), eq(""), eq(0), eq(false));
    }

    @Test
    void hugeBody_isStoredCut_withItsRealSize() throws Exception {
        mvc.perform(post("/paytm/notification").contentType(MediaType.TEXT_PLAIN).content("x".repeat(200_000)))
                .andExpect(status().isOk());

        verify(logService).record(any(), any(), any(), anyString(), eq("x".repeat(20_000)), eq(200_000), eq(true));
    }

    @Test
    void credentialsInTheHeaders_areNotStored() throws Exception {
        mvc.perform(post("/paytm/notification").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer secret-token").header("Cookie", "session=abc"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<String> headers = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logService).record(any(), any(), any(), headers.capture(), any(), anyInt(), anyBoolean());
        org.junit.jupiter.api.Assertions.assertFalse(headers.getValue().toLowerCase().contains("secret-token"));
        org.junit.jupiter.api.Assertions.assertFalse(headers.getValue().toLowerCase().contains("session=abc"));
    }

    @Test
    void ifStoringFails_paytmStillGetsItsOk() throws Exception {
        doThrow(new RuntimeException("database is down")).when(logService)
                .record(any(), any(), any(), any(), any(), anyInt(), anyBoolean());

        mvc.perform(post("/paytm/notification").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(content().string("OK"));
    }
}
