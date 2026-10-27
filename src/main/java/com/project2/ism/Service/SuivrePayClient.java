package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project2.ism.Model.CreditCard.CreditCardApiLog;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payment.PaymentVendorCredentials;
import com.project2.ism.Repository.PaymentVendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Thin HTTP client for the SuivrePay credit-card bill APIs.
 *
 * Credentials come from the "Payment Vendors" + "Payment Vendors Credentials"
 * admin screens (vendor name "SuivrePay"), so they can be changed without a
 * deploy. The existing credential columns are reused like this:
 *
 *   User ID              -> X-Auth-User
 *   Salt Key             -> X-Auth-Pass
 *   Secret Key           -> X-Auth-Key
 *   Encrypt/Decrypt Key  -> MID (SuivrePay merchant id, e.g. AP503682)
 *   Base URL             -> https://suivrepay.com/apipanel/billavenue
 *
 * This client has its own timeouts (the shared RestTemplate has none) and never
 * throws on a 4xx/5xx: the caller gets the status and body and decides what it
 * means, which matters when money has already been deducted.
 *
 * LOGGING: every call writes a "SuivrePay request" line and a "SuivrePay response"
 * line sharing one callId, and any problem adds a "SuivrePay PROBLEM" line with a
 * plain-language hint. To follow a failure in catalina.out:  grep "SuivrePay"
 * Secrets are never logged: header values are hidden, and card digits, mobile
 * numbers and PAN are masked in the request body.
 */
@Service
public class SuivrePayClient {

    private static final Logger log = LoggerFactory.getLogger(SuivrePayClient.class);

    private static final int LOGGED_BODY_CHARS = 500;
    private static final int MAX_STORED_RESPONSE_CHARS = 20_000;
    private static final Set<String> MASKED_FIELDS = Set.of("value", "customer_pan", "mobile", "customer_mobile");

    /** Result of one vendor call. body is null when the response was not JSON. */
    public record VendorResponse(int httpStatus, JsonNode body, String raw) {
    }

    /** The vendor could not be reached / did not answer in time: outcome unknown. */
    public static class VendorUnreachableException extends RuntimeException {
        public VendorUnreachableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record Creds(String environment, String baseUrl, String user, String pass, String key, String mid) {
    }

    @Value("${suivrepay.vendor-name:SuivrePay}")
    private String vendorName;

    private final PaymentVendorRepository paymentVendorRepository;
    private final PaymentVendorCredentialsService credentialsService;
    private final ObjectMapper objectMapper;
    private final CreditCardApiLogService apiLogService;
    private final RestTemplate restTemplate;

