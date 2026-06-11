package com.project2.ism.Service;


import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payment.PaymentVendorCredentials;
import com.project2.ism.Repository.PaymentVendorCredentialsRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.request.BankVerificationRequest;
import com.project2.ism.request.PanVerificationRequest;
import com.project2.ism.response.VerificationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class VerificationService {

    private static final String SUREPASS_VENDOR = "External Vendor";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private PaymentVendorRepository paymentVendorRepository;

    @Autowired
    private PaymentVendorCredentialsRepository credentialsRepository;

    // ====================================================
    // PAN VERIFICATION
    // ====================================================

    public VerificationResponse verifyPan(
            PanVerificationRequest request) {

        try {

            PaymentVendorCredentials credentials =
                    getSurepassCredentials();

            String url = getBaseUrl(credentials)
                    + "/api/v1/pan/pan";

            String token = getToken(credentials);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            Map<String, Object> body = new HashMap<>();
            body.put("id_number", request.getPanNumber());

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );

            Map responseBody =
                    response.getBody();

            boolean success = responseBody != null
                    && Boolean.TRUE.equals(
                    responseBody.get("success"));

            if (success) {
                return new VerificationResponse(
                        "SUCCESS",
                        "PAN verified successfully",
                        null
                );
            }

            return new VerificationResponse(
                    "FAILED",
                    "PAN verification failed",
                    null
            );

        } catch (Exception ex) {

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

        try {

            PaymentVendorCredentials credentials =
                    getSurepassCredentials();

            String url = getBaseUrl(credentials)
                    + "/api/v1/bank-verification/";

            String token = getToken(credentials);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            Map<String, Object> body = new HashMap<>();
            body.put("id_number",
                    request.getAccountNumber());
            body.put("ifsc",
                    request.getIfsc());
            body.put("ifsc_details", true);

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );

            Map<String, Object> responseBody =
                    response.getBody();

            boolean success = responseBody != null
                    && Boolean.TRUE.equals(
                    responseBody.get("success"));

            if (success) {

                Map<String, Object> data =
                        (Map<String, Object>) responseBody.get("data");

                String accountHolderName = null;

                if (data != null) {
                    accountHolderName =
                            (String) data.get("full_name");
                }

                return new VerificationResponse(
                        "SUCCESS",
                        "Bank account verified successfully",
                        accountHolderName
                );
            }

            return new VerificationResponse(
                    "FAILED",
                    "Bank account verification failed",
                    null
            );

        } catch (Exception ex) {

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