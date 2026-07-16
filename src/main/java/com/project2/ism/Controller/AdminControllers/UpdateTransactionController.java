package com.project2.ism.Controller.AdminControllers;


import com.project2.ism.DTO.ReportDTO.ApiResponse;
import com.project2.ism.DTO.ReportDTO.TransactionReportDTO;
import com.project2.ism.Service.AdminServices.UpdateTransactionService;
import com.project2.ism.request.ManualPayoutStatusUpdateRequest;
import com.project2.ism.response.ManualPayoutStatusUpdateResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/update-transaction")
public class UpdateTransactionController {

    private static final Logger logger =
            LoggerFactory.getLogger(UpdateTransactionController.class);

    private final UpdateTransactionService updateTransactionService;

    public UpdateTransactionController(
            UpdateTransactionService updateTransactionService) {
        this.updateTransactionService = updateTransactionService;
    }

    /**
     * Fetch merchant transactions using transaction date.
     *
     * Required:
     * - startDate
     * - endDate
     * - status
     *
     * Optional:
     * - merchantId
     *
     * Example:
     * /update-transaction/merchant-report
     * ?startDate=2026-07-01T00:00:00
     * &endDate=2026-07-15T23:59:59
     * &status=SUCCESS
     * &merchantId=10
     * &page=0
     * &size=50
     */
    @GetMapping("/merchant-report")
    public ResponseEntity<ApiResponse<
            TransactionReportDTO.TransactionReportResponse>>
    getMerchantTransactionReport(

            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,

            @RequestParam(
                    value = "merchantId",
                    required = false
            )
            Long merchantId,

            @RequestParam("status") String status,

            @RequestParam(
                    value = "page",
                    defaultValue = "0"
            )
            @Min(0) Integer page,

            @RequestParam(
                    value = "size",
                    defaultValue = "50"
            )
            @Min(1) @Max(1000) Integer size) {

        logger.info(
                "Received merchant report request. " +
                        "startDate={}, endDate={}, merchantId={}, status={}, page={}, size={}",
                startDate,
                endDate,
                merchantId,
                status,
                page,
                size
        );

        ApiResponse<TransactionReportDTO.TransactionReportResponse>
                apiResponse = new ApiResponse<>();

        try {

            LocalDateTime parsedStartDate =
                    LocalDateTime.parse(startDate);

            LocalDateTime parsedEndDate =
                    LocalDateTime.parse(endDate);

            TransactionReportDTO.TransactionReportResponse report =
                    updateTransactionService
                            .getMerchantTransactionReport(
                                    parsedStartDate,
                                    parsedEndDate,
                                    merchantId,
                                    status,
                                    page,
                                    size
                            );

            apiResponse.setSuccess(true);
            apiResponse.setMessage(
                    "Merchant transaction report fetched successfully"
            );
            apiResponse.setData(report);
            apiResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity.ok(apiResponse);

        } catch (DateTimeParseException exception) {

            logger.warn(
                    "Invalid date format. startDate={}, endDate={}",
                    startDate,
                    endDate
            );

            apiResponse.setSuccess(false);
            apiResponse.setMessage(
                    "Invalid date format. Required format: yyyy-MM-dd'T'HH:mm:ss"
            );
            apiResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(apiResponse);

        } catch (IllegalArgumentException exception) {

            logger.warn(
                    "Invalid merchant report request: {}",
                    exception.getMessage()
            );

            apiResponse.setSuccess(false);
            apiResponse.setMessage(exception.getMessage());
            apiResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(apiResponse);

        } catch (Exception exception) {

            logger.error(
                    "Error while fetching merchant transaction report",
                    exception
            );

            apiResponse.setSuccess(false);
            apiResponse.setMessage(
                    "Failed to fetch merchant transaction report: "
                            + exception.getMessage()
            );
            apiResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(apiResponse);
        }
    }

    @PostMapping("/payout/status")
    public ResponseEntity<ApiResponse<ManualPayoutStatusUpdateResponse>>
    updatePayoutTransactionStatus(
            @Valid @RequestBody ManualPayoutStatusUpdateRequest request) {

        logger.info(
                "Received manual payout update request. status={}, transactionCount={}",
                request.getStatus(),
                request.getTransactionIds() != null
                        ? request.getTransactionIds().size()
                        : 0
        );

        ApiResponse<ManualPayoutStatusUpdateResponse> apiResponse = new ApiResponse<>();

        try {

            ManualPayoutStatusUpdateResponse result =
                    updateTransactionService
                            .updatePayoutTransactionStatuses(
                                    request
                            );

            apiResponse.setSuccess(result.getFailedToProcess() == 0);

            if (result.getFailedToProcess() == 0) {

                apiResponse.setMessage("All payout transactions processed successfully");

            } else if (result.getProcessedSuccessfully() == 0) {

                apiResponse.setMessage("No payout transaction could be processed");

            } else {

                apiResponse.setMessage(
                        "Payout transactions processed partially. "
                                + result.getProcessedSuccessfully()
                                + " succeeded and "
                                + result.getFailedToProcess()
                                + " failed"
                );
            }

            apiResponse.setData(result);
            apiResponse.setTimestamp(LocalDateTime.now());

            /*
             * HTTP 200 is intentional.
             *
             * Individual item errors are returned inside results.
             * The batch request itself was successfully accepted and processed.
             */
            return ResponseEntity.ok(apiResponse);

        } catch (IllegalArgumentException exception) {

            logger.warn(
                    "Invalid manual payout update request: {}",
                    exception.getMessage()
            );

            apiResponse.setSuccess(false);
            apiResponse.setMessage(exception.getMessage());
            apiResponse.setTimestamp(LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiResponse);

        } catch (Exception exception) {

            logger.error(
                    "Unexpected error while processing manual payout updates",
                    exception
            );

            apiResponse.setSuccess(false);
            apiResponse.setMessage(
                    "Failed to process payout updates: "
                            + exception.getMessage()
            );
            apiResponse.setTimestamp(
                    LocalDateTime.now()
            );

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(apiResponse);
        }
    }
}
