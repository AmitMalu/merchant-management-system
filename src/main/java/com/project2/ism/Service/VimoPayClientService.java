package com.project2.ism.Service;



import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.PaymentDTO.*;
import com.project2.ism.DTO.PayoutDTO.PayoutCallback;
import com.project2.ism.DTO.PayoutDTO.PayoutRequest;
import com.project2.ism.DTO.PayoutDTO.PayoutResult;
import com.project2.ism.DTO.PayoutDTO.VimoEncryptedRequest;
import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import com.project2.ism.Repository.PaymentVendorResponseLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class VimoPayClientService {

    private final Logger log = LoggerFactory.getLogger(VimoPayClientService.class);

    private final PaymentVendorCredentialsService credentialsService;
    private final PaymentVendorCryptoService cryptoService;
    private final VendorBankService vendorBankService;
    private final VendorStateService vendorStateService;
    private final VendorPurposeService vendorPurposeService;
    private final VendorBillerCategoryService vendorBillerCategoryService;
    private final PaymentVendorResponseLogRepository paymentVendorResponseLogRepository;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    // Token cache (in-memory)
    private final AtomicReference<String> cachedToken = new AtomicReference<>(null);
    private final AtomicReference<Instant> tokenFetchedAt = new AtomicReference<>(null);

    // Paths (left empty for you to fill during testing)
    @Value("${vimo.api.path.token}")
    private String TOKEN_PATH;

    @Value("${vimo.api.path.banks}")
    private String BANKS_PATH;

    @Value("${vimo.api.path.states}")
    private String STATES_PATH;

    @Value("${vimo.api.path.purposes}")
    private String PURPOSES_PATH;

    @Value("${vimo.api.path.payout}")
    private String PAYOUT_PATH;

    @Value("${vimo.api.path.biller.category.list}")
    private String BILLER_CATEGORY_LIST;

    @Value("${vimo.api.path.biller.code.list}")
    private String BILLER_CODE_LIST;


    public VimoPayClientService(PaymentVendorCredentialsService credentialsService,
                                PaymentVendorCryptoService cryptoService,
                                VendorBankService vendorBankService,
                                VendorStateService vendorStateService,
                                VendorPurposeService vendorPurposeService,
                                VendorBillerCategoryService vendorBillerCategoryService,
                                PaymentVendorResponseLogRepository paymentVendorResponseLogRepository,
                                ObjectMapper objectMapper,
                                WebClient.Builder webClientBuilder) {
        this.credentialsService = credentialsService;
        this.cryptoService = cryptoService;
        this.vendorBankService = vendorBankService;
        this.vendorStateService = vendorStateService;
        this.vendorPurposeService = vendorPurposeService;
        this.vendorBillerCategoryService = vendorBillerCategoryService;
        this.paymentVendorResponseLogRepository = paymentVendorResponseLogRepository;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .build();
    }

    /**
     * Public API to trigger one-time sync: token -> banks -> states -> purposes
     */
    public void fetchAndSaveAll(Long vendorId) {
        // Get base URL
        String baseUrl = credentialsService.getBaseUrl(vendorId);
        if (baseUrl == null) {
            throw new RuntimeException("Base URL is not configured for vendor: " + vendorId);
        }

        // 1) Get token
        String token = obtainToken(vendorId, baseUrl, true);

        // 2) Fetch each list and save to DB (decrypting responses)
        fetchAndSaveBanks(vendorId, baseUrl, token);
        fetchAndSaveStates(vendorId, baseUrl, token);
        fetchAndSavePurposes(vendorId, baseUrl, token);
    }

    /**
     * Public API to trigger one-time sync: token -> banks -> states -> purposes
     */
    public void fetchAndSaveAllCreditCard(Long vendorId) {
        // Get base URL
        String baseUrl = credentialsService.getBaseUrl(vendorId);
        if (baseUrl == null) {
            throw new RuntimeException("Base URL is not configured for vendor: " + vendorId);
        }

        // 1) Get token
        String token = obtainToken(vendorId, baseUrl, true);

        // 2) Fetch each list and save to DB (decrypting responses)
        fetchAndSaveBillerCategoryList(vendorId, baseUrl, token);
       // fetchAndSaveBillerCodeList(vendorId, baseUrl, token);

//        fetchAndSaveBanks(vendorId, baseUrl, token);
//        fetchAndSaveStates(vendorId, baseUrl, token);
//        fetchAndSavePurposes(vendorId, baseUrl, token);
    }

    /* ------------------------ token management ------------------------ */

    /**
     * Obtain token; if forceRefresh==false, try cached token first.
     * If HTTP 401 encountered, and retryOnAuthFail==true, refresh token one time.
     */
    private String obtainToken(Long vendorId, String baseUrl, boolean forceRefresh) {
        if (!forceRefresh) {
            String t = cachedToken.get();
            if (t != null) return t;
        }

        synchronized (this) {
            // double-check
            if (!forceRefresh) {
                String t = cachedToken.get();
                if (t != null) return t;
            }

            String token = callTokenApi(vendorId, baseUrl);
            cachedToken.set(token);
            tokenFetchedAt.set(Instant.now());
            return token;
        }
    }

    private String buildTokenRequestPayload(Long vendorId) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("requestTime", String.valueOf(System.currentTimeMillis()));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build token request payload", e);
        }
    }

    /**
     * Call the vendor token API (no body; headers contain credentials). Returns opaque token (string).
     */
    private String callTokenApi(Long vendorId, String baseUrl) {

        String url = concat(baseUrl, TOKEN_PATH);

        String requestPayload = "N/A";
        String encryptedRequest = "N/A";
        String encryptedResponse = "N/A";
        String decryptedResponse = "N/A";
        int statusCode = 0;

        try {
            // Build request payload
            requestPayload = buildTokenRequestPayload(vendorId);

            // Encrypt request
            encryptedRequest = cryptoService.encryptForVendor(vendorId, requestPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // REQUIRED vendor headers (THIS WAS MISSING)
            headers.set("secretKey", credentialsService.getSecretKey(vendorId));
            headers.set("saltKey", credentialsService.getSaltKey(vendorId));
            headers.set("encryptdecryptKey", credentialsService.getEncryptDecryptKey(vendorId));
            headers.set("userId", credentialsService.getUserId(vendorId));

            //  Encrypted payload (KEEP AS IS)
            headers.set("payload", encryptedRequest);

            long start = System.currentTimeMillis();

            ResponseEntity<String> resp = webClient
                    .post()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .toEntity(String.class)
                    .block(Duration.ofSeconds(30));

            long duration = System.currentTimeMillis() - start;

            if (resp == null || resp.getBody() == null) {
                throw new RuntimeException("Empty response from vendor token API");
            }

            statusCode = resp.getStatusCodeValue();
            encryptedResponse = resp.getBody();

            log.info("Token API response code={} timeMs={}", statusCode, duration);

            // Parse wrapper FIRST
            VendorApiResponse wrapper =
                    objectMapper.readValue(encryptedResponse, VendorApiResponse.class);

                decryptedResponse = encryptedResponse; // store plain error JSON

            // Save FULL log (NO NULLS)
            saveLog(
                    vendorId,
                    "TOKEN",
                    requestPayload,
                    encryptedRequest,
                    encryptedResponse,
                    statusCode,
                    decryptedResponse
            );

            // Parse decrypted response
            VendorApiResponse decryptedWrapper =
                    objectMapper.readValue(decryptedResponse, VendorApiResponse.class);

            if (!"000".equals(decryptedWrapper.getResponseCode()) || decryptedWrapper.getData() == null) {
                throw new RuntimeException("Failed to obtain token: " + decryptedWrapper.getMessage());
            }

            return decryptedWrapper.getData();

        } catch (Exception e) {

            log.error("Token API failed", e);

            // SAVE ERROR LOG ALSO (NO NULLS)
            saveLog(
                    vendorId,
                    "TOKEN",
                    requestPayload,
                    encryptedRequest,
                    encryptedResponse,
                    statusCode,
                    decryptedResponse + " | ERROR: " + e.getMessage()
            );

            throw new RuntimeException("Token API failed", e);
        }
    }


    /* ------------------------ generic GET/POST helpers ------------------------ */

    private VendorApiResponse callGetEncrypted(Long vendorId, String baseUrl, String path, String token) {
        String url = concat(baseUrl, path);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        // userId header is required for non-token APIs per your last message
        headers.set("userId", credentialsService.getUserId(vendorId));
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        try {
            long start = System.currentTimeMillis();
            ResponseEntity<String> resp = webClient.get()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .toEntity(String.class)
                    .block(Duration.ofSeconds(30));
            long duration = System.currentTimeMillis() - start;

            String respBody = resp != null ? resp.getBody() : null;
            int status = resp != null ? resp.getStatusCodeValue() : 0;

            saveLog(vendorId, path, null, null, respBody, status, null);

            if (status == 401) {
                // signal unauthorized to caller
                throw new WebClientResponseException("Unauthorized", 401, "Unauthorized", null, null, null);
            }

            if (respBody == null) {
                throw new RuntimeException("Empty response from vendor for " + path);
            }

            VendorApiResponse wrapper = objectMapper.readValue(respBody, VendorApiResponse.class);
            return wrapper;

        } catch (WebClientResponseException e) {
            log.warn("GET {} returned status {}", path, e.getRawStatusCode());
            throw e;
        } catch (Exception e) {
            log.error("Error calling GET " + path, e);
            throw new RuntimeException(e);
        }
    }

    private VendorApiResponse callPostEncrypted(Long vendorId, String baseUrl, String path,
                                                String token, String encryptedPayload, String plainJson) {

        String url = concat(baseUrl, path);
        log.info("Calling vendor POST API | vendorId={} | url={}", vendorId, url);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        headers.set("userId", credentialsService.getUserId(vendorId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(
                    new VimoEncryptedRequest(encryptedPayload)
            );
            log.debug("Encrypted request body built | vendorId={}", vendorId);
        } catch (Exception ex) {
            log.error("Failed to build encrypted request body | vendorId={}", vendorId, ex);
            throw new RuntimeException("Failed to build encrypted body", ex);
        }

        try {
            long start = System.currentTimeMillis();

            ResponseEntity<String> resp = webClient.post()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .bodyValue(requestBody)
                    .retrieve()
                    .toEntity(String.class)
                    .block(Duration.ofSeconds(30));

            long duration = System.currentTimeMillis() - start;

            String respBody = resp != null ? resp.getBody() : null;
            int status = resp != null ? resp.getStatusCodeValue() : 0;

            log.info("Vendor API response | path={} | status={} | time={}ms",
                    path, status, duration);

            saveLog(vendorId, path, plainJson, requestBody, respBody, status, null);

            if (status == 401) {
                log.warn("Vendor API unauthorized | path={}", path);
                throw new WebClientResponseException(
                        "Unauthorized", 401, "Unauthorized", null, null, null
                );
            }

            if (respBody == null) {
                log.error("Empty response from vendor | path={}", path);
                throw new RuntimeException("Empty response from vendor for " + path);
            }

            VendorApiResponse wrapper =
                    objectMapper.readValue(respBody, VendorApiResponse.class);

            log.debug("Vendor response parsed | responseCode={}",
                    wrapper.getResponseCode());

            return wrapper;

        } catch (WebClientResponseException e) {
            log.warn("Vendor POST failed | path={} | status={}",
                    path, e.getRawStatusCode(), e);
            throw e;

        } catch (Exception e) {
            log.error("Vendor POST exception | path={}", path, e);
            throw new RuntimeException(e);
        }
    }


//    public PayoutCallback handleEncryptedCallback(Long vendorId, Map<String, Object> encryptedBody) {
//        try {
//            String encrypted = (String) encryptedBody.get("data");
//            if (encrypted == null) throw new RuntimeException("Missing encrypted data");
//
//            String decryptedJson = cryptoService.decryptFromVendor(vendorId, encrypted);
//
//            return objectMapper.readValue(decryptedJson, PayoutCallback.class);
//
//        } catch (Exception e) {
//            log.error("Failed to decrypt vendor callback", e);
//            throw new RuntimeException("Invalid callback payload");
//        }
//    }

    public PayoutCallback handleEncryptedCallback(Long vendorId, Map<String, Object> body) {

        String rawJson = null;
        String encryptedBlock = null;
        String decryptedJson = null;

        try {
            // Case 1: encrypted callback: {"data": "<encryptedString>"}
            if (body.containsKey("data") && body.get("data") instanceof String) {

                encryptedBlock = (String) body.get("data");
                decryptedJson = cryptoService.decryptFromVendor(vendorId, encryptedBlock);

                // Log encrypted + decrypted
                saveLog(
                        vendorId,
                        "CALLBACK_ENCRYPTED",
                        objectMapper.writeValueAsString(body),
                        encryptedBlock,
                        null,
                        200,
                        decryptedJson
                );

                return objectMapper.readValue(decryptedJson, PayoutCallback.class);
            }

            // Case 2: plain JSON callback (vendor testing / fallback)
            rawJson = objectMapper.writeValueAsString(body);

            saveLog(
                    vendorId,
                    "CALLBACK_PLAIN",
                    rawJson,
                    null,
                    null,
                    200,
                    rawJson
            );

            return objectMapper.readValue(rawJson, PayoutCallback.class);

        } catch (Exception ex) {

            // Log failure safely
            try {
                rawJson = rawJson == null ? objectMapper.writeValueAsString(body) : rawJson;
                saveLog(
                        vendorId,
                        "CALLBACK_ERROR",
                        rawJson,
                        encryptedBlock,
                        null,
                        500,
                        ex.getMessage()
                );
            } catch (Exception ignore) {}

            throw new RuntimeException("Invalid callback payload", ex);
        }
    }


    /* ------------------------ fetch & save helpers ------------------------ */

    private void fetchAndSaveBanks(Long vendorId, String baseUrl, String token) {
        // Attempt with token; on 401 refresh token once and retry
        try {
            VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, BANKS_PATH, token);
            handleEncryptedListResponseForBanks(vendorId, wrapper);
        } catch (WebClientResponseException e) {
            if (e.getRawStatusCode() == 401) {
                log.info("Banks API 401 -> refreshing token and retrying");
                String newToken = obtainToken(vendorId, baseUrl, true);
                VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, BANKS_PATH, newToken);
                handleEncryptedListResponseForBanks(vendorId, wrapper);
            } else {
                throw e;
            }
        }
    }

    private void fetchAndSaveStates(Long vendorId, String baseUrl, String token) {
        try {
            VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, STATES_PATH, token);
            handleEncryptedListResponseForStates(vendorId, wrapper);
        } catch (WebClientResponseException e) {
            if (e.getRawStatusCode() == 401) {
                log.info("States API 401 -> refreshing token and retrying");
                String newToken = obtainToken(vendorId, baseUrl, true);
                VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, STATES_PATH, newToken);
                handleEncryptedListResponseForStates(vendorId, wrapper);
            } else {
                throw e;
            }
        }
    }

    private void fetchAndSavePurposes(Long vendorId, String baseUrl, String token) {
        try {
            VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, PURPOSES_PATH, token);
            handleEncryptedListResponseForPurposes(vendorId, wrapper);
        } catch (WebClientResponseException e) {
            if (e.getRawStatusCode() == 401) {
                log.info("Purposes API 401 -> refreshing token and retrying");
                String newToken = obtainToken(vendorId, baseUrl, true);
                VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, PURPOSES_PATH, newToken);
                handleEncryptedListResponseForPurposes(vendorId, wrapper);
            } else {
                throw e;
            }
        }
    }

    private void fetchAndSaveBillerCategoryList(Long vendorId, String baseUrl, String token) {
        // Attempt with token; on 401 refresh token once and retry
        try {
            VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, BILLER_CATEGORY_LIST, token);
            handleEncryptedListResponseForBillerCategory(vendorId, wrapper);
        } catch (WebClientResponseException e) {
            if (e.getRawStatusCode() == 401) {
                log.info("Biller Category API 401 -> refreshing token and retrying");
                String newToken = obtainToken(vendorId, baseUrl, true);
                VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, BILLER_CATEGORY_LIST, newToken);
                handleEncryptedListResponseForBillerCategory(vendorId, wrapper);
            } else {
                throw e;
            }
        }
    }