    public SuivrePayClient(PaymentVendorRepository paymentVendorRepository,
                           PaymentVendorCredentialsService credentialsService,
                           ObjectMapper objectMapper,
                           CreditCardApiLogService apiLogService) {
        this.apiLogService = apiLogService;
        this.paymentVendorRepository = paymentVendorRepository;
        this.credentialsService = credentialsService;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false; // let the caller read 4xx / 5xx bodies
            }
        });
    }

    /** Fails fast (before any money moves) when SuivrePay is not configured. */
    public void ensureConfigured() {
        creds();
    }

    /** SuivrePay merchant id used in the Fetch Bill / Pay Bill request bodies. */
    public String mid() {
        return creds().mid();
    }

    /** Who is making a call and what it is for. Only used for the admin "Credit Card Logs". */
    public record CallContext(String action, String customerType, Long customerId, String billerId,
                              String cardLast4, String requestId, java.math.BigDecimal amount) {
        public static CallContext none() {
            return new CallContext(CreditCardApiLog.UNKNOWN_ACTION, null, null, null, null, null, null);
        }

        public static CallContext of(String action, CustomerRef who, String billerId, String cardLast4,
                                     String requestId, java.math.BigDecimal amount) {
            return new CallContext(action, who != null ? who.type() : null, who != null ? who.id() : null,
                    billerId, cardLast4, requestId, amount);
        }
    }

    public VendorResponse post(String path, Object body) {
        return post(path, body, CallContext.none());
    }

    public VendorResponse post(String path, Object body, CallContext ctx) {
        CallContext context = ctx != null ? ctx : CallContext.none();
        String callId = UUID.randomUUID().toString().substring(0, 8);

        Creds c;
        try {
            c = creds();
        } catch (IllegalStateException e) {
            // vendor / credentials not configured: nothing could be sent, but leave a trace for admin
            recordApiLog(callId, context, path, body == null ? "{}" : safeJson(body), null, null, null,
                    CreditCardApiLog.OUTCOME_CONFIG_ERROR, e.getMessage(), 0L);
            throw e;
        }

        String url = buildUrl(c.baseUrl(), path);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Auth-User", c.user());
        headers.set("X-Auth-Pass", c.pass());
        headers.set("X-Auth-Key", c.key());

        String json;
        try {
            json = body == null ? "{}" : objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Could not build SuivrePay request: " + e.getMessage(), e);
        }

        log.info("SuivrePay request | callId={} | POST {} | environment={} | auth headers sent (values hidden) | body={}",
                callId, url, c.environment(), maskBody(json));

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);

            String raw = response.getBody();
            int http = response.getStatusCode().value();
            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString() : "none";

            JsonNode node = null;
            if (raw != null && !raw.isBlank()) {
                try {
                    node = objectMapper.readTree(raw);
                } catch (Exception ignored) {
                    // not JSON (e.g. an HTML error page): caller treats it as unknown
                }
            }

            long timeMs = System.currentTimeMillis() - start;
            String vendorStatus = node == null ? "(not JSON)" : node.path("status").asText("(none)");
            log.info("SuivrePay response | callId={} | path={} | httpStatus={} | vendorStatus={} | contentType={} | timeMs={}",
                    callId, path, http, vendorStatus, contentType, timeMs);

            boolean ok = http >= 200 && http < 300 && node != null && "success".equalsIgnoreCase(vendorStatus);
            if (!ok) {
                log.warn("SuivrePay PROBLEM | callId={} | url={} | httpStatus={} | json={} | hint={} | responseBody={}",
                        callId, url, http, node != null, hintFor(http, node != null, c.environment()),
                        snippet(raw));
            }

            recordApiLog(callId, context, url, json, http, node, raw,
                    ok ? CreditCardApiLog.OUTCOME_SUCCESS : CreditCardApiLog.OUTCOME_REFUSED, null, timeMs);

            return new VendorResponse(http, node, raw);

        } catch (RestClientException e) {
            Throwable root = rootCause(e);
            long timeMs = System.currentTimeMillis() - start;
            log.error("SuivrePay PROBLEM | callId={} | url={} | no usable response after {} ms | error={} | rootCause={}: {} | hint={}",
                    callId, url, timeMs, e.getClass().getSimpleName(),
                    root.getClass().getSimpleName(), root.getMessage(), hintForFailure(root));

            recordApiLog(callId, context, url, json, null, null, null, CreditCardApiLog.OUTCOME_NO_RESPONSE,
                    root.getClass().getSimpleName() + ": " + root.getMessage(), timeMs);

            throw new VendorUnreachableException("SuivrePay did not respond: " + e.getMessage(), e);
        }
    }

    /**
     * Writes the admin "Credit Card Logs" row. The stored request / response have card digits,
     * mobile numbers and PAN masked. Never throws: a logging problem must not affect the call.
     */
    private void recordApiLog(String callId, CallContext ctx, String url, String requestJson,
                              Integer http, JsonNode node, String raw, String outcome,
                              String errorMessage, long timeMs) {
        try {
            CreditCardApiLog entry = new CreditCardApiLog();
            entry.setCallId(callId);
            entry.setCustomerType(ctx.customerType() != null ? ctx.customerType() : CustomerRef.MERCHANT);
            entry.setMerchantId(ctx.customerId());
            entry.setAction(ctx.action());
            entry.setBillerId(ctx.billerId());
            entry.setCardLast4(ctx.cardLast4());
            entry.setEndpointUrl(truncate(url, 500));
            entry.setRequestPayload(maskBody(requestJson));
            entry.setHttpStatus(http);
            entry.setOutcome(outcome);
            entry.setErrorMessage(truncate(errorMessage, 500));
            entry.setTimeTakenMs(timeMs);

            // request id / amount: from the caller, else from the request body, else from the response
            String requestId = ctx.requestId();
            java.math.BigDecimal amount = ctx.amount();
            try {
                JsonNode req = objectMapper.readTree(requestJson);
                if (requestId == null && req.hasNonNull("request_id")) {
                    requestId = req.get("request_id").asText();
                }
                if (amount == null && req.hasNonNull("amount")) {
                    amount = new java.math.BigDecimal(req.get("amount").asText());
                }
                if (entry.getBillerId() == null && req.hasNonNull("biller_id")) {
                    entry.setBillerId(truncate(req.get("biller_id").asText(), 50));
                }
            } catch (Exception ignored) {
                // request was not JSON: nothing to read
            }

            if (node != null) {
                if (requestId == null && node.path("data").hasNonNull("request_id")) {
                    requestId = node.path("data").get("request_id").asText();
                }
                entry.setVendorStatus(truncate(node.path("status").asText(null), 30));
                entry.setVendorResponseCode(truncate(node.path("response_code").asText(null), 20));
                entry.setVendorErrorCode(truncate(node.path("error_code").asText(null), 30));
                entry.setVendorMessage(truncate(node.path("message").asText(null), 500));

                JsonNode masked = node.deepCopy();
                mask(masked);
                entry.setResponsePayload(truncate(masked.toString(), MAX_STORED_RESPONSE_CHARS));
            } else if (raw != null) {
                entry.setResponsePayload(truncate(raw.replaceAll("\\s+", " ").trim(), 2000));
            }

            entry.setRequestId(truncate(requestId, 100));
            entry.setAmount(amount);

            apiLogService.record(entry);
        } catch (Exception e) {
            log.warn("SuivrePay: could not prepare the credit card log row | callId={} | error={}", callId, e.getMessage());
        }
    }

    private String safeJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private Creds creds() {
        PaymentVendor vendor = paymentVendorRepository
                .findByVendorNameAndStatus(vendorName, true)
                .orElseThrow(() -> {
                    log.error("SuivrePay PROBLEM | no ACTIVE vendor named '{}' in Payment Vendors | hint=Add the vendor (exact name) and mark it active.",
                            vendorName);
                    return new IllegalStateException(
                            "Credit card bill payment is not available right now (vendor not configured).");
                });

        PaymentVendorCredentials c;
        try {
            c = credentialsService.getActiveEnvironmentCredentials(vendor.getId());
        } catch (RuntimeException e) {
            log.error("SuivrePay PROBLEM | credentials not usable for vendorId={} | reason={} | hint=Open Payment Vendors Credentials: it needs an active row, an Active Environment (UAT/PROD) and ALL five fields of that environment filled.",
                    vendor.getId(), e.getMessage());
            throw new IllegalStateException(
                    "Credit card bill payment is not available right now (credentials not configured).");
        }

        boolean uat = "UAT".equalsIgnoreCase(c.getActiveEnvironment());
        Creds creds = new Creds(
                uat ? "UAT" : "PROD",
                uat ? c.getBaseUrlUat() : c.getBaseUrlProd(),
                uat ? c.getUserIdUat() : c.getUserIdProd(),
                uat ? c.getSaltKeyUat() : c.getSaltKeyProd(),
                uat ? c.getSecretKeyUat() : c.getSecretKeyProd(),
                uat ? c.getEncryptDecryptKeyUat() : c.getEncryptDecryptKeyProd()
        );

        List<String> blank = new ArrayList<>();
        if (isBlank(creds.baseUrl())) blank.add("Base URL");
        if (isBlank(creds.user())) blank.add("User ID (X-Auth-User)");
        if (isBlank(creds.pass())) blank.add("Salt Key (X-Auth-Pass)");
        if (isBlank(creds.key())) blank.add("Secret Key (X-Auth-Key)");
        if (isBlank(creds.mid())) blank.add("Encrypt/Decrypt Key (MID)");
        if (!blank.isEmpty()) {
            log.error("SuivrePay PROBLEM | environment={} | these credential fields are empty: {}", creds.environment(), blank);
            throw new IllegalStateException(
                    "Credit card bill payment is not available right now (credentials not configured).");
        }
        return creds;
    }

    // ------------------------------------------------------------------
    // Diagnostics helpers (log text only)
    // ------------------------------------------------------------------

    private String hintFor(int http, boolean json, String environment) {
        if (http == 404 && !json) {
            return "404 HTML page: that file does not exist on SuivrePay's server. Check the Base URL in Payment Vendors Credentials and the suivrepay.path.* values against their API doc, or ask SuivrePay if the API is live.";
        }
        return switch (http) {
            case 400 -> "400: SuivrePay rejected the request data (see responseBody for which field).";
            case 401 -> "401: X-Auth-User / X-Auth-Pass / X-Auth-Key missing or wrong, or the SuivrePay account is inactive. Re-check the " + environment + " values in Payment Vendors Credentials.";
            case 402 -> "402: SuivrePay wallet balance is too low for this payment. Fund the SuivrePay wallet.";
            case 403 -> "403: this server's public IP is probably not whitelisted at SuivrePay (or access is blocked).";
            case 404 -> "404 JSON: SuivrePay says the item was not found (see responseBody).";
            case 429 -> "429: too many requests to SuivrePay.";
            case 200 -> "200 with status=error: SuivrePay or the biller refused the request. Read message and error_code in responseBody (for example ERR033 = too many requests for that biller, 001 = biller/account error).";
            default -> http >= 500
                    ? http + ": SuivrePay server error. For a payment the result is UNKNOWN, so it stays PENDING."
                    : http + ": unexpected answer (see responseBody).";
        };
    }

    private String hintForFailure(Throwable root) {
        String name = root.getClass().getSimpleName();
        String msg = String.valueOf(root.getMessage());
        if (name.contains("Timeout")) {
            return "Timed out waiting for SuivrePay (connect 10s / read 60s). For a payment the result is UNKNOWN, so it stays PENDING.";
        }
        if (name.contains("UnknownHost")) {
            return "DNS: this server cannot resolve the SuivrePay host name. Check the Base URL and the server's DNS/internet.";
        }
        if (name.contains("ConnectException") || msg.toLowerCase().contains("connection refused")) {
            return "Could not connect to SuivrePay (refused / blocked). Check the Base URL, firewall and outbound access.";
        }
        if (name.contains("SSL") || msg.contains("PKIX") || msg.toLowerCase().contains("certificate")) {
            return "TLS/certificate problem talking to SuivrePay. Check the Base URL (https) and the server's Java trust store.";
        }
        return "Network error talking to SuivrePay (see rootCause).";
    }

    private static Throwable rootCause(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r;
    }

    /** Request body for the log: card digits, mobile numbers and PAN are masked. */
    private String maskBody(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            mask(node);
            return node.toString();
        } catch (Exception e) {
            return "(unreadable)";
        }
    }

    private void mask(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = obj.get(name);
                if (MASKED_FIELDS.contains(name) && child.isValueNode()) {
                    obj.put(name, maskValue(child.asText()));
                } else {
                    mask(child);
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::mask);
        }
    }

    private static String maskValue(String v) {
        if (v == null || v.length() <= 4) {
            return "****";
        }
        return v.substring(0, 2) + "*".repeat(v.length() - 4) + v.substring(v.length() - 2);
    }

    private static String snippet(String raw) {
        if (raw == null || raw.isBlank()) {
            return "(empty)";
        }
        String oneLine = raw.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= LOGGED_BODY_CHARS ? oneLine : oneLine.substring(0, LOGGED_BODY_CHARS) + "...(cut)";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Base URL + path. A path that is already a full http(s) URL is used as it is (some
     * SuivrePay APIs, such as complaints, live in a different folder from the bill APIs).
     * Accidental double slashes (for example "...com//apipanel") are collapsed.
     */
    private static String buildUrl(String baseUrl, String path) {
        String url = (path.startsWith("http://") || path.startsWith("https://"))
                ? path
                : trimTrailingSlash(baseUrl) + (path.startsWith("/") ? path : "/" + path);
        return url.replaceAll("(?<!:)/{2,}", "/");
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
