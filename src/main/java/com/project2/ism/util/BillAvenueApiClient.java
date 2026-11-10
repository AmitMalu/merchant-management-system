package com.project2.ism.util;

/**
 * @author SHUBHAM KHOPADE
 */
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project2.ism.DTO.BillAvenueCredentials;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Helper.BillAvenueIVGenerator;
import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import com.project2.ism.Service.PaymentVendorCredentialsService;

import com.project2.ism.WebConfig.BillAvenuceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;

@Service
public class BillAvenueApiClient {

    @Value("${bill.avenue.path.biller.info.api}")
    private String billerInfoPath;

    @Value("${bill.avenue.path.biller.fetch.api}")
    private String billerFetchPath;

    @Value("${bill.avenue.path.biller.payment.api}")
    private String billPaymentPath;

    @Value("${bill.avenue.path.txn.status.api}")
    private String transactionStatusPath;

    @Value("${bill.avenue.path.complaint.register.api:/extComplaints/register/json}")
    private String complaintRegisterPath;

    @Value("${bill.avenue.path.complaint.track.api:/extComplaints/track/json}")
    private String complaintTrackPath;

    private static final Logger log =
            LoggerFactory.getLogger(BillAvenueApiClient.class);

    private final RestTemplate restTemplate;
    private final PaymentVendorCredentialsService credentialsService;
    private final BillAvenuceProperties properties;
    private final VendorResponseLogWriter logWriter;
    private final com.project2.ism.Service.BillAvenueCreditCardLogService creditCardLog;
    private final ObjectMapper objectMapper;

    public BillAvenueApiClient(
            RestTemplate restTemplate,
            PaymentVendorCredentialsService credentialsService,
            BillAvenuceProperties properties,
            VendorResponseLogWriter logWriter,
            com.project2.ism.Service.BillAvenueCreditCardLogService creditCardLog,
            ObjectMapper objectMapper) {

        this.restTemplate = restTemplate;
        this.credentialsService = credentialsService;
        this.properties = properties;
        this.logWriter = logWriter;
        this.creditCardLog = creditCardLog;
        this.objectMapper = objectMapper;
    }

    public String call(
            Long vendorId,
            BillAvenueApiType apiType,
            String requestId,
            String requestJson
    ) throws Exception {

        final long callStarted = System.currentTimeMillis();

        log.info(
                "Bill Avenue API call started. vendorId={}, apiType={}, requestId={}",
                vendorId,
                apiType,
                requestId
        );

        // Generate requestId ONLY for BILLER_INFO
        if (apiType == BillAvenueApiType.BILLER_INFO) {

            if (requestId == null || requestId.isBlank()) {

                log.debug(
                        "RequestId not provided for BILLER_INFO. Generating new requestId. vendorId={}",
                        vendorId
                );

                requestId = BillAvenueIVGenerator.generate();

                log.debug(
                        "RequestId generated successfully. vendorId={}, requestId={}",
                        vendorId,
                        requestId
                );
            }
        }

        // For other APIs requestId must come from frontend
        else {

            if (requestId == null || requestId.isBlank()) {

                log.warn(
                        "RequestId missing for Bill Avenue API. vendorId={}, apiType={}",
                        vendorId,
                        apiType
                );

                throw new IllegalArgumentException(
                        "requestId is required for " + apiType
                );
            }
        }

        log.debug(
                "Fetching Bill Avenue credentials. vendorId={}, apiType={}, requestId={}",
                vendorId,
                apiType,
                requestId
        );

        BillAvenueCredentials credentials =
                credentialsService
                        .getBillAvenueCredentials(vendorId);

        log.debug(
                "Bill Avenue credentials loaded successfully. vendorId={}, apiType={}, requestId={}",
                vendorId,
                apiType,
                requestId
        );

        String encryptedRequest = null;
        String encryptedResponse = null;
        String decryptedResponse = null;

        int statusCode = 500;
        boolean creditCardLogged = false;

        try {

            // -----------------------------------------
            // 1. Encrypt request
            // -----------------------------------------

            log.debug(
                    "Encrypting Bill Avenue request. vendorId={}, apiType={}, requestId={}",
                    vendorId,
                    apiType,
                    requestId
            );

            encryptedRequest =
                    BillAvenueCryptoUtil.encrypt(
                            requestJson,
                            credentials.encryptDecryptKey()
                    );

            log.debug(
                    "Bill Avenue request encrypted successfully. vendorId={}, apiType={}, requestId={}, payloadSize={}",
                    vendorId,
                    apiType,
                    requestId,
                    encryptedRequest != null
                            ? encryptedRequest.length()
                            : 0
            );

            // -----------------------------------------
            // 2. Build URL
            // -----------------------------------------

            String url =
                    buildUrl(
                            credentials,
                            apiType,
                            requestId,
                            encryptedRequest
                    );

            log.debug(
                    "Bill Avenue request URL built successfully. vendorId={}, apiType={}, requestId={}",
                    vendorId,
                    apiType,
                    requestId
            );

            // -----------------------------------------
            // 3. Headers
            // -----------------------------------------

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.TEXT_PLAIN
            );

            HttpEntity<String> entity =
                    new HttpEntity<>(
                            "",
                            headers
                    );

            // -----------------------------------------
            // 4. Vendor API call
            // -----------------------------------------

            log.info(
                    "Calling Bill Avenue API. vendorId={}, apiType={}, requestId={}",
                    vendorId,
                    apiType,
                    requestId
            );

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            String.class
                    );

