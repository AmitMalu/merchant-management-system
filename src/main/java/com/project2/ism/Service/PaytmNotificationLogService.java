package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Model.Logs.PaytmNotificationLog;
import com.project2.ism.Repository.PaytmNotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Stores the notifications Paytm sends (table paytm_notification_logs) and lets the admin
 * search them.
 *
 * The notification URL is open to the internet, and Paytm must always get a 200 back, so:
 *  - saving never throws (a database problem is only written to the application log);
 *  - what one call can store is capped (body and headers are cut);
 *  - at most paytm.notification.max-saved-per-minute rows are stored per minute, so a flood
 *    cannot fill the database (the call is still acknowledged and still written to the log file);
 *  - rows older than 90 days are deleted every night.
 */
@Service
public class PaytmNotificationLogService {

    private static final Logger log = LoggerFactory.getLogger(PaytmNotificationLogService.class);

    static final int MAX_HEADERS_CHARS = 8_000;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int RETENTION_DAYS = 90;

    // Names Paytm may use for the order id / transaction id (JSON key or form field), any case.
    private static final List<String> ORDER_ID_KEYS = List.of("orderid", "order_id", "merchantorderid");
    private static final List<String> TXN_ID_KEYS = List.of("txnid", "txn_id", "transactionid", "transaction_id");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PaytmNotificationLogRepository repository;

    @Value("${paytm.notification.max-saved-per-minute:300}")
    private int maxSavedPerMinute = 300;

    public PaytmNotificationLogService(PaytmNotificationLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves one received notification. Never throws.
     *
     * @param body       the body, already cut to the size limit
     * @param bodyLength the size of what Paytm actually sent
     * @return the saved row, or null when it was not stored (flood guard or database problem)
     */
    public PaytmNotificationLog record(String remoteAddr, String forwardedFor, String contentType,
                                       String headers, String body, int bodyLength, boolean truncated) {
        try {
            if (maxSavedPerMinute > 0
                    && repository.countByCreatedAtAfter(LocalDateTime.now().minusMinutes(1)) >= maxSavedPerMinute) {
                log.warn("Paytm notification NOT stored: more than {} stored in the last minute (flood guard) | remoteAddr={}",
                        maxSavedPerMinute, remoteAddr);
                return null;
            }

            PaytmNotificationLog entry = new PaytmNotificationLog();
            entry.setRemoteAddr(cut(remoteAddr, 64));
            entry.setForwardedFor(cut(forwardedFor, 255));
            entry.setContentType(cut(contentType, 150));
            entry.setHeaders(cut(headers, MAX_HEADERS_CHARS));
            entry.setRawBody(body);
            entry.setBodyLength(bodyLength);
            entry.setBodyTruncated(truncated);
            entry.setOrderId(cut(findValue(body, ORDER_ID_KEYS), 100));
            entry.setTxnId(cut(findValue(body, TXN_ID_KEYS), 100));
            entry.setProcessStatus("RECEIVED");

            return repository.save(entry);
        } catch (Exception e) {
            log.error("Could not save the Paytm notification log (the notification was still acknowledged): {}",
                    e.getMessage(), e);
            return null;
        }
    }

    /** Admin search; every filter is optional. Newest first. */
    @Transactional(readOnly = true)
    public Page<PaytmNotificationLog> search(String status, String reference, LocalDate start, LocalDate end,
                                             int page, int size) {
        String processStatus = (status == null || status.isBlank() || "all".equalsIgnoreCase(status.trim()))
                ? null : status.trim().toUpperCase(Locale.ROOT);
        String ref = (reference == null || reference.isBlank()) ? null : reference.trim();

        return repository.findLogsByFilters(
                ref,
                processStatus,
                start != null ? start.atStartOfDay() : null,
                end != null ? end.atTime(23, 59, 59) : null,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
    }

    @Scheduled(cron = "0 30 2 * * ?") // every night at 2:30
    @Transactional
    public void cleanupOldLogs() {
        try {
            int deleted = repository.deleteOlderThan(LocalDateTime.now().minusDays(RETENTION_DAYS));
            log.info("Cleaned up {} Paytm notification logs older than {} days", deleted, RETENTION_DAYS);
        } catch (Exception e) {
            log.error("Error cleaning up Paytm notification logs: {}", e.getMessage());
        }
    }

    // =====================================================================

    /**
     * Finds the first of `keys` (any case) in a JSON body (at the top, or inside "body") or in a
     * form-encoded body (a=b&c=d). Returns null when it is not there or the body is neither.
     */
    static String findValue(String body, List<String> keys) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("{")) {
                JsonNode root = MAPPER.readTree(trimmed);
                String found = valueIn(root, keys);
                if (found == null && root.path("body").isObject()) {
                    found = valueIn(root.path("body"), keys);
                }
                return found;
            }
            if (trimmed.contains("=")) {
                for (String pair : trimmed.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0 && keys.contains(pair.substring(0, eq).trim().toLowerCase(Locale.ROOT))) {
                        String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8).trim();
                        return value.isEmpty() ? null : value;
                    }
                }
            }
        } catch (Exception ignored) {
            // not parseable: the raw body is still stored, there is just no id to search by
        }
        return null;
    }

    private static String valueIn(JsonNode node, List<String> keys) {
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (keys.contains(field.getKey().toLowerCase(Locale.ROOT)) && field.getValue().isValueNode()) {
                String value = field.getValue().asText().trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
