package com.project2.ism.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The Paytm URL must answer 200 "OK" whatever Paytm sends (JSON, form, nothing). */
class PaytmNotificationControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaytmNotificationController()).build();

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
    }
}
