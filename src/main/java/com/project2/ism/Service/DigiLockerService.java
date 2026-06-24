package com.project2.ism.Service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.DigiLockerDTO.DigilockerInitializeRequestDTO;
import com.project2.ism.DTO.DigiLockerDTO.SurepassInitializeRequest;
import com.project2.ism.DTO.DigiLockerDTO.SurepassInitializeResponse;
import com.project2.ism.DTO.VendorCredentialDTO;
import com.project2.ism.Enum.DigilockerStatus;
import com.project2.ism.Enum.VerificationType;
import com.project2.ism.Model.DigilockerVerification;
import com.project2.ism.Model.Logs.DigilockerLog;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.DigilockerLogRepository;
import com.project2.ism.Repository.DigilockerVerificationRepository;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class DigiLockerService {

    private final MerchantRepository merchantRepository;

    private final FranchiseRepository franchiseRepository;

    private final DigilockerVerificationRepository digilockerVerificationRepository;

    private final PaymentVendorCredentialsService vendorCredentialService;

    private final RestTemplate restTemplate;

    private final DigilockerLogRepository digilockerLogRepository;

    private final ObjectMapper objectMapper;

    private static final Logger log =
            LoggerFactory.getLogger(DigiLockerService.class);

    @Value("{surepass.api.token}")
    private String surepassToken;

    public DigiLockerService(MerchantRepository merchantRepository, FranchiseRepository franchiseRepository,
                             DigilockerVerificationRepository digilockerVerificationRepository,
                             PaymentVendorCredentialsService vendorCredentialService, RestTemplate restTemplate,
                             ObjectMapper objectMapper, DigilockerLogRepository digilockerLogRepository) {
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
        this.digilockerVerificationRepository = digilockerVerificationRepository;
        this.vendorCredentialService = vendorCredentialService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.digilockerLogRepository = digilockerLogRepository;
    }


    private SurepassInitializeRequest buildRequest(
            String fullName,
            String mobile,
            String email,
            String state) {

        log.debug(
                "Building Surepass initialize request for state: {}",
                state
        );

        SurepassInitializeRequest request =
                new SurepassInitializeRequest();

        SurepassInitializeRequest.Data data =
                new SurepassInitializeRequest.Data();

        SurepassInitializeRequest.PrefillOptions options =
                new SurepassInitializeRequest.PrefillOptions();

        options.setFullName(fullName);
        options.setMobileNumber(mobile);
        options.setUserEmail(email);

        data.setPrefillOptions(options);

        data.setExpiryMinutes(10);
        data.setSendSms(false);
        data.setSendEmail(false);

        data.setVerifyPhone(false);
        data.setVerifyEmail(false);

        data.setSignupFlow(false);

        data.setRedirectUrl(
                "http://localhost:5173/digilocker-success"
        );

        data.setState(state);

        request.setData(data);

        return request;
    }

    private SurepassInitializeResponse.DataResponse callInitializeApi(
            String fullName,
            String mobile,
            String email,
            String state) {

        long startTime = System.currentTimeMillis();

        DigilockerLog apiLog = new DigilockerLog();

        log.info(
                "Calling Surepass Digilocker Initialize API. State: {}",
                state
        );

        try {

            VendorCredentialDTO vendor =
                    vendorCredentialService
                            .getSurepassCredentials();

            SurepassInitializeRequest request =
                    buildRequest(
                            fullName,
                            mobile,
                            email,
                            state
                    );

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );

            headers.setBearerAuth(
                    surepassToken
            );

            HttpEntity<SurepassInitializeRequest> entity =
                    new HttpEntity<>(
                            request,
                            headers
                    );

            String requestUrl =
                    vendor.getBaseUrl()
                            + "/api/v1/digilocker/initialize";

            log.info(
                    "Surepass URL : {}",
                    requestUrl
            );

            log.debug(
                    "Surepass Request : {}",
                    objectMapper.writeValueAsString(request)
            );

            ResponseEntity<SurepassInitializeResponse> response =
                    restTemplate.exchange(
                            requestUrl,
                            HttpMethod.POST,
                            entity,
                            SurepassInitializeResponse.class
                    );

            if (response.getBody() == null) {

                log.error(
                        "Surepass returned null response"
                );

                throw new RuntimeException(
                        "Surepass returned empty response"
                );
            }

            log.info(
                    "Surepass API Success. ClientId: {}",
                    response
                            .getBody()
                            .getData()
                            .getClientId()
            );

            apiLog.setApiName(
                    "DIGILOCKER_INITIALIZE"
            );

            apiLog.setRequestUrl(
                    requestUrl
            );

            apiLog.setRequestBody(
                    objectMapper.writeValueAsString(request)
            );

            apiLog.setResponseBody(
                    objectMapper.writeValueAsString(
                            response.getBody()
                    )
            );

            apiLog.setClientId(
                    response
                            .getBody()
                            .getData()
                            .getClientId()
            );

            apiLog.setProcessStatus(
                    "SUCCESS"
            );

            apiLog.setHttpStatus(
                    response
                            .getStatusCode()
                            .value()
            );

            apiLog.setProcessingTimeMs(
                    System.currentTimeMillis()
                            - startTime
            );

            digilockerLogRepository
                    .save(apiLog);

            return response
                    .getBody()
                    .getData();

        } catch (Exception ex) {

            log.error(
                    "Failed to initialize Digilocker for state: {}",
                    state,
                    ex
            );

            apiLog.setApiName(
                    "DIGILOCKER_INITIALIZE"
            );

            apiLog.setProcessStatus(
                    "FAILED"
            );

            apiLog.setErrorMessage(
                    ex.getMessage()
            );

            apiLog.setProcessingTimeMs(
                    System.currentTimeMillis()
                            - startTime
            );

            digilockerLogRepository
                    .save(apiLog);

            throw new RuntimeException(
                    "Failed to initialize Digilocker",
                    ex
            );
        }
    }

    @Transactional
    public void initializeForFranchise(
            Franchise franchise) {

        log.info(
                "Initializing Digilocker for Franchise Id: {}",
                franchise.getId()
        );

        SurepassInitializeResponse.DataResponse data =
                callInitializeApi(
                        franchise.getLegalName(),
                        franchise.getContactPerson()
                                .getPhoneNumber(),
                        franchise.getContactPerson()
                                .getEmail(),
                        "FRANCHISE_" + franchise.getId()
                );

        DigilockerVerification verification =
                digilockerVerificationRepository
                        .findByFranchise(franchise)
                        .orElse(
                                new DigilockerVerification()
                        );

        verification.setMerchant(null);

        verification.setFranchise(franchise);

        verification.setVerificationType(
                VerificationType.FRANCHISE
        );

        verification.setClientId(
                data.getClientId()
        );

        verification.setDigilockerUrl(
                data.getUrl()
        );

        verification.setStatus(
                DigilockerStatus.PENDING
        );

        verification.setCompleted(false);

        verification.setPanAvailable(false);

        verification.setAadhaarLinked(false);

        digilockerVerificationRepository
                .save(verification);

        log.info(
                "Digilocker initialized successfully for Franchise Id: {}",
                franchise.getId()
        );
    }

    @Transactional
    public void initializeForMerchant(

            Merchant merchant,

            VerificationType type

    ) {
        log.info(
                "Initializing Digilocker for Merchant Id: {}, Type: {}",
                merchant.getId(),
                type
        );

        SurepassInitializeResponse.DataResponse data

                =

                callInitializeApi(

                        merchant

                                .getLegalName(),

                        merchant

                                .getContactPerson()

                                .getPhoneNumber(),

                        merchant

                                .getContactPerson()

                                .getEmail(),

                        type.name()

                                +

                                "_"

                                +

                                merchant.getId()

                );

        DigilockerVerification verification

                =

                digilockerVerificationRepository

                        .findByMerchant(

                                merchant

                        )

                        .orElse(

                                new DigilockerVerification()

                        );

        verification.setMerchant(

                merchant

        );

        verification.setFranchise(

                merchant.getFranchise()

        );

        verification.setVerificationType(

                type

        );

        verification.setClientId(

                data.getClientId()

        );

        verification.setDigilockerUrl(

                data.getUrl()

        );

        verification.setStatus(

                DigilockerStatus.PENDING

        );

        verification.setCompleted(false);

        verification.setPanAvailable(false);

        verification.setAadhaarLinked(false);

        digilockerVerificationRepository

                .save(

                        verification

                );

        log.info(
                "Digilocker initialized successfully for Merchant Id: {}",
                merchant.getId()
        );

    }

    @Transactional
    public void resend(

            DigilockerInitializeRequestDTO dto

    ) {

        log.info(
                "Resend Digilocker requested. Id: {}, Type: {}",
                dto.getId(),
                dto.getVerificationType()
        );

        if (

                dto.getVerificationType()

                        ==

                        VerificationType.FRANCHISE

        ) {

            Franchise franchise

                    =

                    franchiseRepository

                            .findById(

                                    dto.getId()

                            )

                            .orElseThrow(

                                    () ->

                                            new RuntimeException(

                                                    "Franchise not found"

                                            )

                            );


            initializeForFranchise(

                    franchise

            );

        } else {

            Merchant merchant

                    =

                    merchantRepository

                            .findById(

                                    dto.getId()

                            )

                            .orElseThrow(

                                    () ->

                                            new RuntimeException(

                                                    "Merchant not found"

                                            )

                            );

            initializeForMerchant(

                    merchant,

                    dto.getVerificationType()

            );

        }

    }
}
