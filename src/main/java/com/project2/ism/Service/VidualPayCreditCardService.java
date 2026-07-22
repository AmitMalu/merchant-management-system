package com.project2.ism.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.PaymentDTO.VendorApiResponse;
import com.project2.ism.Repository.PaymentVendorResponseLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class VidualPayCreditCardService {

    private final Logger log = LoggerFactory.getLogger(VidualPayCreditCardService.class);

    private final PaymentVendorCredentialsService credentialsService;
    private final PaymentVendorCryptoService cryptoService;
    private final VimoPayClientService vimoPayClientService;
    private final PaymentVendorResponseLogRepository paymentVendorResponseLogRepository;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public VidualPayCreditCardService(PaymentVendorCredentialsService credentialsService,
                                PaymentVendorCryptoService cryptoService, VimoPayClientService vimoPayClientService,
                                PaymentVendorResponseLogRepository paymentVendorResponseLogRepository,
                                ObjectMapper objectMapper,
                                WebClient.Builder webClientBuilder) {
        this.credentialsService = credentialsService;
        this.cryptoService = cryptoService;
        this.vimoPayClientService = vimoPayClientService;
        this.paymentVendorResponseLogRepository = paymentVendorResponseLogRepository;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .build();
    }

    @Value("${vimo.api.path.biller.fetch}")
    private String FETCH_BILLER_API;

    public Object getBillerDetails(String billerCode, Long vendorId) throws JsonProcessingException {

        String baseUrl = credentialsService.getBaseUrl(vendorId);

        String token = vimoPayClientService.obtainTokenForBbps(vendorId, baseUrl, true);

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("billerCode", billerCode);

        String plainJson = objectMapper.writeValueAsString(requestMap);

        String encrypted =
                cryptoService.encryptForVendor(vendorId, plainJson);

        VendorApiResponse response =
                vimoPayClientService.callPostEncrypted(
                        vendorId,
                        baseUrl,
                        FETCH_BILLER_API,
                        token,
                        encrypted,
                        plainJson
                );

        String decrypted =
                cryptoService.decryptFromVendor(
                        vendorId,
                        response.getData()
                );

        log.info("Decrypted Response {} ", decrypted);

        // Check for vendor-level error before attempting decryption
        if (!"000".equals(response.getResponseCode())) {
            log.error("Biller Details API returned error | responseCode={} | message={} | billerCode={}",
                    response.getResponseCode(), response.getMessage(), billerCode);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("responseCode", response.getResponseCode());
            errorResponse.put("message", response.getMessage());
            errorResponse.put("billerCode", billerCode);
            return errorResponse;
        }

        if (response.getData() == null) {
            log.error("Biller Details API returned null data | billerCode={}", billerCode);
            throw new RuntimeException("Empty data in vendor response for billerCode: " + billerCode);
        }

        return objectMapper.readValue(decrypted, Object.class);
    }
}
