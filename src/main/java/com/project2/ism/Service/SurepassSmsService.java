package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.project2.ism.DTO.VendorCredentialDTO;
import com.project2.ism.Model.Sms.SmsLog;
import com.project2.ism.Model.Sms.SmsTemplate;
import com.project2.ism.Repository.SmsLogRepository;
import com.project2.ism.Repository.SmsTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sends DLT template SMS through Surepass (the sender). The DLT template itself is registered on the
 * DLT portal (Satzilio); here we only name the template id and fill its variables.
 *
 * Safe by default:
 *  - off unless sms.surepass.enabled=true (environment variable SMS_SUREPASS_ENABLED);
 *  - a message is only sent when its row in sms_templates is enabled and has a template id;
 *  - the Surepass base URL and token are the ones already saved under Vendor Credentials (SUREPASS),
 *    so nothing is kept in a file;
 *  - never throws (a failed SMS must not affect a payment) and sends the same message only once;
 *  - every attempt is written to sms_logs with the mobile number masked.
 */
@Service
public class SurepassSmsService {

    private static final Logger log = LoggerFactory.getLogger(SurepassSmsService.class);

    /** One key per service, so an admin can switch each one on or off. Bill Avenue and SuivrePay share one DLT template. */
    public static final String BILL_AVENUE_PAYMENT = "BILL_AVENUE_PAYMENT";
    public static final String SUIVREPAY_PAYMENT = "SUIVREPAY_PAYMENT";
    public static final String BILL_AVENUE_COMPLAINT = "BILL_AVENUE_COMPLAINT";

    static final String DEFAULT_SENDER_ID = "SMCRSO";
    static final String COMPLAINT_REGISTERED_TEXT =
            "Your complaint has been registered successfully for B-connect Txn ID {#var#}. "
                    + "Your Complaint ID is {#var#}. Track the status of your complaint using your Complaint ID. - SMARTCREDIT";
    static final String BILL_PAYMENT_SUCCESS_TEXT =
            "Thank you for payment of Rs. {#var#} against {#var#}, Consumer No. {#var#}, "
                    + "B-connect Txn ID {#var#} on {#var#} via {#var#}. - SMARTCREDIT";

    /** DLT limits an alphanumeric variable to 30 characters. */
    private static final int MAX_VARIABLE_LENGTH = 30;
    private static final Pattern INDIAN_MOBILE = Pattern.compile("^[6-9]\\d{9}$");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final SmsTemplateRepository templates;
    private final SmsLogRepository logs;
    private final PaymentVendorCredentialsService credentials;
    private final boolean enabled;
    private final String sendPath;
    private final RestTemplate restTemplate;

    @Autowired
    public SurepassSmsService(SmsTemplateRepository templates,
                              SmsLogRepository logs,
                              PaymentVendorCredentialsService credentials,
                              @Value("${sms.surepass.enabled:false}") boolean enabled,
                              @Value("${sms.surepass.path:/api/v1/sms/send-sms}") String sendPath) {
        this(templates, logs, credentials, enabled, sendPath, withTimeouts());
    }

    SurepassSmsService(SmsTemplateRepository templates, SmsLogRepository logs,
                       PaymentVendorCredentialsService credentials, boolean enabled,
                       String sendPath, RestTemplate restTemplate) {
        this.templates = templates;
        this.logs = logs;
        this.credentials = credentials;
        this.enabled = enabled;
        this.sendPath = sendPath;
        this.restTemplate = restTemplate;
        log.info("Surepass SMS ready: enabled={}", enabled);
    }

