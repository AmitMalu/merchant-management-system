package com.project2.ism.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The agent device details sent to Bill Avenue: the real client IPv4, with a configured fallback.
 */
class BillAvenueAgentDeviceTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            req.addHeader("X-Forwarded-For", forwardedFor);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test
    void usesTheRealClientIpv4_notATestValue() {
        request("49.36.10.20", null);
        BillAvenueAgentDevice device = new BillAvenueAgentDevice("INT", "aa-bb", "", false);

        assertEquals("49.36.10.20", device.ip());
        assertEquals("INT", device.channel());
        assertEquals("aa-bb", device.mac());
    }

    @Test
    void ipv6OrLoopback_usesTheFallback() {
        BillAvenueAgentDevice device = new BillAvenueAgentDevice("INT", "m", "203.0.113.5", false);

        request("2401:4900:1c2a::1", null);
        assertEquals("203.0.113.5", device.ip());

        request("127.0.0.1", null);
        assertEquals("203.0.113.5", device.ip());
    }

    @Test
    void noIpv4AndNoFallback_failsWithAClearMessage() {
        request("2401:4900:1c2a::1", null);
        BillAvenueAgentDevice device = new BillAvenueAgentDevice("INT", "m", "", false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, device::ip);
        assertTrue(ex.getMessage().contains("IPv4"));
    }

    @Test
    void aBadFallbackIsNotUsed() {
        request("::1", null);
        BillAvenueAgentDevice device = new BillAvenueAgentDevice("INT", "m", "not-an-ip", false);
        assertThrows(IllegalStateException.class, device::ip);
    }

    @Test
    void forwardedForIsIgnoredUnlessTrusted() {
        request("49.36.10.20", "8.8.8.8");
        assertEquals("49.36.10.20", new BillAvenueAgentDevice("INT", "m", "", false).ip());
    }

    @Test
    void forwardedForIsUsedWhenTrusted_firstAddressOnly() {
        request("10.0.0.2", "8.8.4.4, 10.0.0.2");
        assertEquals("8.8.4.4", new BillAvenueAgentDevice("INT", "m", "", true).ip());
    }

    @Test
    void noRequestAtAll_usesTheFallback() {
        BillAvenueAgentDevice device = new BillAvenueAgentDevice("INT", "m", "203.0.113.5", false);
        assertEquals("203.0.113.5", device.ip());
    }
}
