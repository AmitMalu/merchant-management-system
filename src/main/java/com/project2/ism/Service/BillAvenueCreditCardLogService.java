package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project2.ism.Model.CreditCard.CreditCardApiLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts every Bill Avenue call into the admin "CCBP Logs" (table credit_card_api_logs), next to the
 * SuivrePay calls, with vendor = BILLAVENUE: who made it, which biller, the card's last 4 digits, the
 * amount, what Bill Avenue answered and how long it took. Mobile numbers, PAN, Aadhaar, e-mail and
 * the values typed into the biller fields are masked before they are stored.
 *
 * Never throws: a logging problem must not affect a payment.
 */
@Service
public class BillAvenueCreditCardLogService {

    private static final Logger log = LoggerFactory.getLogger(BillAvenueCreditCardLogService.class);

    private static final int MAX_STORED_CHARS = 60000;
    private static final Set<String> MASKED_FIELDS = Set.of(
            "customermobile", "customerpan", "customeradhaar", "customeremail", "remitter_name");

    private final CreditCardApiLogService apiLogService;
    private final CallerIdentityService callerIdentity;
    private final ObjectMapper objectMapper;

    public BillAvenueCreditCardLogService(CreditCardApiLogService apiLogService,
                                          CallerIdentityService callerIdentity,
                                          ObjectMapper objectMapper) {
        this.apiLogService = apiLogService;
        this.callerIdentity = callerIdentity;
        this.objectMapper = objectMapper;
    }

    /** The admin log action for a Bill Avenue call (by the name the client logs it under). */
    static String actionFor(String apiName) {
        if (apiName == null) {
            return CreditCardApiLog.UNKNOWN_ACTION;
        }
        return switch (apiName) {
            case "BILLER_INFO" -> CreditCardApiLog.FETCH_BILLERS;
            case "BILL_FETCH", "Bill Fetch Details" -> CreditCardApiLog.FETCH_BILL;
            case "BILL_PAYMENT" -> CreditCardApiLog.PAY_BILL;
            case "COMPLAINT_REGISTER" -> CreditCardApiLog.RAISE_COMPLAINT;
            case "COMPLAINT_TRACK" -> CreditCardApiLog.COMPLAINT_STATUS;
            case "TRANSACTION_STATUS" -> CreditCardApiLog.TXN_STATUS;
            default -> CreditCardApiLog.UNKNOWN_ACTION;
        };
    }

