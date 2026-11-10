package com.project2.ism.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Pattern;

/**
 * The "agent device" details Bill Avenue wants with every bill fetch and bill payment.
 *
 * Before: the IP, MAC and channel were fixed test values in the code. Now:
 *  - IP      the real address of the browser that made the request (IPv4 only, as Bill Avenue
 *            expects). If it cannot be found or is not IPv4 (for example IPv6), the configured
 *            fallback address is used instead.
 *  - MAC     from configuration (a web application has no real MAC address to send).
 *  - channel from configuration (INT = internet).
 *
 * The X-Forwarded-For header is read only when bill.avenue.agent.trust-forwarded-for=true, because
 * anyone can send that header themselves; turn it on only if a trusted proxy sits in front.
 */
@Component
public class BillAvenueAgentDevice {

    private static final Logger log = LoggerFactory.getLogger(BillAvenueAgentDevice.class);

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$");

    private final String channel;
    private final String mac;
    private final String fallbackIp;
    private final boolean trustForwardedFor;

    public BillAvenueAgentDevice(
            @Value("${bill.avenue.agent.channel:INT}") String channel,
            @Value("${bill.avenue.agent.mac:}") String mac,
            @Value("${bill.avenue.agent.fallback-ip:}") String fallbackIp,
            @Value("${bill.avenue.agent.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.channel = channel;
        this.mac = mac;
        this.fallbackIp = fallbackIp;
        this.trustForwardedFor = trustForwardedFor;
    }

    public String channel() {
        return channel;
    }

    public String mac() {
        return mac;
    }

    /** The IPv4 address to send, or an IllegalStateException with a clear message when there is none. */
    public String ip() {
        String found = clientIpFromCurrentRequest();
        if (found != null) {
            return found;
        }
        if (fallbackIp != null && IPV4.matcher(fallbackIp.trim()).matches()) {
            log.info("Client IPv4 not available - using the configured fallback address");
            return fallbackIp.trim();
        }
        throw new IllegalStateException(
                "Could not work out your IPv4 address for the bill payment request. "
                        + "Please contact the administrator (bill.avenue.agent.fallback-ip is not set).");
    }

    private String clientIpFromCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        HttpServletRequest request = servletAttributes.getRequest();

        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // the first address is the original client
                String first = forwarded.split(",")[0].trim();
                if (IPV4.matcher(first).matches()) {
                    return first;
                }
            }
        }

        String remote = request.getRemoteAddr();
        if (remote != null && IPV4.matcher(remote.trim()).matches() && !remote.startsWith("127.")) {
            return remote.trim();
        }
        return null;
    }
}
