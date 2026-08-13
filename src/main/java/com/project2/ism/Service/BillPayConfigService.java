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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author SHUBHAM KHOPADE
 */

@Service
@Transactional
public class BillPayConfigService {

    private final BillAvenueConfigRepository billAvenueConfigRepository;
    private final PaymentVendorRepository paymentVendorRepository;
    private final BillAvenueApiClient billAvenueApiClient;
    private final ObjectMapper objectMapper;

    public BillPayConfigService(BillAvenueConfigRepository billAvenueConfigRepository,
                                PaymentVendorRepository paymentVendorRepository, BillAvenueApiClient billAvenueApiClient,
                                ObjectMapper objectMapper) {
        this.billAvenueConfigRepository = billAvenueConfigRepository;
        this.paymentVendorRepository = paymentVendorRepository;
        this.billAvenueApiClient = billAvenueApiClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBillAvenueServices(BillPayConfigDTO req) {

        List<BillPayServiceDTO> data = billAvenueConfigRepository
                .findDistinctServicesByVendorName(req.getVendorName())
                .stream()
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

    @Transactional
    public Object fetchBill(
            BillerFetchRequestDTO request) throws Exception {

        validateBillFetchRequest(request);

        PaymentVendor vendor =
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

        String requestJson =
                objectMapper.writeValueAsString(request);

        String decryptedResponse =
                billAvenueApiClient.call(
                        vendor.getId(),
                        BillAvenueApiType.BILLER_FETCH,
                        request.getRequestId(),
                        requestJson
                );

        return objectMapper.readTree(
                decryptedResponse
        );
    }

    private void validateBillFetchRequest(
            BillerFetchRequestDTO request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request cannot be null"
            );
        }

        if (request.getBillerId() == null ||
                request.getBillerId().isBlank()) {

            throw new IllegalArgumentException(
                    "billerId is required"
            );
        }

        if (request.getInputParams() == null ||
                request.getInputParams().getInput() == null ||
                request.getInputParams().getInput().isEmpty()) {

            throw new IllegalArgumentException(
                    "inputParams is required"
            );
        }
    }
}

