package com.project2.ism.Helper;

import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import com.project2.ism.Service.PayoutService;
import com.project2.ism.request.ManualPayoutStatusUpdateRequest;
import com.project2.ism.request.ManualPayoutUpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ManualPayoutTransactionProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(ManualPayoutTransactionProcessor.class);

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final MerchantTransDetRepository merchantTransDetRepository;
    private final FranchiseTransDetRepository franchiseTransDetRepository;

    /*
     * Replace PayoutService with the actual class that currently contains:
     *
     * refundToWallet(payoutTxn)
     *
     * and the new public wrapper:
     *
     * refundPayoutForManualFailure(payoutTxn)
     */
    private final PayoutService payoutService;

    public ManualPayoutTransactionProcessor(
            PayoutTransactionRepository payoutTransactionRepository,
            MerchantTransDetRepository merchantTransDetRepository,
            FranchiseTransDetRepository franchiseTransDetRepository,
            PayoutService payoutService) {

        this.payoutTransactionRepository = payoutTransactionRepository;
        this.merchantTransDetRepository = merchantTransDetRepository;
        this.franchiseTransDetRepository = franchiseTransDetRepository;
        this.payoutService = payoutService;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class
    )
    public ManualPayoutUpdateResult processSingleTransaction(
            Long transactionId,
            ManualPayoutStatusUpdateRequest.ManualPayoutStatus requestedStatus,
            String remarks) {

        log.info(
                "Processing manual payout status update. transactionId={}, requestedStatus={}",
                transactionId,
                requestedStatus
        );

        PayoutTransaction payoutTransaction =
                payoutTransactionRepository
                        .findByIdForManualUpdate(transactionId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Payout transaction not found: " + transactionId
                        ));

        String previousStatus =
                payoutTransaction.getStatus() != null
                        ? payoutTransaction.getStatus().name()
                        : null;

        validateTransactionForManualUpdate(
                payoutTransaction,
                requestedStatus
        );

        boolean walletRefunded = false;

        if (requestedStatus ==
                ManualPayoutStatusUpdateRequest.ManualPayoutStatus.SUCCESS) {

            markTransactionSuccessful(
                    payoutTransaction,
                    remarks
            );

        } else {

            /*
             * Use the existing working refund process.
             *
             * It should:
             * 1. Credit totalDeducted back to the wallet.
             * 2. Create the corresponding refund/credit ledger record.
             * 3. Prevent an incorrect wallet balance.
             */
            payoutService.refundPayoutForManualFailure(
                    payoutTransaction
            );

            walletRefunded = true;

            markTransactionFailed(
                    payoutTransaction,
                    remarks
            );
        }

        PayoutTransaction savedTransaction =
                payoutTransactionRepository.save(
                        payoutTransaction
                );

        updateOriginalLedgerStatus(
                savedTransaction,
                requestedStatus,
                remarks
        );

        log.info(
                "Manual payout status update completed. transactionId={}, previousStatus={}, finalStatus={}, walletRefunded={}",
                transactionId,
                previousStatus,
                savedTransaction.getStatus(),
                walletRefunded
        );

        ManualPayoutUpdateResult result = new ManualPayoutUpdateResult();

        result.setTransactionId(transactionId);
        result.setMerchantRefId(savedTransaction.getMerchantRefId());
        result.setPreviousStatus(previousStatus);
        result.setRequestedStatus(requestedStatus.name());
        result.setFinalStatus(savedTransaction.getStatus().name());
        result.setProcessed(true);
        result.setWalletRefunded(walletRefunded);

        if (requestedStatus == ManualPayoutStatusUpdateRequest.ManualPayoutStatus.SUCCESS) {
            result.setMessage("Payout marked as SUCCESS");
        } else {
            result.setMessage("Payout marked as FAILED and wallet refunded");
        }

        result.setProcessedAt(LocalDateTime.now());

        return result;
    }

    private void validateTransactionForManualUpdate(
            PayoutTransaction payoutTransaction,
            ManualPayoutStatusUpdateRequest.ManualPayoutStatus requestedStatus) {

        if (payoutTransaction.getStatus() == null) {
            throw new IllegalStateException(
                    "Payout transaction has no current status"
            );
        }

        /*
         * Manual status change is allowed only for PENDING.
         *
         * This prevents:
         * - duplicate refunds
         * - SUCCESS being changed to FAILED
         * - FAILED payout being refunded again
         */
        if (payoutTransaction.getStatus() !=
                PayoutTransaction.PayoutStatus.PENDING) {

            throw new IllegalStateException(
                    "Only PENDING transactions can be manually updated. " +
                            "Current status: " +
                            payoutTransaction.getStatus()
            );
        }

        if (requestedStatus == null) {
            throw new IllegalArgumentException(
                    "Requested status is required"
            );
        }

        if (requestedStatus !=
                ManualPayoutStatusUpdateRequest.ManualPayoutStatus.SUCCESS
                &&
                requestedStatus !=
                        ManualPayoutStatusUpdateRequest.ManualPayoutStatus.FAILED) {

            throw new IllegalArgumentException(
                    "Only SUCCESS or FAILED status is allowed"
            );
        }
    }

    private void markTransactionSuccessful(
            PayoutTransaction payoutTransaction,
            String remarks) {

        payoutTransaction.setStatus(
                PayoutTransaction.PayoutStatus.SUCCESS
        );

        payoutTransaction.setCompletedAt(
                LocalDateTime.now()
        );

        payoutTransaction.setResponseMessage(
                buildManualResponseMessage(
                        "Manually marked SUCCESS by admin",
                        remarks
                )
        );
    }

    private void markTransactionFailed(
            PayoutTransaction payoutTransaction,
            String remarks) {

        payoutTransaction.setStatus(
                PayoutTransaction.PayoutStatus.FAILED
        );

        payoutTransaction.setCompletedAt(
                LocalDateTime.now()
        );

        payoutTransaction.setResponseMessage(
                buildManualResponseMessage(
                        "Manually marked FAILED by admin",
                        remarks
                )
        );
    }

    private void updateOriginalLedgerStatus(
            PayoutTransaction payoutTransaction,
            ManualPayoutStatusUpdateRequest.ManualPayoutStatus requestedStatus,
            String remarks) {

        String finalLedgerStatus =
                requestedStatus.name();

        LocalDateTime updatedAt =
                LocalDateTime.now();

        if (payoutTransaction.getLedgerMerchantTxnId() != null) {

            MerchantTransactionDetails merchantTransaction =
                    merchantTransDetRepository
                            .findById(
                                    payoutTransaction
                                            .getLedgerMerchantTxnId()
                            )
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Merchant ledger transaction not found: "
                                                    + payoutTransaction
                                                    .getLedgerMerchantTxnId()
                                    )
                            );

            merchantTransaction.setTranStatus(
                    finalLedgerStatus
            );

            merchantTransaction
                    .setUpdatedDateAndTimeOfTransaction(
                            updatedAt
                    );

            if (requestedStatus ==
                    ManualPayoutStatusUpdateRequest
                            .ManualPayoutStatus.FAILED) {

                merchantTransaction.setFailureRemarks(
                        buildManualResponseMessage(
                                "Manually marked FAILED by admin",
                                remarks
                        )
                );
            }

            merchantTransDetRepository.save(
                    merchantTransaction
            );

            log.info(
                    "Merchant ledger updated. ledgerTransactionId={}, status={}",
                    merchantTransaction.getTransactionId(),
                    finalLedgerStatus
            );
        }

        if (payoutTransaction.getLedgerFranchiseTxnId() != null) {

            FranchiseTransactionDetails franchiseTransaction =
                    franchiseTransDetRepository
                            .findById(
                                    payoutTransaction
                                            .getLedgerFranchiseTxnId()
                            )
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Franchise ledger transaction not found: "
                                                    + payoutTransaction
                                                    .getLedgerFranchiseTxnId()
                                    )
                            );

            franchiseTransaction.setTranStatus(
                    finalLedgerStatus
            );

            franchiseTransaction
                    .setUpdatedDateAndTimeOfTransaction(
                            updatedAt
                    );

            if (requestedStatus ==
                    ManualPayoutStatusUpdateRequest
                            .ManualPayoutStatus.FAILED) {

                franchiseTransaction.setFailureRemarks(
                        buildManualResponseMessage(
                                "Manually marked FAILED by admin",
                                remarks
                        )
                );
            }

            franchiseTransDetRepository.save(
                    franchiseTransaction
            );

            log.info(
                    "Franchise ledger updated. ledgerTransactionId={}, status={}",
                    franchiseTransaction.getTransactionId(),
                    finalLedgerStatus
            );
        }

        if (payoutTransaction.getLedgerMerchantTxnId() == null
                &&
                payoutTransaction.getLedgerFranchiseTxnId() == null) {

            throw new IllegalStateException(
                    "No merchant or franchise ledger transaction is linked " +
                            "with payout transaction: "
                            + payoutTransaction.getId()
            );
        }
    }

    private String buildManualResponseMessage(
            String defaultMessage,
            String remarks) {

        if (remarks == null || remarks.isBlank()) {
            return defaultMessage;
        }

        return defaultMessage + ". Remarks: " + remarks.trim();
    }
}