    /**
     * @param requestId  the id the call was sent under (may be null: read from the request body then)
     * @param requestJson the plain (unencrypted) request
     * @param answer      the decrypted answer, or the error text when there was none
     */
    public void record(String apiName, String requestId, String requestJson, Integer httpStatus,
                       String answer, Long timeTakenMs) {
        try {
            CreditCardApiLog entry = new CreditCardApiLog();
            entry.setVendor(CreditCardApiLog.VENDOR_BILL_AVENUE);
            entry.setAction(actionFor(apiName));
            entry.setHttpStatus(httpStatus);
            entry.setTimeTakenMs(timeTakenMs);

            Long merchantId = callerIdentity.currentMerchantIdOrNull();
            entry.setMerchantId(merchantId);
            entry.setCustomerType(merchantId != null ? "MERCHANT" : "SYSTEM");

            JsonNode request = parse(requestJson);
            if (request != null) {
                entry.setBillerId(truncate(request.path("billerId").asText(null), 50));
                entry.setCardLast4(cardLast4(request));
                String amountPaise = request.path("amountInfo").path("amount").asText(null);
                if (amountPaise != null && amountPaise.matches("\\d+")) {
                    entry.setAmount(new BigDecimal(amountPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                }
                if (requestId == null && request.hasNonNull("requestId")) {
                    requestId = request.get("requestId").asText();
                }
                entry.setRequestPayload(truncate(masked(request), MAX_STORED_CHARS));
            } else if (requestJson != null) {
                entry.setRequestPayload(truncate(requestJson, 2000));
            }
            entry.setRequestId(truncate(requestId, 100));

            JsonNode response = parse(answer);
            if (response == null) {
                response = parseEmbedded(answer);
            }
            if (response != null && response.isObject() && response.has("responseCode")
                    || response != null && response.has("complaintResponseCode")) {
                String code = firstText(response, "responseCode", "complaintResponseCode");
                entry.setVendorResponseCode(truncate(code, 20));
                entry.setVendorStatus(truncate(code, 30));

                List<String[]> errors = new ArrayList<>();
                collectErrors(response, errors);
                if (!errors.isEmpty()) {
                    entry.setVendorErrorCode(truncate(errors.get(0)[0], 30));
                    entry.setVendorMessage(truncate(joinMessages(errors), 500));
                } else {
                    entry.setVendorMessage(truncate(firstText(response, "responseReason", "complaintResponseReason"), 500));
                }
                entry.setOutcome("000".equals(code) ? CreditCardApiLog.OUTCOME_SUCCESS : CreditCardApiLog.OUTCOME_REFUSED);
                entry.setResponsePayload(truncate(masked(response), MAX_STORED_CHARS));
            } else {
                // no usable answer: the text is the reason
                entry.setOutcome(CreditCardApiLog.OUTCOME_NO_RESPONSE);
                entry.setErrorMessage(truncate(answer, 500));
                entry.setResponsePayload(truncate(answer, 2000));
            }

            apiLogService.record(entry);
        } catch (Exception e) {
            log.warn("Could not prepare the Bill Avenue credit card log row | api={} | error={}", apiName, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    /** An error text like "Bill Avenue API returned error: {...}": the JSON inside it, if any. */
    private JsonNode parseEmbedded(String text) {
        if (text == null) {
            return null;
        }
        int from = text.indexOf('{');
        int to = text.lastIndexOf('}');
        return from >= 0 && to > from ? parse(text.substring(from, to + 1)) : null;
    }

    private String cardLast4(JsonNode request) {
        for (JsonNode input : request.path("inputParams").path("input")) {
            String name = input.path("paramName").asText("").toLowerCase();
            if (name.contains("last 4") || name.contains("last four")) {
                String digits = input.path("paramValue").asText("").replaceAll("\\D", "");
                return digits.length() >= 4 ? digits.substring(digits.length() - 4) : null;
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            if (node.hasNonNull(f)) {
                return node.get(f).asText();
            }
        }
        return null;
    }

    /** Bill Avenue sends errors in several shapes: errorInfo.error[], errorInfo[].error, vErrorRootVO.error[]. */
    private void collectErrors(JsonNode node, List<String[]> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(n -> collectErrors(n, out));
        } else if (node.isObject()) {
            if (node.hasNonNull("errorMessage")) {
                out.add(new String[]{node.path("errorCode").asText(""), node.get("errorMessage").asText()});
            }
            collectErrors(node.path("errorInfo"), out);
            collectErrors(node.path("vErrorRootVO"), out);
            collectErrors(node.path("error"), out);
        }
    }

    private String joinMessages(List<String[]> errors) {
        List<String> parts = new ArrayList<>();
        for (String[] e : errors) {
            parts.add(e[0].isBlank() ? e[1] : "[" + e[0] + "] " + e[1]);
        }
        return String.join(" | ", parts);
    }

    /** A masked copy as text. */
    private String masked(JsonNode original) {
        JsonNode copy = original.deepCopy();
        mask(copy);
        return copy.toString();
    }

    private void mask(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            // a biller field: {"paramName": "...", "paramValue": "..."}  -> the value is typed by the customer
            if (obj.has("paramName") && obj.has("paramValue") && obj.get("paramValue").isTextual()) {
                obj.put("paramValue", maskValue(obj.get("paramValue").asText()));
            }
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (MASKED_FIELDS.contains(field.getKey().toLowerCase()) && field.getValue().isTextual()) {
                    field.setValue(com.fasterxml.jackson.databind.node.TextNode.valueOf(maskValue(field.getValue().asText())));
                } else {
                    mask(field.getValue());
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            array.forEach(this::mask);
        }
    }

    /** Keeps only the last two characters. */
    static String maskValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 4) {
            return "**";
        }
        return "*".repeat(value.length() - 2) + value.substring(value.length() - 2);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
