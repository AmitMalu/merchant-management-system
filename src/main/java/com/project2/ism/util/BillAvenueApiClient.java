package com.project2.ism.util;

/**
 * @author SHUBHAM KHOPADE
 */
import com.project2.ism.DTO.BillAvenueCredentials;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Helper.BillAvenueIVGenerator;
import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import com.project2.ism.Repository.PaymentVendorResponseLogRepository;
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

    private static final Logger log =
            LoggerFactory.getLogger(BillAvenueApiClient.class);

    private final RestTemplate restTemplate;
    private final PaymentVendorCredentialsService credentialsService;
    private final BillAvenuceProperties properties;
    private final PaymentVendorResponseLogRepository paymentVendorResponseLogRepository;

    public BillAvenueApiClient(
            RestTemplate restTemplate,
            PaymentVendorCredentialsService credentialsService,
            BillAvenuceProperties properties,
            PaymentVendorResponseLogRepository paymentVendorResponseLogRepository) {

        this.restTemplate = restTemplate;
        this.credentialsService = credentialsService;
        this.properties = properties;
        this.paymentVendorResponseLogRepository = paymentVendorResponseLogRepository;
    }

    public String call(
            Long vendorId,
            BillAvenueApiType apiType,
            String requestId,
            String requestJson
    ) throws Exception {

        // Generate requestId ONLY for BILLER_INFO
        if (apiType == BillAvenueApiType.BILLER_INFO) {

            if (requestId == null || requestId.isBlank()) {
                requestId = BillAvenueIVGenerator.generate();
            }
        }

        // For other APIs requestId must come from frontend
        else {

            if (requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException(
                        "requestId is required for " + apiType
                );
            }
        }

        BillAvenueCredentials credentials =
                credentialsService
                        .getBillAvenueCredentials(vendorId);

        String encryptedRequest = null;
        String encryptedResponse = null;
        String decryptedResponse = null;

        int statusCode = 500;

        try {

            // -----------------------------------------
            // 1. Encrypt request
            // -----------------------------------------

            encryptedRequest =
                    BillAvenueCryptoUtil.encrypt(
                            requestJson,
                            credentials.encryptDecryptKey()
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

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            String.class
                    );

            statusCode =
                    response.getStatusCode().value();

            encryptedResponse =
                    response.getBody();

            // -----------------------------------------
            // 5. Decrypt
            // -----------------------------------------

            decryptedResponse =
                    BillAvenueCryptoUtil.decrypt(
                            encryptedResponse,
                            credentials.encryptDecryptKey()
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

            return decryptedResponse;

        } catch (Exception e) {

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
            default -> throw new IllegalArgumentException(
                    "Unsupported Bill Avenue API type: "
                            + apiType
            );
        };

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

    private String getApiPath(
            BillAvenueApiType apiType) {

        return switch (apiType) {

            case BILLER_INFO ->
                    properties.getBillerInfoPath();

            case BILLER_FETCH ->
                    properties.getBillerFetchPath();

            case BILL_PAYMENT ->
                    properties.getBillPaymentPath();

            case TRANSACTION_STATUS ->
                    properties.getTransactionStatusPath();
        };
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

            paymentVendorResponseLogRepository.save(
                    logEntry
            );

        } catch (Exception e) {

            log.warn(
                    "Failed to persist Bill Avenue vendor log",
                    e
            );
        }
    }
}
