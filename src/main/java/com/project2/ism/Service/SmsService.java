package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sends one SMS through Fast2SMS. Runs on the server, so the API key is never sent to a browser.
 *
 * Never throws: a failed SMS must not affect a payment. Returns true only when Fast2SMS accepted it.
 * Off unless sms.enabled=true and an API key is set (sms.fast2sms.api-key, normally from the
 * FAST2SMS_API_KEY environment variable).
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final String URL = "https://www.fast2sms.com/dev/bulkV2";
    private static final Pattern INDIAN_MOBILE = Pattern.compile("^[6-9]\\d{9}$");

    private final boolean enabled;
    private final String apiKey;
    private final String route;
    private final RestTemplate restTemplate;

    @Autowired
    // All three come from the server's environment: SMS_ENABLED=true and FAST2SMS_API_KEY=... (and
    // optionally SMS_FAST2SMS_ROUTE). Nothing is kept in application.properties.
    public SmsService(@Value("${sms.enabled:false}") boolean enabled,
                      @Value("${sms.fast2sms.api-key:${FAST2SMS_API_KEY:}}") String apiKey,
                      @Value("${sms.fast2sms.route:q}") String route) {
        this(enabled, apiKey, route, withTimeouts());
    }

    SmsService(boolean enabled, String apiKey, String route, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.route = route;
        log.info("SMS service ready: enabled={}, apiKeyPresent={}, route={}",
                enabled, apiKey != null && !apiKey.isBlank(), route);
        this.restTemplate = restTemplate;
    }

    private static RestTemplate withTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }

    public boolean send(String mobile, String message) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.info("SMS NOT sent: {}", !enabled ? "sms.enabled / SMS_ENABLED is not true"
                    : "no API key (FAST2SMS_API_KEY is empty)");
            return false;
        }
        String number = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (number.length() == 12 && number.startsWith("91")) {
            number = number.substring(2);
        }
        if (!INDIAN_MOBILE.matcher(number).matches() || message == null || message.isBlank()) {
            log.warn("SMS NOT sent: no valid 10-digit Indian mobile number (got {} digits)", number.length());
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", apiKey);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("route", route);
            body.put("message", message);
            body.put("language", "english");
            body.put("numbers", number);

            JsonNode response = restTemplate
                    .exchange(URL, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class)
                    .getBody();

            // Fast2SMS answers HTTP 200 even when it refuses; the real result is in the body.
            boolean accepted = response != null && response.path("return").asBoolean(false);
            if (accepted) {
                log.info("SMS accepted by Fast2SMS for ***{}", number.substring(6));
            } else {
                log.warn("SMS REFUSED by Fast2SMS for ***{}: {}", number.substring(6),
                        response != null ? response.toString() : "no response");
            }
            return accepted;
        } catch (Exception ex) {
            log.warn("SMS could not be sent to ***{}: {}", number.substring(6), ex.getMessage());
            return false;
        }
    }
}