            // ... after getting the response
            encryptedResponse = response.getBody();
            statusCode = response.getStatusCode().value();

            log.info("Bill Avenue API response received. vendorId={}, apiType={}, requestId={}, statusCode={}, responseSize={}",
                    vendorId, apiType, requestId, statusCode, encryptedResponse != null ? encryptedResponse.length() : 0);

            // --- START OF NEW ERROR HANDLING ---
            // Check if the response is a JSON error (like {"responseCode":"202", ...})
            if (encryptedResponse != null && encryptedResponse.trim().startsWith("{")) {
                // It's likely a JSON error response. Don't attempt to decrypt it.
                log.error("Received JSON error response from Bill Avenue, likely authentication or validation error. Response: {}", encryptedResponse);

                // Parse the JSON to get the error code and message
                try {
                    ObjectNode errorJson = (ObjectNode) objectMapper.readTree(encryptedResponse);
                    String errorCode = errorJson.path("responseCode").asText();
                    String errorMessage = errorJson.path("errorInfo").path("error").path(0).path("errorMessage").asText();

                    // Save the log with the error message
                    saveLog(vendorId, apiType.name(), requestJson, encryptedRequest, encryptedResponse, statusCode, "ERROR: " + errorMessage);
                    creditCardLog.record(apiType.name(), requestId, requestJson, statusCode, encryptedResponse,
                            System.currentTimeMillis() - callStarted);
                    creditCardLogged = true;
                    throw new RuntimeException(String.format("Bill Avenue API error. Code: %s, Message: %s", errorCode, errorMessage));
                } catch (Exception e) {
                    // If parsing fails, throw a generic error
                    throw new RuntimeException("Received JSON error response from Bill Avenue: " + encryptedResponse);
                }
            }
            // --- END OF NEW ERROR HANDLING ---

            // Proceed with decryption only if it's not a JSON error
            log.debug("Decrypting Bill Avenue response. vendorId={}, apiType={}, requestId={}", vendorId, apiType, requestId);
            decryptedResponse = BillAvenueCryptoUtil.decrypt(encryptedResponse, credentials.encryptDecryptKey());
            // ...

            log.debug(
                    "Bill Avenue response decrypted successfully. vendorId={}, apiType={}, requestId={}, responseSize={}",
                    vendorId,
                    apiType,
                    requestId,
                    decryptedResponse != null
                            ? decryptedResponse.length()
                            : 0
            );

            // -----------------------------------------
            // 6. SUCCESS vendor log
            // -----------------------------------------

            saveLog(
                    vendorId,
                    apiType.name(),
                    requestJson,
                    encryptedRequest,
                    encryptedResponse,
                    statusCode,
                    decryptedResponse
            );

            creditCardLog.record(apiType.name(), requestId, requestJson, statusCode, decryptedResponse,
                    System.currentTimeMillis() - callStarted);

            log.info(
                    "Bill Avenue API call completed successfully. vendorId={}, apiType={}, requestId={}, statusCode={}",
                    vendorId,
                    apiType,
                    requestId,
                    statusCode
            );

