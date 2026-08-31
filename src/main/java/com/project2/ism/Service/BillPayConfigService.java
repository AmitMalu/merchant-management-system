package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project2.ism.DTO.*;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Helper.BillAvenueIVGenerator;
import com.project2.ism.Model.Bbps.BillAvenueConfig;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Repository.BillAvenueConfigRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.response.CommonResponse;
import com.project2.ism.util.BillAvenueApiClient;
import com.project2.ism.util.BillAvenueCryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author SHUBHAM KHOPADE
 */

@Service
@Transactional
public class BillPayConfigService {

    private static final Logger log =
            LoggerFactory.getLogger(BillPayConfigService.class);

    private final BillAvenueConfigRepository billAvenueConfigRepository;
    private final PaymentVendorRepository paymentVendorRepository;
    private final BillAvenueApiClient billAvenueApiClient;
    private final ObjectMapper objectMapper;
    private final PaymentVendorCredentialsService credentialsService;

    public BillPayConfigService(BillAvenueConfigRepository billAvenueConfigRepository,
                                PaymentVendorRepository paymentVendorRepository, BillAvenueApiClient billAvenueApiClient,
                                ObjectMapper objectMapper, PaymentVendorCredentialsService credentialsService) {
        this.billAvenueConfigRepository = billAvenueConfigRepository;
        this.paymentVendorRepository = paymentVendorRepository;
        this.billAvenueApiClient = billAvenueApiClient;
        this.objectMapper = objectMapper;
        this.credentialsService = credentialsService;
    }

    // Per the Bharat Connect reviewer's brand-guideline feedback, only these
    // categories may be shown to the user — the biller-info cache (populated
    // from live Bill Avenue responses) can otherwise pick up extra/unapproved
    // categories over time, so this list is the source of truth, not the cache.
    private static final Set<String> APPROVED_BILL_PAY_CATEGORIES = Set.of(
            "Agent Collection",
            "Broadband Postpaid",
            "Cable TV",
            "Clubs and Associations",
            "Credit Card",
            "DTH",
            "eChallan",
            "Education Fees",
            "Electricity",
            "EV Recharge",
            "Fastag",
            "Fleet Card Recharge",
            "Gas",
            "Housing Society",
            "Insurance",
            "Landline Postpaid",
            "Loan Repayment",
            "LPG Gas",
            "Mobile Postpaid",
            "Mobile Prepaid",
            "Municipal Services",
            "Municipal Taxes",
            "National Pension System",
            "NCMC Recharge",
            "Prepaid Meter",
            "Rental",
            "Subscription",
            "Water"
    );

    @Transactional(readOnly = true)
    public Map<String, Object> getBillAvenueServices(BillPayConfigDTO req) {

        List<BillPayServiceDTO> data = billAvenueConfigRepository
                .findDistinctServicesByVendorName(req.getVendorName())
                .stream()
                .filter(APPROVED_BILL_PAY_CATEGORIES::contains)
                .map(BillPayServiceDTO::new)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", 200);
        response.put("message", "Bill Avenue Service List Fetched Successfully");
        response.put("vendorName", req.getVendorName());
        response.put("data", data);

        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBillAvenueProviders(BillPayProviderRequestDTO req) {

        List<BillPayProviderDTO> providers =
                billAvenueConfigRepository.findProvidersByServiceName(
                        req.getVendorName(),
                        req.getServiceName()
                );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", 200);
        response.put("message", "Bill Avenue Provider List Fetched Successfully");
        response.put("vendorName", req.getVendorName());
        response.put("serviceName", req.getServiceName());
        response.put("data", providers);

        return response;
    }

    @Transactional
    public CommonResponse<Object> fetchBillerInfo(
            FetchBillerInfoRequest request) throws Exception {

        // -------------------------------------------------
        // 1. Validate request
        // -------------------------------------------------

        if (request == null ||
                request.getBillerId() == null ||
                request.getBillerId().isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one billerId is required"
            );
        }

        // -------------------------------------------------
        // 2. Get requested biller IDs
        // -------------------------------------------------

        List<String> requestedBillerIds =
                request.getBillerId()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(id -> !id.isBlank())
                        .distinct()
                        .toList();

        if (requestedBillerIds.isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one valid billerId is required"
            );
        }

