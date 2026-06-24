package com.project2.ism.Service;


import com.project2.ism.Model.Logs.DigilockerLog;
import com.project2.ism.Repository.DigilockerLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DigilockerLogService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DigilockerLogService.class
            );

    private final DigilockerLogRepository
            digilockerLogRepository;

    public DigilockerLogService(

            DigilockerLogRepository
                    digilockerLogRepository

    ) {

        this.digilockerLogRepository =
                digilockerLogRepository;
    }


    /**
     * Create log entry immediately
     */

    public Long logReceived(

            String clientId,

            Long merchantId,

            Long franchiseId,

            String apiName,

            String requestUrl,

            String requestBody

    ) {

        DigilockerLog entry =
                new DigilockerLog();

        entry.setClientId(
                clientId
        );

        entry.setMerchantId(
                merchantId
        );

        entry.setFranchiseId(
                franchiseId
        );

        entry.setApiName(
                apiName
        );

        entry.setRequestUrl(
                requestUrl
        );

        entry.setRequestBody(
                requestBody
        );

        entry.setProcessStatus(
                "RECEIVED"
        );

        entry =
                digilockerLogRepository
                        .save(entry);

        return entry.getId();
    }


    /**
     * Success update
     */

    @Async("razorpayNotificationExecutor")
    public void logSuccess(

            Long logId,

            String responseBody,

            Integer httpStatus,

            long timeMs

    ) {

        digilockerLogRepository

                .findById(logId)

                .ifPresent(entry -> {

                    entry.setProcessStatus(
                            "SUCCESS"
                    );

                    entry.setResponseBody(
                            responseBody
                    );

                    entry.setHttpStatus(
                            httpStatus
                    );

                    entry.setProcessingTimeMs(
                            timeMs
                    );

                    digilockerLogRepository
                            .save(entry);

                });

    }


    /**
     * Failure update
     */

    @Async("razorpayNotificationExecutor")
    public void logFailure(

            Long logId,

            String responseBody,

            String error,

            Integer httpStatus,

            long timeMs

    ) {

        digilockerLogRepository

                .findById(logId)

                .ifPresent(entry -> {

                    entry.setProcessStatus(
                            "FAILED"
                    );

                    entry.setResponseBody(
                            responseBody
                    );

                    entry.setErrorMessage(
                            error
                    );

                    entry.setHttpStatus(
                            httpStatus
                    );

                    entry.setProcessingTimeMs(
                            timeMs
                    );

                    digilockerLogRepository
                            .save(entry);

                });

    }


    /**
     * Failure before logId generation
     */

    public void logFailureWithoutId(

            String clientId,

            Long merchantId,

            Long franchiseId,

            String apiName,

            String requestUrl,

            String requestBody,

            String responseBody,

            String error,

            Integer httpStatus,

            long timeMs

    ) {

        DigilockerLog entry =
                new DigilockerLog();

        entry.setClientId(
                clientId
        );

        entry.setMerchantId(
                merchantId
        );

        entry.setFranchiseId(
                franchiseId
        );

        entry.setApiName(
                apiName
        );

        entry.setRequestUrl(
                requestUrl
        );

        entry.setRequestBody(
                requestBody
        );

        entry.setResponseBody(
                responseBody
        );

        entry.setProcessStatus(
                "FAILED"
        );

        entry.setErrorMessage(
                error
        );

        entry.setHttpStatus(
                httpStatus
        );

        entry.setProcessingTimeMs(
                timeMs
        );

        digilockerLogRepository
                .save(entry);
    }

}
