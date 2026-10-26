package com.project2.ism.Controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * TEMPORARY endpoint for Paytm's notification (webhook) URL.
 *
 * Right now it only receives whatever Paytm sends, writes it to the log and
 * answers 200 "OK" straight away. Nothing is saved or processed yet; once we
 * have seen the real payload (and Paytm's source IPs / signature scheme) the
 * processing can be added, the same way as the Razorpay / Mosambee controllers.
 *
 * URL to give Paytm: https://portal.utsabpay.com/api/paytm/notification
 */
@RestController
@RequestMapping("/paytm")
public class PaytmNotificationController {

    private static final Logger log = LoggerFactory.getLogger(PaytmNotificationController.class);

    // The endpoint is open to the internet, so cap what one request can put in the log.
    private static final int MAX_LOGGED_BODY_CHARS = 20_000;

    // Never write credentials a caller might attach into the log.
    private static final Set<String> HIDDEN_HEADERS = Set.of("authorization", "cookie", "proxy-authorization");

    @PostMapping("/notification")
    public ResponseEntity<String> receiveNotification(@RequestBody(required = false) String body,
                                                      HttpServletRequest request) {

        String printableBody = body == null ? "" : body;
        boolean truncated = printableBody.length() > MAX_LOGGED_BODY_CHARS;
        if (truncated) {
            printableBody = printableBody.substring(0, MAX_LOGGED_BODY_CHARS);
        }

        log.info("Paytm notification received | remoteAddr={} | x-forwarded-for={} | contentType={} | bodyLength={}{}",
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"),
                request.getContentType(),
                body == null ? 0 : body.length(),
                truncated ? " (truncated in log)" : "");
        log.info("Paytm notification headers: {}", describeHeaders(request));
        log.info("Paytm notification body: {}", printableBody);

        return ResponseEntity.ok("OK");
    }

    /** Lets you open the URL in a browser to confirm it is reachable. */
    @GetMapping("/notification")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("OK");
    }

    private String describeHeaders(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        for (String name : Collections.list(request.getHeaderNames())) {
            if (HIDDEN_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name).append('=').append(request.getHeader(name));
        }
        return sb.toString();
    }
}