        String requestId =
                BillAvenueIVGenerator.generate();

        // -------------------------------------------------
        // 3. Check DB
        // -------------------------------------------------

        List<BillAvenueConfig> existingBillers =
                billAvenueConfigRepository
                        .findByProviderIdIn(
                                requestedBillerIds
                        );

        Map<String, BillAvenueConfig> existingMap =
                existingBillers.stream()
                        .collect(Collectors.toMap(
                                BillAvenueConfig::getProviderId,
                                Function.identity()
                        ));

        // -------------------------------------------------
        // 4. Find missing billers
        // -------------------------------------------------

        List<String> missingBillerIds =
                requestedBillerIds.stream()
                        .filter(id -> !existingMap.containsKey(id))
                        .toList();

        // -------------------------------------------------
        // 5. If everything exists in DB
        // -------------------------------------------------

        if (missingBillerIds.isEmpty()) {

            return CommonResponse.success(
                    buildDbResponse(
                            existingBillers,
                            requestId
                    ),
                    "Biller info fetched from DB"
            );
        }

        // -------------------------------------------------
        // 6. Find active Bill Avenue vendor
        // -------------------------------------------------

        PaymentVendor paymentVendor =
                paymentVendorRepository
                        .findByVendorNameAndStatus(
                                "Bill Avenue",
                                true
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Active Bill Avenue vendor not found"
                                )
                        );

        // -------------------------------------------------
        // 7. Create vendor request
        // -------------------------------------------------

        FetchBillerInfoRequest vendorRequest =
                new FetchBillerInfoRequest(
                        missingBillerIds
                );

        String requestJson =
                objectMapper.writeValueAsString(
                        vendorRequest
                );

        // -------------------------------------------------
        // 8. Call Bill Avenue dynamically
        // -------------------------------------------------

        String decryptedResponse =
                billAvenueApiClient.call(
                        paymentVendor.getId(),
                        BillAvenueApiType.BILLER_INFO,
                        null,
                        requestJson
                );

        // -------------------------------------------------
        // 9. Parse vendor response
        // -------------------------------------------------

        JsonNode responseNode =
                objectMapper.readTree(
                        decryptedResponse
                );

        if (responseNode.isObject()) {

            ((ObjectNode) responseNode)
                    .put("requestId", requestId);
        }

        // -------------------------------------------------
        // 10. Save returned billers
        // -------------------------------------------------

        saveBillerConfigurations(
                paymentVendor,
                responseNode
        );

        // -------------------------------------------------
        // 11. Return vendor response
        // -------------------------------------------------

        return CommonResponse.success(
                responseNode,
                "Biller info fetched from Vendor API"
        );
    }

    private void saveBillerConfigurations(
            PaymentVendor paymentVendor,
            JsonNode responseNode) {

        JsonNode billerArray =
                responseNode.path("biller");

        if (!billerArray.isArray()) {
            return;
        }

        for (JsonNode biller : billerArray) {

            String billerId =
                    biller.path("billerId").asText(null);

            if (billerId == null ||
                    billerId.isBlank()) {
                continue;
            }

            BillAvenueConfig config =
                    new BillAvenueConfig();

            config.setPaymentVendor(
                    paymentVendor
            );

            config.setServiceName(
                    biller.path("billerCategory")
                            .asText()
            );

            config.setProviderId(
                    billerId
            );

            config.setProviderName(
                    biller.path("billerName")
                            .asText()
            );

            config.setRawConfig(
                    biller.toString()
            );

            billAvenueConfigRepository.save(
                    config
            );
        }
    }

    private List<ObjectNode> buildDbResponse(
            List<BillAvenueConfig> billers,
            String requestId) {

        return billers.stream()
                .map(config -> {

                    try {

                        ObjectNode node =
                                (ObjectNode) objectMapper.readTree(
                                        config.getRawConfig()
                                );

                        node.put("requestId", requestId);

                        return node;

                    } catch (Exception e) {

                        throw new RuntimeException(
                                "Invalid raw_config for billerId: "
                                        + config.getProviderId(),
                                e
                        );
                    }
                })
                .toList();
    }

