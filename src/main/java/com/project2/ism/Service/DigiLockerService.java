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


    private SurepassInitializeRequest buildRequest(String fullName, String mobile, String email, String state

    ) {

        SurepassInitializeRequest request = new SurepassInitializeRequest();

        SurepassInitializeRequest.Data data = new SurepassInitializeRequest.Data();
        SurepassInitializeRequest.PrefillOptions options = new SurepassInitializeRequest.PrefillOptions();

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
        data.setRedirectUrl("http://localhost:5173/digilocker-success");
        data.setState(state);
        request.setData(data);

        return request;

    }

    private SurepassInitializeResponse.DataResponse callInitializeApi(

            String fullName,

            String mobile,

            String email,

            String state

    ) {

        long startTime = System.currentTimeMillis();

        DigilockerLog log = new DigilockerLog();

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


            HttpHeaders headers

                    = new HttpHeaders();


            headers.setContentType(

                    MediaType.APPLICATION_JSON

            );

            headers.setBearerAuth(

                    vendor.getToken()

            );

            HttpEntity<SurepassInitializeRequest>

                    entity

                    =

                    new HttpEntity<>(

                            request,

                            headers

                    );


            String requestUrl =

                    vendor.getBaseUrl()

                            +

                            "/api/v1/digilocker/initialize";


            ResponseEntity<SurepassInitializeResponse>

                    response

                    =

                    restTemplate.exchange(

                            requestUrl,

                            HttpMethod.POST,

                            entity,

                            SurepassInitializeResponse.class

                    );


            if (response.getBody() == null) {

                throw new RuntimeException(

                        "Surepass returned empty response"

                );

            }


            // SAVE SUCCESS LOG

            log.setApiName(

                    "DIGILOCKER_INITIALIZE"

            );

            log.setRequestUrl(

                    requestUrl

            );

            log.setRequestBody(

                    objectMapper

                            .writeValueAsString(

                                    request

                            )

            );

            log.setResponseBody(

                    objectMapper

                            .writeValueAsString(

                                    response.getBody()

                            )

            );

            log.setClientId(

                    response

                            .getBody()

                            .getData()

                            .getClientId()

            );

            log.setProcessStatus(

                    "SUCCESS"

            );

            log.setHttpStatus(

                    response

                            .getStatusCode()

                            .value()

            );


            log.setProcessingTimeMs(

                    System.currentTimeMillis()

                            -

                            startTime

            );

            digilockerLogRepository

                    .save(

                            log

                    );


            return response

                    .getBody()

                    .getData();

        } catch (Exception ex) {

            log.setApiName(

                    "DIGILOCKER_INITIALIZE"

            );

            log.setProcessStatus(

                    "FAILED"

            );

            log.setErrorMessage(

                    ex.getMessage()

            );

            log.setProcessingTimeMs(

                    System.currentTimeMillis()

                            -

                            startTime

            );

            digilockerLogRepository

                    .save(

                            log

                    );


            throw new RuntimeException(

                    "Failed to initialize Digilocker",

                    ex

            );

        }

    }

    @Transactional
    public void initializeForFranchise(

            Franchise franchise

    ) {

        SurepassInitializeResponse.DataResponse data

                =

                callInitializeApi(

                        franchise

                                .getLegalName(),

                        franchise

                                .getContactPerson()

                                .getPhoneNumber(),

                        franchise

                                .getContactPerson()

                                .getEmail(),

                        "FRANCHISE_"

                                +

                                franchise.getId()

                );


        DigilockerVerification verification

                =

                digilockerVerificationRepository

                        .findByFranchise(

                                franchise

                        )

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

    }

    @Transactional
    public void initializeForMerchant(

            Merchant merchant,

            VerificationType type

    ) {
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

    }

    @Transactional
    public void resend(

            DigilockerInitializeRequestDTO dto

    ) {

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