    private static RestTemplate withTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }

    /**
     * Adds a row for each message the first time the app starts. They start ON, so nothing changes for
     * customers until a template id is added (and the environment switch is on). The old sender keeps
     * working for the messages it already sent.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedTemplates() {
        seed(BILL_AVENUE_PAYMENT, "Bill Avenue - payment success", BILL_PAYMENT_SUCCESS_TEXT);
        seed(SUIVREPAY_PAYMENT, "SuivrePay credit card - payment success", BILL_PAYMENT_SUCCESS_TEXT);
        seed(BILL_AVENUE_COMPLAINT, "Bill Avenue - complaint registered", COMPLAINT_REGISTERED_TEXT);
    }

    private void seed(String key, String label, String text) {
        try {
            SmsTemplate row = templates.findByTemplateKey(key).orElse(null);
            if (row == null) {
                row = new SmsTemplate();
                row.setTemplateKey(key);
                row.setSenderId(DEFAULT_SENDER_ID);
                row.setMessageText(text);
                row.setEnabled(true);
                row.setLabel(label);
                templates.save(row);
                log.info("SMS template row {} created (on, no template id yet)", key);
            } else if (row.getLabel() == null) {
                row.setLabel(label);
                templates.save(row);
            }
        } catch (Exception ex) {
            log.warn("Could not create the SMS template row {}: {}", key, ex.getMessage());
        }
    }

    /**
     * The admin's switch. False means this message must not be sent by ANY sender.
     * A message with no row yet counts as on, and so does a failed read, so a database hiccup never
     * silences customers who were getting the SMS before.
     */
    public boolean isEnabled(String templateKey) {
        try {
            return templates.findByTemplateKey(templateKey).map(SmsTemplate::isEnabled).orElse(true);
        } catch (Exception ex) {
            log.warn("Could not read SMS switch {}: {}", templateKey, ex.getMessage());
            return true;
        }
    }

    /** The environment switch (SMS_SUREPASS_ENABLED). Shown to admins; it is not something they can change. */
    public boolean isSurepassSwitchedOn() {
        return enabled;
    }

    /** True when this message would really go out through Surepass (switch on, row enabled, template id set). */
    public boolean isActive(String templateKey) {
        if (!enabled) {
            return false;
        }
        try {
            return templates.findByTemplateKey(templateKey)
                    .map(t -> t.isEnabled() && hasText(t.getTemplateId()) && hasText(t.getSenderId()))
                    .orElse(false);
        } catch (Exception ex) {
            log.warn("Could not read SMS template {}: {}", templateKey, ex.getMessage());
            return false;
        }
    }

    /** "Thank you for payment of Rs. {amount} against {biller}, Consumer No. {no}, B-connect Txn ID {txn} on {time} via {mode}." */
    public boolean sendPaymentSuccess(String templateKey, String mobile, BigDecimal amount, String biller, String consumerNo,
                                      String txnId, LocalDateTime when, String mode, String refId) {
        List<String> variables = List.of(
                amount == null ? "-" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                value(biller),
                value(consumerNo),
                value(txnId),
                (when != null ? when : LocalDateTime.now()).format(WHEN),
                value(mode));
        return send(templateKey, mobile, variables, refId);
    }

    /** "Your complaint has been registered successfully for B-connect Txn ID {txn}. Your Complaint ID is {id}. ..." */
    public boolean sendComplaintRegistered(String mobile, String txnId, String complaintId, String refId) {
        return send(BILL_AVENUE_COMPLAINT, mobile, List.of(value(txnId), value(complaintId)), refId);
    }

    /** Sends one template SMS. Returns true only when Surepass accepted it. Never throws. */
    public boolean send(String templateKey, String mobile, List<String> variables, String refId) {
        String number = null;
        try {
            if (!enabled) {
                log.info("Surepass SMS NOT sent: sms.surepass.enabled / SMS_SUREPASS_ENABLED is not true");
                return false;
            }
            SmsTemplate template = templates.findByTemplateKey(templateKey).orElse(null);
            if (template == null || !template.isEnabled() || !hasText(template.getTemplateId())
                    || !hasText(template.getSenderId())) {
                log.info("Surepass SMS NOT sent: template {} is missing, switched off or has no template id", templateKey);
                return false;
            }
            number = tenDigits(mobile);
            if (!INDIAN_MOBILE.matcher(number).matches()) {
                log.warn("Surepass SMS NOT sent: no valid 10-digit Indian mobile number (got {} digits)", number.length());
                return false;
            }
            if (refId != null && logs.existsByTemplateKeyAndRefIdAndStatus(templateKey, refId, SmsLog.SENT)) {
                log.info("Surepass SMS NOT sent again: {} for {} was already sent", templateKey, refId);
                return false;
            }

            VendorCredentialDTO vendor = credentials.getSurepassCredentials();
            if (vendor == null || !hasText(vendor.getBaseUrl()) || !hasText(vendor.getToken())) {
                record(templateKey, refId, number, SmsLog.FAILED, "Surepass base URL or token is not set under Vendor Credentials");
                return false;
            }

            Map<String, String> vars = new LinkedHashMap<>();
            for (int i = 0; i < variables.size(); i++) {
                vars.put("var" + (i + 1), variables.get(i));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("template_id", template.getTemplateId());
            body.put("sender_id", template.getSenderId());
            body.put("mobile", number);
            body.put("variables", vars);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(vendor.getToken());

            JsonNode response = restTemplate
                    .exchange(trimSlash(vendor.getBaseUrl()) + sendPath, HttpMethod.POST,
                            new HttpEntity<>(body, headers), JsonNode.class)
                    .getBody();

            boolean accepted = response != null
                    && (response.path("success").asBoolean(false) || response.path("status_code").asInt(0) == 200);
            record(templateKey, refId, number, accepted ? SmsLog.SENT : SmsLog.FAILED,
                    response != null ? response.toString() : "no response");
            if (accepted) {
                log.info("Surepass SMS accepted: {} for {} to ***{}", templateKey, refId, number.substring(6));
            } else {
                log.warn("Surepass SMS REFUSED: {} for {} to ***{}: {}", templateKey, refId, number.substring(6), response);
            }
            return accepted;
        } catch (Exception ex) {
            String mask = number != null && number.length() == 10 ? "***" + number.substring(6) : "(no number)";
            log.warn("Surepass SMS could not be sent: {} for {} to {}: {}", templateKey, refId, mask, ex.getMessage());
            record(templateKey, refId, number, SmsLog.FAILED, ex.getMessage());
            return false;
        }
    }

    private void record(String templateKey, String refId, String number, String status, String response) {
        try {
            SmsLog row = new SmsLog();
            row.setTemplateKey(templateKey);
            row.setRefId(refId);
            row.setMobileMasked(number != null && number.length() == 10 ? "******" + number.substring(6) : null);
            row.setStatus(status);
            row.setVendorResponse(response == null ? null : response.substring(0, Math.min(response.length(), 500)));
            logs.save(row);
        } catch (Exception ex) {
            log.warn("Could not write the SMS log row: {}", ex.getMessage());
        }
    }

    /** One variable value: no line breaks, no extra spaces, cut to the DLT limit; "-" when empty. */
    static String value(String raw) {
        if (raw == null) {
            return "-";
        }
        String text = raw.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return "-";
        }
        return text.length() > MAX_VARIABLE_LENGTH ? text.substring(0, MAX_VARIABLE_LENGTH).trim() : text;
    }

    private static String tenDigits(String mobile) {
        String number = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (number.length() == 12 && number.startsWith("91")) {
            number = number.substring(2);
        }
        return number;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
