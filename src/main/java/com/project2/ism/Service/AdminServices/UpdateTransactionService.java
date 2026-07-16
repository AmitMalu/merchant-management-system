package com.project2.ism.Service.AdminServices;

import com.project2.ism.DTO.ReportDTO.MerchantTransactionReportDTO;
import com.project2.ism.DTO.ReportDTO.TransactionReportDTO;
import com.project2.ism.Helper.ManualPayoutTransactionProcessor;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.request.ManualPayoutStatusUpdateRequest;
import com.project2.ism.request.ManualPayoutUpdateResult;
import com.project2.ism.response.ManualPayoutStatusUpdateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class UpdateTransactionService {

    private static final Logger logger =
            LoggerFactory.getLogger(UpdateTransactionService.class);

    private final MerchantTransDetRepository merchantTransDetRepository;
    private final ManualPayoutTransactionProcessor manualPayoutTransactionProcessor;

    public UpdateTransactionService(
            MerchantTransDetRepository merchantTransDetRepository,
            ManualPayoutTransactionProcessor manualPayoutTransactionProcessor) {

        this.merchantTransDetRepository =
                merchantTransDetRepository;
        this.manualPayoutTransactionProcessor =
                manualPayoutTransactionProcessor;
    }

    public TransactionReportDTO.TransactionReportResponse
    getMerchantTransactionReport(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long merchantId,
            String status,
            Integer page,
            Integer size) {

        logger.info(
                "Preparing separate merchant transaction report. " +
                        "startDate={}, endDate={}, merchantId={}, status={}, page={}, size={}",
                startDate,
                endDate,
                merchantId,
                status,
                page,
                size
        );

        validateMerchantReportRequest(
                startDate,
                endDate,
                status,
                page,
                size
        );

        try {

            /*
             * ORDER BY is already present in the JPQL query.
             * Therefore sorting is optional here.
             */
            Pageable pageable = PageRequest.of(
                    page,
                    size
            );

            /*
             * transactionType is not present in this API.
             * Therefore null is passed to fetch all transaction types.
             */
            Page<MerchantTransactionReportDTO> transactionPage =
                    merchantTransDetRepository
                            .findPendingMerchantTransactionsByFilters(
                                    startDate,
                                    endDate,
                                    merchantId,
                                    status.trim(),
                                    null,
                                    pageable
                            );

            List<MerchantTransactionReportDTO> transactions =
                    transactionPage.getContent();

            TransactionReportDTO.TransactionReportResponse<
                    MerchantTransactionReportDTO> response =
                    new TransactionReportDTO.TransactionReportResponse<>();

            response.setTransactions(transactions);

            /*
             * This API does not call the existing report service.
             * Add a separate summary query later if summary is required.
             */
            response.setSummary(null);

            response.setReportGeneratedAt(LocalDateTime.now());
            response.setReportType(
                    "PENDING_MERCHANT_TRANSACTION_REPORT"
            );

            response.setTotalPages(
                    transactionPage.getTotalPages()
            );

            response.setTotalElements(
                    transactionPage.getTotalElements()
            );

            response.setHasNext(
                    transactionPage.hasNext()
            );

            response.setHasPrevious(
                    transactionPage.hasPrevious()
            );

            logger.info(
                    "Separate merchant transaction report generated successfully. " +
                            "merchantId={}, status={}, currentPageRecords={}, totalElements={}, totalPages={}",
                    merchantId,
                    status,
                    transactions.size(),
                    transactionPage.getTotalElements(),
                    transactionPage.getTotalPages()
            );

            return response;

        } catch (Exception exception) {

            logger.error(
                    "Error while fetching separate merchant transaction report. " +
                            "startDate={}, endDate={}, merchantId={}, status={}",
                    startDate,
                    endDate,
                    merchantId,
                    status,
                    exception
            );

            throw new RuntimeException(
                    "Failed to fetch merchant transaction report: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private void validateMerchantReportRequest(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String status,
            Integer page,
            Integer size) {

        if (startDate == null) {
            throw new IllegalArgumentException(
                    "startDate is required"
            );
        }

        if (endDate == null) {
            throw new IllegalArgumentException(
                    "endDate is required"
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must be greater than or equal to startDate"
            );
        }

        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "status is required"
            );
        }

        if (page == null || page < 0) {
            throw new IllegalArgumentException(
                    "page cannot be less than 0"
            );
        }

        if (size == null || size < 1 || size > 1000) {
            throw new IllegalArgumentException(
                    "size must be between 1 and 1000"
            );
        }
    }

    public ManualPayoutStatusUpdateResponse updatePayoutTransactionStatuses(
            ManualPayoutStatusUpdateRequest request) {

        logger.info(
                "Starting manual batch payout update. status={}, requestedTransactionCount={}",
                request.getStatus(),
                request.getTransactionIds() != null
                        ? request.getTransactionIds().size()
                        : 0
        );

        validateManualPayoutUpdateRequest(request);

        /*
         * Remove duplicate transaction IDs.
         *
         * Example input:
         * [101, 101, 102]
         *
         * Actual processing:
         * [101, 102]
         */
        List<Long> uniqueTransactionIds =
                request.getTransactionIds()
                        .stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        List<ManualPayoutUpdateResult> results =
                new ArrayList<>();

        for (Long transactionId : uniqueTransactionIds) {

            try {

                /*
                 * Every call enters a different service bean.
                 * Therefore REQUIRES_NEW works correctly.
                 */
                ManualPayoutUpdateResult result =
                        manualPayoutTransactionProcessor
                                .processSingleTransaction(
                                        transactionId,
                                        request.getStatus(),
                                        request.getRemarks()
                                );

                results.add(result);

            } catch (Exception exception) {

                /*
                 * Do not throw from this loop.
                 *
                 * The failed transaction has already rolled back because
                 * processSingleTransaction() uses REQUIRES_NEW.
                 *
                 * Other transaction IDs continue processing.
                 */
                logger.error(
                        "Manual payout update failed for transactionId={}, requestedStatus={}, reason={}",
                        transactionId,
                        request.getStatus(),
                        exception.getMessage(),
                        exception
                );

                results.add(
                        ManualPayoutUpdateResult.failed(
                                transactionId,
                                request.getStatus().name(),
                                exception.getMessage()
                        )
                );
            }
        }

        int processedSuccessfully =
                (int) results.stream()
                        .filter(
                                ManualPayoutUpdateResult::isProcessed
                        )
                        .count();

        int failedToProcess =
                results.size() - processedSuccessfully;

        ManualPayoutStatusUpdateResponse response =new ManualPayoutStatusUpdateResponse();

        response.setRequestedStatus(request.getStatus().name());
        response.setTotalRequested(uniqueTransactionIds.size());
        response.setProcessedSuccessfully(processedSuccessfully);
        response.setFailedToProcess(failedToProcess);
        response.setResults(results);
        response.setProcessedAt(LocalDateTime.now());

        logger.info(
                "Manual batch payout update completed. status={}, total={}, processedSuccessfully={}, failedToProcess={}",
                request.getStatus(),
                uniqueTransactionIds.size(),
                processedSuccessfully,
                failedToProcess
        );

        return response;
    }

    private void validateManualPayoutUpdateRequest(
            ManualPayoutStatusUpdateRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request cannot be null"
            );
        }

        if (request.getTransactionIds() == null
                ||
                request.getTransactionIds().isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one payout transaction ID is required"
            );
        }

        boolean containsNullTransactionId =
                request.getTransactionIds()
                        .stream()
                        .anyMatch(Objects::isNull);

        if (containsNullTransactionId) {
            throw new IllegalArgumentException(
                    "Transaction ID cannot be null"
            );
        }

        if (request.getStatus() == null) {
            throw new IllegalArgumentException(
                    "Status is required"
            );
        }

        if (request.getStatus() !=
                ManualPayoutStatusUpdateRequest
                        .ManualPayoutStatus.SUCCESS
                &&
                request.getStatus() !=
                        ManualPayoutStatusUpdateRequest
                                .ManualPayoutStatus.FAILED) {

            throw new IllegalArgumentException(
                    "Only SUCCESS or FAILED status is allowed"
            );
        }
    }
}
