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

    public VerificationResponse verifyPan(PanVerificationRequest request) {

        Long logId = null;
        long start = System.currentTimeMillis();

        try {

            log.info("========== PAN VERIFICATION START ==========");

            PaymentVendorCredentials credentials =
                    getSurepassCredentials();

            String url =
                    getBaseUrl(credentials)
                            + "/api/v1/pan/pan-comprehensive";

            log.info("Surepass PAN URL   : {}", url);
            //log.info("Surepass PAN Token : {}", surepassToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(surepassToken);

            Map<String, Object> body = new HashMap<>();
            body.put("id_number", request.getPanNumber());

            log.info("PAN Request Body : {}", body);

            logId = digilockerLogService.logReceived(
                    null,
                    null,
                    null,
                    "PAN_VERIFY",
                    url,
                    body.toString()
            );

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, headers);

            log.info("Calling Surepass PAN API...");

            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );

            log.info("HTTP Status : {}", response.getStatusCodeValue());

            Map<String, Object> responseBody =
                    response.getBody();

            log.info("Raw PAN Response : {}", responseBody);

            if (responseBody == null) {

                log.error("Surepass returned NULL response");

                return new VerificationResponse(
                        "FAILED",
                        "Empty response received from Surepass",
                        null
                );
            }

            Object successObj =
                    responseBody.get("success");

            log.info("Success Flag : {}", successObj);

            boolean success =
                    Boolean.TRUE.equals(successObj);

            if (!success) {

                log.error("PAN Verification Failed");
                log.error("Response : {}", responseBody);

                digilockerLogService.logFailure(
                        logId,
                        responseBody.toString(),
                        "PAN verification failed",
                        response.getStatusCodeValue(),
                        System.currentTimeMillis() - start
                );

                return new VerificationResponse(
                        "FAILED",
                        "PAN verification failed",
                        responseBody
                );
            }

            Map<String, Object> data =
                    (Map<String, Object>) responseBody.get("data");

            log.info("PAN Data : {}", data);

            if (data == null) {

                log.error("Data object missing in response");

                return new VerificationResponse(
                        "FAILED",
                        "No data received from Surepass",
                        null
                );
            }

            log.info("PAN Number Returned : {}",
                    data.get("pan_number"));

            log.info("Full Name Returned : {}",
                    data.get("full_name"));

            log.info("PAN Status : {}",
                    data.get("pan_status"));

            digilockerLogService.logSuccess(
                    logId,
                    responseBody.toString(),
                    response.getStatusCodeValue(),
                    System.currentTimeMillis() - start
            );

            log.info("========== PAN VERIFIED SUCCESSFULLY ==========");

            return new VerificationResponse(
                    "SUCCESS",
                    "PAN verified successfully",
                    data
            );

        } catch (HttpClientErrorException ex) {

            log.error("========== PAN HTTP ERROR ==========");
            log.error("Status Code : {}", ex.getStatusCode());
            log.error("Response Body : {}", ex.getResponseBodyAsString(), ex);

            if (logId != null) {

                digilockerLogService.logFailure(
                        logId,
                        ex.getResponseBodyAsString(),
                        ex.getMessage(),
                        ex.getStatusCode().value(),
                        System.currentTimeMillis() - start
                );
            }

            return new VerificationResponse(
                    "FAILED",
                    ex.getResponseBodyAsString(),
                    null
            );

        } catch (Exception ex) {

            log.error("========== PAN UNEXPECTED ERROR ==========", ex);

            if (logId != null) {

                digilockerLogService.logFailure(
                        logId,
                        null,
                        ex.getMessage(),
                        500,
                        System.currentTimeMillis() - start
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

    public VerificationResponse verifyBank(BankVerificationRequest request) {

        Long logId = null;
        long start = System.currentTimeMillis();

        try {

            log.info("========== BANK VERIFICATION START ==========");
            log.info("Account Number : {}", request.getAccountNumber());
            log.info("IFSC           : {}", request.getIfsc());

            PaymentVendorCredentials credentials = getSurepassCredentials();

            String url = getBaseUrl(credentials) + "/api/v1/bank-verification/pennyless";

            log.info("Surepass URL   : {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(surepassToken);

            Map<String, Object> body = new HashMap<>();
            body.put("id_number", request.getAccountNumber());
            body.put("ifsc", request.getIfsc());
            body.put("ifsc_details", true);

            log.info("Request Body : {}", body);

            logId = digilockerLogService.logReceived(
                    null,
                    null,
                    null,
                    "BANK_VERIFY",
                    url,
                    body.toString()
            );

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, headers);

            log.info("Calling Surepass API...");

            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );

            log.info("HTTP Status : {}", response.getStatusCodeValue());

            Map<String, Object> responseBody =
                    response.getBody();

            log.info("Raw Response : {}", responseBody);

            if (responseBody == null) {

                log.error("Surepass returned NULL response");

                return new VerificationResponse(
                        "FAILED",
                        "Empty response received from Surepass",
                        null
                );
            }

            Object successObj = responseBody.get("success");

            log.info("Success Flag : {}", successObj);

            boolean success =
                    Boolean.TRUE.equals(successObj);

            if (!success) {

                log.error("Success flag is false");

                digilockerLogService.logFailure(
                        logId,
                        responseBody.toString(),
                        "Bank verification failed",
                        response.getStatusCodeValue(),
                        System.currentTimeMillis() - start
                );

                return new VerificationResponse(
                        "FAILED",
                        "Bank verification failed",
                        responseBody
                );
            }

            Map<String, Object> data =
                    (Map<String, Object>) responseBody.get("data");

            log.info("Data Section : {}", data);

            if (data == null) {

                log.error("Data object missing");

                return new VerificationResponse(
                        "FAILED",
                        "No data received from Surepass",
                        null
                );
            }

            Boolean accountExists =
                    (Boolean) data.get("account_exists");

            String fullName =
                    String.valueOf(data.get("full_name"));

            log.info("Account Exists : {}", accountExists);
            log.info("Account Holder : {}", fullName);

            if (Boolean.TRUE.equals(accountExists)) {

                digilockerLogService.logSuccess(
                        logId,
                        responseBody.toString(),
                        response.getStatusCodeValue(),
                        System.currentTimeMillis() - start
                );

                log.info("========== BANK VERIFIED SUCCESSFULLY ==========");

                return new VerificationResponse(
                        "SUCCESS",
                        "Bank account verified successfully",
                        data
                );
            }

            digilockerLogService.logFailure(
                    logId,
                    responseBody.toString(),
                    "Account does not exist",
                    response.getStatusCodeValue(),
                    System.currentTimeMillis() - start
            );

            log.error("Account Exists Flag = FALSE");

            return new VerificationResponse(
                    "FAILED",
                    "Bank account does not exist",
                    data
            );

        } catch (HttpClientErrorException ex) {

            log.error("========== HTTP ERROR ==========");
            log.error("Status Code : {}", ex.getStatusCode());
            log.error("Response    : {}", ex.getResponseBodyAsString(), ex);

            if (logId != null) {

                digilockerLogService.logFailure(
                        logId,
                        ex.getResponseBodyAsString(),
                        ex.getMessage(),
                        ex.getStatusCode().value(),
                        System.currentTimeMillis() - start
                );
            }

            return new VerificationResponse(
                    "FAILED",
                    ex.getResponseBodyAsString(),
                    null
            );

        } catch (Exception ex) {

            log.error("========== UNEXPECTED ERROR ==========");
            log.error("Exception : ", ex);

            if (logId != null) {

                digilockerLogService.logFailure(
                        logId,
                        null,
                        ex.getMessage(),
                        500,
                        System.currentTimeMillis() - start
                );
            }

            return new VerificationResponse(
                    "FAILED",
                    ex.getMessage(),
                    null
            );
        }
    }

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