//    @Transactional
//    public Object fetchBill(
//            BillerFetchRequestDTO request) throws Exception {
//
//        String requestId =
//                request != null ? request.getRequestId() : null;
//
//        log.info(
//                "Starting bill fetch process. requestId={}",
//                requestId
//        );
//
//        try {
//
//            // 1. Validate request
//            log.debug(
//                    "Validating bill fetch request. requestId={}",
//                    requestId
//            );
//
//            validateBillFetchRequest(request);
//
//            log.debug(
//                    "Bill fetch request validation successful. requestId={}",
//                    requestId
//            );
//
//
//            // 2. Find active vendor
//            log.debug(
//                    "Fetching active Bill Avenue vendor. requestId={}",
//                    requestId
//            );
//
//            PaymentVendor vendor =
//                    paymentVendorRepository
//                            .findByVendorNameAndStatus(
//                                    "Bill Avenue",
//                                    true
//                            )
//                            .orElseThrow(() ->
//                                    new RuntimeException(
//                                            "Active Bill Avenue vendor not found"
//                                    )
//                            );
//
//            log.info(
//                    "Bill Avenue vendor found. vendorId={}, requestId={}",
//                    vendor.getId(),
//                    requestId
//            );
//
//
//            // 3. Convert request to JSON
//            String requestJson =
//                    objectMapper.writeValueAsString(request);
//
//            log.debug(
//                    "Bill fetch request converted to JSON. requestId={}, payloadSize={}",
//                    requestId,
//                    requestJson != null ? requestJson.length() : 0
//            );
//
//
//            // 4. Call Bill Avenue
//            log.info(
//                    "Calling Bill Avenue API. apiType={}, vendorId={}, requestId={}",
//                    BillAvenueApiType.BILLER_FETCH,
//                    vendor.getId(),
//                    requestId
//            );
//
//            String decryptedResponse =
//                    billAvenueApiClient.call(
//                            vendor.getId(),
//                            BillAvenueApiType.BILLER_FETCH,
//                            requestId,
//                            requestJson
//                    );
//
//            log.info(
//                    "Bill Avenue API call completed. vendorId={}, requestId={}, responseSize={}",
//                    vendor.getId(),
//                    requestId,
//                    decryptedResponse != null
//                            ? decryptedResponse.length()
//                            : 0
//            );
//
//
//            // 5. Parse response
//            Object response =
//                    objectMapper.readTree(decryptedResponse);
//
//            log.info(
//                    "Bill fetch response parsed successfully. requestId={}",
//                    requestId
//            );
//
//            return response;
//
//        } catch (Exception e) {
//
//            log.error(
//                    "Bill fetch failed. requestId={}",
//                    requestId,
//                    e
//            );
//
//            throw e;
//        }
//    }

    public Object fetchBill(BillerFetchRequestDTO request) throws Exception {

        // Initialize logger - THIS SHOULD BE AT CLASS LEVEL, NOT INSIDE METHOD
        // private static final Logger log = LoggerFactory.getLogger(this.getClass());
        // Move this to class level

        LocalDateTime requestTime = LocalDateTime.now();
        String requestJson = null;
        String responseJson = null;
        Long vendorId = null;
        String requestId = request != null ? request.getRequestId() : null;

        // DECLARE CREDENTIALS OUTSIDE TRY BLOCK
        BillAvenueCredentials credentials = null;

        // DECLARE OTHER VARIABLES NEEDED IN CATCH BLOCK
        String encryptedRequest = null;
        String encryptedResponse = null;

        log.info("========== BILL FETCH PROCESS STARTED ==========");
        log.info("Request ID: {}, Request Time: {}", requestId, requestTime);

        try {
            // ================================
            // STEP 1: FETCH VENDOR DETAILS
            // ================================
            log.debug("Fetching active Bill Avenue vendor...");

            PaymentVendor vendor = paymentVendorRepository
                    .findByVendorNameAndStatus("Bill Avenue", true)
                    .orElseThrow(() -> {
                        log.error("Active Bill Avenue vendor not found in database");
                        return new RuntimeException("Active Bill Avenue vendor not found");
                    });

            vendorId = vendor.getId();
            log.info("Vendor found successfully. Vendor ID: {}, Vendor Name: {}",
                    vendorId, vendor.getVendorName());

            // ================================
            // STEP 2: FETCH CREDENTIALS
            // ================================
            log.debug("Fetching Bill Avenue credentials for vendor ID: {}", vendorId);

            credentials = credentialsService
                    .getBillAvenueCredentials(vendorId);

            log.info("Credentials fetched successfully. User ID: {}, Salt Key: [PROTECTED], Encrypt Key: [PROTECTED]",
                    credentials.userId());

            // ================================
            // STEP 3: PREPARE REQUEST OBJECT
            // ================================
            ObjectMapper mapper = new ObjectMapper();

            // Set Agent ID (UAT/Live based on environment)
            log.debug("Setting Agent ID...");
            // UAT
            assert request != null;
            request.setAgentId(credentials.secretKey());
            // Live
            // request.setAgentId("CC01RP98AGTBAK020158");
            log.info("Agent ID set to: {}", request.getAgentId());

            // ================================
            // STEP 4: SET AGENT DEVICE INFO
            // ================================
            log.debug("Setting Agent Device Info...");

            BillerFetchRequestDTO.AgentDeviceInfo deviceInfo = request.getAgentDeviceInfo();
            if (deviceInfo == null) {
                log.debug("AgentDeviceInfo is null, creating new instance");
                deviceInfo = new BillerFetchRequestDTO.AgentDeviceInfo();
            }

            deviceInfo.setIp("192.168.2.73");
            deviceInfo.setInitChannel("INT");
            deviceInfo.setMac("01-23-45-67-89-ab");
            request.setAgentDeviceInfo(deviceInfo);

            log.info("Agent Device Info - IP: {}, Channel: {}, MAC: {}",
                    deviceInfo.getIp(), deviceInfo.getInitChannel(), deviceInfo.getMac());

            // ================================
            // STEP 5: SET CUSTOMER INFO WITH NULL SAFETY
            // ================================
            log.debug("Setting Customer Info with null safety...");

            BillerFetchRequestDTO.CustomerInfo customerInfo = request.getCustomerInfo();
            if (customerInfo == null) {
                log.debug("CustomerInfo is null, creating new instance");
                customerInfo = new BillerFetchRequestDTO.CustomerInfo();
            }

            // Log customer details (mask sensitive info)
            String customerMobile = customerInfo.getCustomerMobile();
            log.info("Customer Mobile: {}", customerMobile != null ? maskMobile(customerMobile) : "null");

            customerInfo.setCustomerEmail(
                    Optional.ofNullable(customerInfo.getCustomerEmail()).orElse("")
            );
            customerInfo.setCustomerAdhaar(
                    Optional.ofNullable(customerInfo.getCustomerAdhaar()).orElse("")
            );
            customerInfo.setCustomerPan(
                    Optional.ofNullable(customerInfo.getCustomerPan()).orElse("")
            );

            request.setCustomerInfo(customerInfo);
            log.debug("Customer Info set successfully");

            // ================================
            // STEP 6: CONVERT REQUEST TO JSON
            // ================================
            log.debug("Converting request to JSON...");

            String plainJson = mapper.writeValueAsString(request);
            requestJson = plainJson;

            log.info("Request JSON generated. Size: {} characters", requestJson.length());
            log.debug("Request JSON: {}", requestJson);

            // ================================
            // STEP 7: ENCRYPT REQUEST
            // ================================
            log.debug("Encrypting request payload...");

            encryptedRequest = BillAvenueCryptoUtil.encrypt(
                    plainJson,
                    credentials.encryptDecryptKey()
            );

            log.info("Request encrypted successfully. Encrypted size: {} characters",
                    encryptedRequest.length());
            log.debug("Encrypted Request: {}", encryptedRequest);

            // ================================
            // STEP 8: PREPARE REQUEST ID
            // ================================
            if (requestId == null || requestId.isEmpty()) {
                log.warn("Request ID is null or empty, generating new one");
                requestId = BillAvenueIVGenerator.generate();
                log.info("Generated new Request ID: {}", requestId);
            } else {
                log.info("Using provided Request ID: {}", requestId);
            }

            // ================================
            // STEP 9: BUILD FINAL API URL
            // ================================
            log.debug("Building API URL...");

            String url = credentials.baseUrl() + "/extBillCntrl/billFetchRequest/json";

            String finalUrl = url
                    + "?accessCode=" + credentials.saltKey()
                    + "&requestId=" + requestId
                    + "&ver=1.0"
                    + "&instituteId=" + credentials.userId()
                    + "&encRequest=" + encryptedRequest;

            log.info("API URL built successfully. URL: {}?accessCode=[PROTECTED]&requestId={}&ver=1.0&instituteId={}&encRequest=[PROTECTED]",
                    url, requestId, credentials.userId());
            log.debug("Full URL (with encrypted request): {}", finalUrl);

            // ================================
            // STEP 10: PREPARE HEADERS
            // ================================
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            log.debug("Headers prepared: Content-Type=text/plain");

            HttpEntity<String> entity = new HttpEntity<>("", headers);
            log.debug("HTTP Entity created with empty body");

            // ================================
            // STEP 11: CREATE REST TEMPLATE
            // ================================
            RestTemplate restTemplate = new RestTemplate();
            log.debug("RestTemplate created");

            // ================================
            // STEP 12: MAKE API CALL
            // ================================
            log.info("Calling Bill Avenue API...");
            log.info("Method: POST, URL: {}?accessCode=[PROTECTED]&requestId={}&ver=1.0&instituteId={}",
                    url, requestId, credentials.userId());

            long startTime = System.currentTimeMillis();

            try {
                encryptedResponse = restTemplate.postForObject(
                        finalUrl,
                        entity,
                        String.class
                );

                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;

                log.info("API call completed. Duration: {} ms", duration);

                if (encryptedResponse == null) {
                    log.error("Received null response from Bill Avenue API");
                    throw new RuntimeException("Received null response from Bill Avenue API");
                }

                log.info("Encrypted response received. Size: {} characters",
                        encryptedResponse.length());
                log.debug("Encrypted Response: {}", encryptedResponse);

            } catch (Exception e) {
                log.error("API call failed. Duration: {} ms", System.currentTimeMillis() - startTime);
                log.error("API call error: {}", e.getMessage(), e);
                throw e;
            }

            // ================================
            // STEP 13: DECRYPT RESPONSE
            // ================================
            log.debug("Decrypting response...");

            String decrypted = null;
            try {
                decrypted = BillAvenueCryptoUtil.decrypt(
                        encryptedResponse,
                        credentials.encryptDecryptKey()
                );

                log.info("Response decrypted successfully. Size: {} characters",
                        decrypted.length());
                log.debug("Decrypted Response: {}", decrypted);

            } catch (Exception e) {
                log.error("Decryption failed. Input length: {}",
                        encryptedResponse.length());
                log.error("Decryption error: {}", e.getMessage(), e);

                // Check if response is JSON error (not encrypted)
                if (encryptedResponse.trim().startsWith("{")) {
                    log.error("Received JSON error response instead of encrypted data");
                    log.error("Error Response: {}", encryptedResponse);

                    // Save log before throwing
                    billAvenueApiClient.saveLog(
                            vendorId,
                            "Bill Fetch Details",
                            requestJson,
                            encryptedRequest,
                            encryptedResponse,
                            200,
                            encryptedResponse // Save error response as is
                    );

                    throw new RuntimeException("Bill Avenue API returned error: " + encryptedResponse);
                }
                throw e;
            }

            // ================================
            // STEP 14: PARSE RESPONSE
            // ================================
            log.debug("Parsing response JSON...");

            JsonNode responseNode = null;
            try {
                responseNode = mapper.readTree(decrypted);
                responseJson = responseNode.toString();

                log.info("Response parsed successfully");

                // Log response code if present
                if (responseNode.has("responseCode")) {
                    String responseCode = responseNode.get("responseCode").asText();
                    log.info("Bill Avenue Response Code: {}", responseCode);

                    if (!"000".equals(responseCode)) {
                        log.warn("Bill Avenue returned non-success response code: {}", responseCode);

                        // Log error details if present
                        if (responseNode.has("errorInfo")) {
                            JsonNode errorInfo = responseNode.get("errorInfo");
                            log.warn("Error Info: {}", errorInfo);
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Failed to parse response JSON: {}", e.getMessage(), e);
                throw e;
            }

            // ================================
            // STEP 15: SAVE SUCCESS LOG
            // ================================
            log.debug("Saving vendor response log (SUCCESS)...");

            billAvenueApiClient.saveLog(
                    vendorId,
                    "Bill Fetch Details",
                    requestJson,
                    encryptedRequest,
                    encryptedResponse,
                    200,
                    responseJson
            );

            log.info("Vendor response log saved successfully");

            // ================================
            // STEP 16: RETURN RESPONSE
            // ================================
            log.info("========== BILL FETCH PROCESS COMPLETED SUCCESSFULLY ==========");
            log.info("Request ID: {}, Response Code: {}",
                    requestId,
                    responseNode.has("responseCode") ?
                            responseNode.get("responseCode").asText() : "N/A");

            return responseNode;

        } catch (Exception e) {

            log.error("========== BILL FETCH PROCESS FAILED ==========");
            log.error("Request ID: {}", requestId);
            log.error("Error: {}", e.getMessage(), e);

            // ================================
            // FAILURE VENDOR LOG - Now credentials is in scope
            // ================================
            try {
                log.debug("Saving vendor response log (FAILURE)...");

                String failureEncryptedRequest = null;
                if (requestJson != null && credentials != null) {
                    try {
                        failureEncryptedRequest = BillAvenueCryptoUtil.encrypt(
                                requestJson,
                                credentials.encryptDecryptKey()
                        );
                    } catch (Exception encEx) {
                        log.warn("Failed to encrypt request for failure log: {}", encEx.getMessage());
                    }
                }

                billAvenueApiClient.saveLog(
                        vendorId != null ? vendorId : 0L,
                        "Bill Fetch Details",
                        requestJson,
                        failureEncryptedRequest,
                        encryptedResponse != null ? encryptedResponse : "",
                        500,
                        e.getMessage()
                );

                log.info("Vendor response log saved for failure");

            } catch (Exception logException) {
                log.error("Failed to save vendor log: {}", logException.getMessage());
            }

            throw e;
        }
    }

// ================================
// HELPER METHODS
// ================================

    /**
     * Masks mobile number for logging (shows only last 4 digits)
     */
    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "****";
        }
        return "****" + mobile.substring(mobile.length() - 4);
    }

    /**
     * Logs request details at a higher level
     */
    private void logRequestDetails(BillerFetchRequestDTO request) {
        if (request == null) {
            log.warn("Request is null");
            return;
        }

        log.info("Request Details:");
        log.info("  - Request ID: {}", request.getRequestId());
        log.info("  - Biller ID: {}", request.getBillerId());
        log.info("  - Agent ID: {}", request.getAgentId());

        if (request.getCustomerInfo() != null) {
            log.info("  - Customer Mobile: {}",
                    maskMobile(request.getCustomerInfo().getCustomerMobile()));
            log.info("  - Customer Email: {}",
                    request.getCustomerInfo().getCustomerEmail() != null ?
                            request.getCustomerInfo().getCustomerEmail() : "Not provided");
        }

        if (request.getInputParams() != null) {
            log.info("  - Input Parameters Count: {}",
                    request.getInputParams().getInput() != null ?
                            request.getInputParams().getInput().size() : 0);
        }
    }

    private void validateBillFetchRequest(
            BillerFetchRequestDTO request) {

        log.debug("Validating bill fetch request");

        if (request == null) {
            log.warn("Bill fetch validation failed: request is null");

            throw new IllegalArgumentException(
                    "Request cannot be null"
            );
        }

        if (request.getBillerId() == null ||
                request.getBillerId().isBlank()) {

            log.warn(
                    "Bill fetch validation failed: billerId is missing. requestId={}",
                    request.getRequestId()
            );

            throw new IllegalArgumentException(
                    "billerId is required"
            );
        }

        if (request.getInputParams() == null ||
                request.getInputParams().getInput() == null ||
                request.getInputParams().getInput().isEmpty()) {

            log.warn(
                    "Bill fetch validation failed: inputParams is missing. " +
                            "billerId={}, requestId={}",
                    request.getBillerId(),
                    request.getRequestId()
            );

            throw new IllegalArgumentException(
                    "inputParams is required"
            );
        }

        log.debug(
                "Bill fetch request validation successful. " +
                        "billerId={}, requestId={}",
                request.getBillerId(),
                request.getRequestId()
        );
    }
}