            return decryptedResponse;

        } catch (Exception e) {

            log.error(
                    "Bill Avenue API call failed. vendorId={}, apiType={}, requestId={}, statusCode={}, error={}",
                    vendorId,
                    apiType,
                    requestId,
                    statusCode,
                    e.getMessage(),
                    e
            );

            if (!creditCardLogged) {
                creditCardLog.record(apiType.name(), requestId, requestJson, statusCode,
                        decryptedResponse != null ? decryptedResponse : e.getMessage(),
                        System.currentTimeMillis() - callStarted);
            }

            // -----------------------------------------
            // 7. FAILURE vendor log
            // -----------------------------------------

            saveLog(
                    vendorId,
                    apiType.name(),
                    requestJson,
                    encryptedRequest,
                    encryptedResponse,
                    statusCode,
                    decryptedResponse != null
                            ? decryptedResponse
                            : e.getMessage()
            );

            throw e;
        }
    }

    private String buildUrl(
            BillAvenueCredentials credentials,
            BillAvenueApiType apiType,
            String requestId,
            String encryptedRequest) {

        String path = switch (apiType) {
            case BILLER_INFO -> billerInfoPath;
            case BILLER_FETCH -> billerFetchPath;
            case BILL_PAYMENT -> billPaymentPath;
            case TRANSACTION_STATUS -> transactionStatusPath;
            case COMPLAINT_REGISTER -> complaintRegisterPath;
            case COMPLAINT_TRACK -> complaintTrackPath;

            default -> {
                log.error(
                        "Unsupported Bill Avenue API type. apiType={}, requestId={}",
                        apiType,
                        requestId
                );

                throw new IllegalArgumentException(
                        "Unsupported Bill Avenue API type: "
                                + apiType
                );
            }
        };

        log.debug(
                "Building Bill Avenue URL. apiType={}, path={}, requestId={}",
                apiType,
                path,
                requestId
        );

        return UriComponentsBuilder
                .fromUriString(credentials.baseUrl())
                .path(path)
                .queryParam("accessCode", credentials.saltKey())
                .queryParam("requestId", requestId)
                .queryParam("ver", "1.0")
                .queryParam("instituteId", credentials.userId())
                .queryParam("encRequest", encryptedRequest)
                .build()
                .toUriString();
    }

    static final int MAX_LOGGED_CHARS = 15000;

    static String clip(String value) {
        if (value == null || value.length() <= MAX_LOGGED_CHARS) {
            return value;
        }
        return value.substring(0, MAX_LOGGED_CHARS)
                + "...[shortened, " + (value.length() - MAX_LOGGED_CHARS) + " more characters]";
    }

    /** For callers that make their own Bill Avenue call (the bill fetch): one row in the CCBP admin log. */
    public void logCreditCardCall(String apiName, String requestId, String requestJson, Integer httpStatus,
                                  String answer, Long timeTakenMs) {
        creditCardLog.record(apiName, requestId, requestJson, httpStatus, answer, timeTakenMs);
    }

    public void saveLog(
            Long vendorId,
            String apiName,
            String requestPayload,
            String encryptedRequest,
            String encryptedResponse,
            int statusCode,
            String decryptedResponse) {

        try {

            // The log columns are TEXT (about 64 KB). A very large answer (the full biller list is
            // megabytes) must not make the log insert fail, so long values are shortened.
            requestPayload = clip(requestPayload);
            encryptedRequest = clip(encryptedRequest);
            encryptedResponse = clip(encryptedResponse);
            decryptedResponse = clip(decryptedResponse);

            PaymentVendorResponseLog logEntry =
                    new PaymentVendorResponseLog();

            logEntry.setVendorId(vendorId);

            logEntry.setApiName(apiName);

            logEntry.setRequestPayload(
                    requestPayload != null
                            ? requestPayload
                            : "N/A"
            );

            logEntry.setEncryptedRequest(
                    encryptedRequest != null
                            ? encryptedRequest
                            : "N/A"
            );

            logEntry.setEncryptedResponse(
                    encryptedResponse != null
                            ? encryptedResponse
                            : "N/A"
            );

            logEntry.setDecryptedResponse(
                    decryptedResponse != null
                            ? decryptedResponse
                            : "N/A"
            );

            logEntry.setStatusCode(statusCode);

            logEntry.setCreatedOn(
                    LocalDateTime.now()
            );

            // own transaction: a log problem must never affect the request that wrote it
            logWriter.write(logEntry);

        } catch (Exception e) {

            log.warn(
                    "Failed to persist Bill Avenue vendor log",
                    e
            );
        }
    }
}