//    private void fetchAndSaveBillerCodeList(Long vendorId, String baseUrl, String token) {
//        // Attempt with token; on 401 refresh token once and retry
//        try {
//            VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, BILLER_CODE_LIST, token);
//            handleEncryptedListResponseForBanks(vendorId, wrapper);
//        } catch (WebClientResponseException e) {
//            if (e.getRawStatusCode() == 401) {
//                log.info("Banks API 401 -> refreshing token and retrying");
//                String newToken = obtainToken(vendorId, baseUrl, true);
//                VendorApiResponse wrapper = callGetEncrypted(vendorId, baseUrl, BANKS_PATH, newToken);
//                handleEncryptedListResponseForBanks(vendorId, wrapper);
//            } else {
//                throw e;
//            }
//        }
//    }

    private void handleEncryptedListResponseForBanks(Long vendorId, VendorApiResponse wrapper) {
        if (wrapper == null) throw new RuntimeException("Empty wrapper for banks");
        if (!"000".equals(wrapper.getResponseCode())) {
            throw new RuntimeException("Biller Category API returned: " + wrapper.getMessage());
        }
        String enc = wrapper.getData();
        String decrypted = cryptoService.decryptFromVendor(vendorId, enc);
        try {
            List<VendorBankDTO> list = objectMapper.readValue(decrypted, new TypeReference<List<VendorBankDTO>>() {});
            // delegate save to your existing service which upserts
            vendorBankService.saveVendorBankList(vendorId, list);
        } catch (Exception e) {
            log.error("Failed to parse bank list", e);
            throw new RuntimeException("Failed to parse bank list", e);
        }
    }

    private void handleEncryptedListResponseForStates(Long vendorId, VendorApiResponse wrapper) {
        if (wrapper == null) throw new RuntimeException("Empty wrapper for states");
        if (!"000".equals(wrapper.getResponseCode())) {
            throw new RuntimeException("States API returned: " + wrapper.getMessage());
        }
        String enc = wrapper.getData();
        String decrypted = cryptoService.decryptFromVendor(vendorId, enc);
        try {
            List<VendorStateDTO> list = objectMapper.readValue(decrypted, new TypeReference<List<VendorStateDTO>>() {});
            vendorStateService.saveVendorStateList(vendorId, list);
        } catch (Exception e) {
            log.error("Failed to parse state list", e);
            throw new RuntimeException("Failed to parse state list", e);
        }
    }

    private void handleEncryptedListResponseForPurposes(Long vendorId, VendorApiResponse wrapper) {
        if (wrapper == null) throw new RuntimeException("Empty wrapper for purposes");
        if (!"000".equals(wrapper.getResponseCode())) {
            throw new RuntimeException("Purposes API returned: " + wrapper.getMessage());
        }
        String enc = wrapper.getData();
        String decrypted = cryptoService.decryptFromVendor(vendorId, enc);
        try {
            List<VendorPurposeDTO> list = objectMapper.readValue(decrypted, new TypeReference<List<VendorPurposeDTO>>() {});
            vendorPurposeService.saveVendorPurposes(vendorId, list);
        } catch (Exception e) {
            log.error("Failed to parse purpose list", e);
            throw new RuntimeException("Failed to parse purpose list", e);
        }
    }

    private void handleEncryptedListResponseForBillerCategory(Long vendorId, VendorApiResponse wrapper) {
        if (wrapper == null) throw new RuntimeException("Empty wrapper for purposes");
        if (!"000".equals(wrapper.getResponseCode())) {
            throw new RuntimeException("Biller Category API returned: " + wrapper.getMessage());
        }
        String enc = wrapper.getData();
        String decrypted = cryptoService.decryptFromVendor(vendorId, enc);
        try {
            List<BillerCategoryListDto> list = objectMapper.readValue(decrypted, new TypeReference<List<BillerCategoryListDto>>() {});
            vendorBillerCategoryService.saveVendorBillerCategoryList(vendorId, list);
        } catch (Exception e) {
            log.error("Failed to parse purpose list", e);
            throw new RuntimeException("Failed to parse purpose list", e);
        }
    }

    // Add these methods to VimoPayClientService.java

    /**
     * Submit payout request to vendor
     */
    public PayoutResult submitPayout(Long vendorId, PayoutRequest request, BigDecimal charges) {
        String baseUrl = credentialsService.getBaseUrl(vendorId);
        if (baseUrl == null) {
            throw new RuntimeException("Base URL is not configured for vendor: " + vendorId);
        }

        // Get token (will use cached if available)
        String token = obtainToken(vendorId, baseUrl, false);

        try {
            return submitPayoutInternal(vendorId, baseUrl, token, request, charges, false);
        } catch (WebClientResponseException e) {
            if (e.getRawStatusCode() == 401) {
                log.info("Payout API 401 -> refreshing token and retrying");
                String newToken = obtainToken(vendorId, baseUrl, true);
                return submitPayoutInternal(vendorId, baseUrl, newToken, request, charges, true);
            } else {
                throw e;
            }
        }
    }

    /**
     * Internal method to submit payout with retry logic
     */
    private PayoutResult submitPayoutInternal(Long vendorId, String baseUrl, String token,
                                              PayoutRequest request, BigDecimal charges,
                                              boolean isRetry) {

        log.info("Submitting payout | vendorId={} | merchantRefId={} | retry={}",
                vendorId, request.getMerchantRefId(), isRetry);

        try {
            // Build request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("amount", request.getAmount());
            payload.put("merchantRefId", request.getMerchantRefId());
            payload.put("beneficiaryBank", request.getBeneficiaryBank());
            payload.put("paymentPurpose", request.getPaymentPurpose());
            payload.put("paymentMode", request.getPaymentMode().toLowerCase());
            payload.put("beneficiaryAccountNumber", request.getBeneficiaryAccountNumber());
            payload.put("beneficiaryIFSC", request.getBeneficiaryIFSC());
            payload.put("beneficiaryMobileNumber", request.getBeneficiaryMobileNumber());
            payload.put("beneficiaryLocation", request.getBeneficiaryLocation());
            payload.put("beneficiaryName", request.getBeneficiaryName());
            payload.put("lat", request.getLat());
            payload.put("long", request.getLongitude());
            payload.put("udf1", "");
            payload.put("udf2", "");
            payload.put("udf3", "");

            String plainJson = objectMapper.writeValueAsString(payload);
            log.debug("Payout plain payload | merchantRefId={} | payload={}",
                    request.getMerchantRefId(), plainJson);

            // Encrypt payload
            String encryptedPayload = cryptoService.encryptForVendor(vendorId, plainJson);
            log.debug("Payout payload encrypted | merchantRefId={}", request.getMerchantRefId());

            // Call vendor API
            VendorApiResponse wrapper = callPostEncrypted(
                    vendorId, baseUrl, PAYOUT_PATH, token, encryptedPayload, plainJson
            );

            log.info("Vendor response received | merchantRefId={} | responseCode={} | message={}",
                    request.getMerchantRefId(),
                    wrapper.getResponseCode(),
                    wrapper.getMessage());

            if (!"000".equals(wrapper.getResponseCode())) {
                log.warn("Vendor returned non-success response | merchantRefId={} | code={}",
                        request.getMerchantRefId(), wrapper.getResponseCode());

                String decryptedData = cryptoService.decryptFromVendor(vendorId, wrapper.getData());
                log.debug("Decrypted vendor error response | merchantRefId={} | data={}",
                        request.getMerchantRefId(), decryptedData);

                return PayoutResult.failed(request.getMerchantRefId(), wrapper.getMessage());
            }

            // Decrypt response
            String decryptedData = cryptoService.decryptFromVendor(vendorId, wrapper.getData());
            log.debug("Decrypted payout response | merchantRefId={} | data={}",
                    request.getMerchantRefId(), decryptedData);

            Map<String, Object> responseData = objectMapper.readValue(
                    decryptedData, new TypeReference<Map<String, Object>>() {}
            );

            String txnId = (String) responseData.get("txnId");
            String txnStatus = (String) responseData.get("txnStatus");
            String statusCode = (String) responseData.get("txnStatusCode");
            String responseMessage = (String) responseData.getOrDefault("responseMessage", null);

            log.info("Parsed vendor response | merchantRefId={} | txnId={} | statusCode={} | status={}",
                    request.getMerchantRefId(), txnId, statusCode, txnStatus);

            String statusCodeNormalized = statusCode != null ? statusCode.trim() : null;
            BigDecimal vendorCharges = parseBigDecimal(responseData.get("charges"));
            BigDecimal vendorAmount = parseBigDecimal(responseData.get("amount"));

            // SUCCESS
            if ("000".equals(statusCodeNormalized)) {
                log.info("Payout SUCCESS | merchantRefId={} | txnId={}",
                        request.getMerchantRefId(), txnId);

                return PayoutResult.ok(
                        txnId,
                        request.getMerchantRefId(),
                        request.getAmount(),
                        charges,
                        vendorCharges != null ? vendorCharges : BigDecimal.ZERO,
                        BigDecimal.ZERO
                );
            }

            // PENDING / QUEUED
            if ("002".equals(statusCodeNormalized) || "004".equals(statusCodeNormalized)) {
                log.info("Payout PENDING | merchantRefId={} | txnId={} | statusCode={}",
                        request.getMerchantRefId(), txnId, statusCodeNormalized);

                PayoutResult pending = PayoutResult.pending(
                        txnId,
                        request.getMerchantRefId(),
                        vendorAmount != null ? vendorAmount : request.getAmount(),
                        charges,
                        vendorCharges != null ? vendorCharges : BigDecimal.ZERO,
                        BigDecimal.ZERO
                );

                pending.setMessage(responseMessage);
                pending.setRrn((String) responseData.getOrDefault("rrn", null));
                return pending;
            }

            // FAILED
            log.warn("Payout FAILED | merchantRefId={} | statusCode={} | message={}",
                    request.getMerchantRefId(), statusCodeNormalized, responseMessage);

            String msg = responseMessage != null ? responseMessage : "Transaction failed";
            return PayoutResult.failed(request.getMerchantRefId(), msg);

        } catch (WebClientResponseException e) {
            log.error("Payout API HTTP error | merchantRefId={} | status={}",
                    request.getMerchantRefId(), e.getRawStatusCode(), e);

            if (!isRetry && e.getRawStatusCode() == 401) {
                throw e;
            }
            return PayoutResult.failed(
                    request.getMerchantRefId(), "API error: " + e.getMessage()
            );

        } catch (Exception e) {
            log.error("Payout submission exception | merchantRefId={}",
                    request.getMerchantRefId(), e);

            return PayoutResult.failed(
                    request.getMerchantRefId(), "Submission failed: " + e.getMessage()
            );
        }
    }


    /* ------------------------ helpers ------------------------ */
    private BigDecimal parseBigDecimal(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Number) return new BigDecimal(((Number) o).toString());
            return new BigDecimal(o.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    private void saveLog(
            Long vendorId,
            String apiName,
            String requestPayload,
            String encryptedRequest,
            String encryptedResponse,
            int statusCode,
            String decryptedResponse) {

        try {
            PaymentVendorResponseLog l = new PaymentVendorResponseLog();

            l.setVendorId(vendorId);
            l.setApiName(apiName);
            l.setRequestPayload(
                    requestPayload != null ? requestPayload : "N/A"
            );
            l.setEncryptedRequest(
                    encryptedRequest != null ? encryptedRequest : "N/A"
            );
            l.setEncryptedResponse(
                    encryptedResponse != null ? encryptedResponse : "N/A"
            );
            l.setDecryptedResponse(
                    decryptedResponse != null ? decryptedResponse : "N/A"
            );
            l.setStatusCode(statusCode);
            l.setCreatedOn(LocalDateTime.now());

            paymentVendorResponseLogRepository.save(l);

        } catch (Exception e) {
            log.warn("Failed to persist vendor log", e);
        }
    }


    private String concat(String base, String path) {
        if (path == null || path.isBlank()) return base;
        if (base.endsWith("/") && path.startsWith("/")) return base + path.substring(1);
        if (!base.endsWith("/") && !path.startsWith("/")) return base + "/" + path;
        return base + path;
    }

    /* Simple wrapper for JSON body containing encrypted data */
    private static class SimpleDataBody {
        public String data;
        public SimpleDataBody(String d) { this.data = d; }
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
    }
}
