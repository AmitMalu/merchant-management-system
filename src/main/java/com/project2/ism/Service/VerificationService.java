package com.project2.ism.Service;


import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payment.PaymentVendorCredentials;
import com.project2.ism.Repository.PaymentVendorCredentialsRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.Repository.VendorStateRepository;
import com.project2.ism.request.BankVerificationRequest;
import com.project2.ism.request.PanVerificationRequest;
import com.project2.ism.response.VerificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class VerificationService {

    private static final Logger log =
            LoggerFactory.getLogger(VerificationService.class);

    private static final String SUREPASS_VENDOR = "Surepass";

    @Value("${surepass.api.token}")
    private String surepassToken;

    private final RestTemplate restTemplate;

    private final PaymentVendorRepository paymentVendorRepository;

    private final PaymentVendorCredentialsRepository credentialsRepository;

    private final DigilockerLogService digilockerLogService;

    public VerificationService(RestTemplate restTemplate, PaymentVendorRepository paymentVendorRepository, PaymentVendorCredentialsRepository credentialsRepository, DigilockerLogService digilockerLogService) {
        this.restTemplate = restTemplate;
        this.paymentVendorRepository = paymentVendorRepository;
        this.credentialsRepository = credentialsRepository;
        this.digilockerLogService = digilockerLogService;
    }

    // ====================================================
    // PAN VERIFICATION
    // ====================================================

    public VerificationResponse verifyPan(
            PanVerificationRequest request) {

        Long logId = null;

        long start =
                System.currentTimeMillis();

        try {

            PaymentVendorCredentials credentials =
                    getSurepassCredentials();

            String url =
                    getBaseUrl(credentials)
                            + "/api/v1/pan/pan-verify";

            log.info(
                    "Surepass PAN URL : {}",
                    url
            );

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );

            headers.setBearerAuth(
                    surepassToken
            );

            Map<String, Object> body =
                    new HashMap<>();

            body.put(
                    "id_number",
                    request.getPanNumber()
            );

            body.put(
                    "full_name",
                    request.getFullName()
            );

            body.put(
                    "dob",
                    "2000-01-01"
            );

            log.info(
                    "PAN Request Body : {}",
                    body
            );

            // ================= LOG REQUEST =================

            logId =
                    digilockerLogService
                            .logReceived(

                                    null, // clientId

                                    null, // merchantId

                                    null, // franchiseId

                                    "PAN_VERIFY",

                                    url,

                                    body.toString()
                            );

            // ===============================================

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(
                            body,
                            headers
                    );

            ResponseEntity<Map> response =
                    restTemplate.exchange(

                            url,

                            HttpMethod.POST,

                            entity,

                            Map.class
                    );

            Map<String, Object> responseBody =
                    response.getBody();

            log.info(
                    "Surepass PAN Response : {}",
                    responseBody
            );

            boolean success =
                    responseBody != null
                            &&
                            Boolean.TRUE.equals(
                                    responseBody.get(
                                            "success"
                                    )
                            );

            if (success) {

                Map<String, Object> data =
                        (Map<String, Object>)
                                responseBody.get(
                                        "data"
                                );

                digilockerLogService
                        .logSuccess(

                                logId,

                                responseBody.toString(),

                                response
                                        .getStatusCodeValue(),

                                System.currentTimeMillis()
                                        - start
                        );

                return new VerificationResponse(

                        "SUCCESS",

                        "PAN verified successfully",

                        data
                );
            }

            digilockerLogService
                    .logFailure(

                            logId,

                            responseBody != null
                                    ? responseBody.toString()
                                    : null,

                            "PAN verification failed",

                            response
                                    .getStatusCodeValue(),

                            System.currentTimeMillis()
                                    - start
                    );

            return new VerificationResponse(

                    "FAILED",

                    "PAN verification failed",

                    null
            );

        }

        catch (HttpClientErrorException ex) {

            log.error(
                    "Surepass Status Verify PAN : {}",
                    ex.getStatusCode()
            );

            log.error(
                    "Surepass Error Body : {}",
                    ex.getResponseBodyAsString()
            );

            if (logId != null) {

                digilockerLogService
                        .logFailure(

                                logId,

                                ex.getResponseBodyAsString(),

                                ex.getMessage(),

                                ex.getStatusCode()
                                        .value(),

                                System.currentTimeMillis()
                                        - start
                        );
            }

            return new VerificationResponse(

                    "FAILED",

                    ex.getResponseBodyAsString(),

                    null
            );
        }

        catch (Exception ex) {

            log.error(
                    "PAN verification failed",
                    ex
            );

            if (logId != null) {

                digilockerLogService
                        .logFailure(

                                logId,

                                null,

                                ex.getMessage(),

                                500,

                                System.currentTimeMillis()
                                        - start
                        );
            }

            return new VerificationResponse(

                    "FAILED",

                    ex.getMessage(),

                    null
            );
        }
    }

    // ====================================================
    // BANK VERIFICATION
    // ====================================================

    public VerificationResponse verifyBank(
            BankVerificationRequest request) {

        Long logId = null;

        long start =
                System.currentTimeMillis();

        try {

            log.info(
                    "Starting Bank Verification for Account: {}, IFSC: {}",
                    request.getAccountNumber(),
                    request.getIfsc()
            );

            PaymentVendorCredentials credentials =
                    getSurepassCredentials();

            String url =
                    getBaseUrl(credentials)
                            + "/api/v1/bank-verification/";

            log.info(
                    "Surepass Bank Verification URL: {}",
                    url
            );

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );

            headers.setBearerAuth(
                    surepassToken
            );

            Map<String, Object> body =
                    new HashMap<>();

            body.put(
                    "id_number",
                    request.getAccountNumber()
            );

            body.put(
                    "ifsc",
                    request.getIfsc()
            );

            body.put(
                    "ifsc_details",
                    true
            );

            log.info(
                    "Bank Verification Request Body: {}",
                    body
            );

            // ================= LOG REQUEST =================

            logId =
                    digilockerLogService
                            .logReceived(

                                    null, // clientId

                                    null, // merchantId

                                    null, // franchiseId

                                    "BANK_VERIFY",

                                    url,

                                    body.toString()
                            );

            // ===============================================

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(
                            body,
                            headers
                    );

            ResponseEntity<Map> response =
                    restTemplate.exchange(

                            url,

                            HttpMethod.POST,

                            entity,

                            Map.class
                    );

            Map<String, Object> responseBody =
                    response.getBody();

            log.info(
                    "Surepass Bank Verification Response: {}",
                    responseBody
            );

            boolean success =
                    responseBody != null
                            &&
                            Boolean.TRUE.equals(
                                    responseBody.get(
                                            "success"
                                    )
                            );

            if (success) {

                Map<String, Object> data =
                        (Map<String, Object>)
                                responseBody.get(
                                        "data"
                                );

                Boolean accountExists =
                        data != null
                                ? (Boolean)
                                data.get(
                                        "account_exists"
                                )
                                : false;

                if (Boolean.TRUE.equals(
                        accountExists
                )) {

                    digilockerLogService
                            .logSuccess(

                                    logId,

                                    responseBody.toString(),

                                    response
                                            .getStatusCodeValue(),

                                    System.currentTimeMillis()
                                            - start
                            );

                    return new VerificationResponse(

                            "SUCCESS",

                            "Bank account verified successfully",

                            data
                    );
                }

                digilockerLogService
                        .logFailure(

                                logId,

                                responseBody.toString(),

                                "Bank account does not exist",

                                response
                                        .getStatusCodeValue(),

                                System.currentTimeMillis()
                                        - start
                        );

                return new VerificationResponse(

                        "FAILED",

                        "Bank account does not exist",

                        null
                );
            }

            digilockerLogService
                    .logFailure(

                            logId,

                            responseBody != null
                                    ? responseBody.toString()
                                    : null,

                            "Bank account verification failed",

                            response
                                    .getStatusCodeValue(),

                            System.currentTimeMillis()
                                    - start
                    );

            return new VerificationResponse(

                    "FAILED",

                    "Bank account verification failed",

                    null
            );

        }

        catch (HttpClientErrorException ex) {

            log.error(
                    "Surepass Status Verify Bank : {}",
                    ex.getStatusCode()
            );

            log.error(
                    "Surepass Response : {}",
                    ex.getResponseBodyAsString()
            );

            if (logId != null) {

                digilockerLogService
                        .logFailure(

                                logId,

                                ex.getResponseBodyAsString(),

                                ex.getMessage(),

                                ex.getStatusCode()
                                        .value(),

                                System.currentTimeMillis()
                                        - start
                        );
            }

            return new VerificationResponse(

                    "FAILED",

                    ex.getResponseBodyAsString(),

                    null
            );

        }

        catch (Exception ex) {

            log.error(
                    "Bank verification failed",
                    ex
            );

            if (logId != null) {

                digilockerLogService
                        .logFailure(

                                logId,

                                null,

                                ex.getMessage(),

                                500,

                                System.currentTimeMillis()
                                        - start
                        );
            }

            return new VerificationResponse(

                    "FAILED",

                    ex.getMessage(),

                    null
            );
        }
    }

    // ====================================================
    // HELPER METHODS
    // ====================================================

    private PaymentVendorCredentials getSurepassCredentials() {

        PaymentVendor vendor =
                paymentVendorRepository
                        .findByVendorNameAndStatus(
                                SUREPASS_VENDOR,
                                true
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "External Vendor not configured"));

        return credentialsRepository
                .findByPaymentVendorAndIsActive(
                        vendor,
                        true
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Vendor credentials not configured"));
    }

    private String getBaseUrl(
            PaymentVendorCredentials credentials) {

        if ("PROD".equalsIgnoreCase(
                credentials.getActiveEnvironment())) {

            return credentials.getBaseUrlProd();
        }

        return credentials.getBaseUrlUat();
    }

    private String getToken(
            PaymentVendorCredentials credentials) {

        if ("PROD".equalsIgnoreCase(
                credentials.getActiveEnvironment())) {

            return credentials.getSecretKeyProd();
        }

        return credentials.getSecretKeyUat();
    }
}