package com.project2.ism.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.MosambeeNotificationDTO;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationCallback;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationLog;
import com.project2.ism.Model.VendorTransactions;
import com.project2.ism.Repository.*;
import com.project2.ism.Security.MosambeeChecksumUtil;
import com.project2.ism.Service.InstantSettlement.InstantSettlementTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MosambeeTransactionService {

    private static final Logger log =
            LoggerFactory.getLogger(RazorpayTransactionService.class);

    @Value("${mosambee.salt}")
    private String salt;

    private final VendorTransactionsRepository vendorTransactionsRepository;
    private final ObjectMapper mapper;
    private final InstantSettlementTrigger instantSettlementTrigger; // NEW
    private final MosambeeNotificationLogService mosambeeNotificationLogService;
    private final FranchiseOrMerchantNotificationCallbackRepository franchiseOrMerchantNotificationCallbackRepo;
    private final FranchiseOrMerchantNotificationLogRepository franchiseOrMerchantNotificationLogRepo;
    private final MerchantRepository merchantRepository;
    private final ProductSerialNumbersRepository productSerialNumbersRepository;
    private final RestTemplate restTemplate;
    private final MosambeeChecksumUtil mosambeeChecksumUtil;

    public MosambeeTransactionService(VendorTransactionsRepository vendorTransactionsRepository, ObjectMapper mapper, InstantSettlementTrigger instantSettlementTrigger,
                                      FranchiseOrMerchantNotificationCallbackRepository franchiseOrMerchantNotificationCallbackRepo, FranchiseOrMerchantNotificationLogRepository franchiseOrMerchantNotificationLogRepo,
                                      MerchantRepository merchantRepository, ProductSerialNumbersRepository productSerialNumbersRepository, RestTemplate restTemplate, MosambeeChecksumUtil mosambeeChecksumUtil,
                                      MosambeeNotificationLogService mosambeeNotificationLogService) {
        this.vendorTransactionsRepository = vendorTransactionsRepository;
        this.mapper = mapper;
        this.instantSettlementTrigger = instantSettlementTrigger;
        this.franchiseOrMerchantNotificationCallbackRepo = franchiseOrMerchantNotificationCallbackRepo;
        this.franchiseOrMerchantNotificationLogRepo = franchiseOrMerchantNotificationLogRepo;
        this.merchantRepository = merchantRepository;
        this.productSerialNumbersRepository = productSerialNumbersRepository;
        this.restTemplate = restTemplate;
        this.mosambeeChecksumUtil = mosambeeChecksumUtil;
        this.mosambeeNotificationLogService = mosambeeNotificationLogService;
    }

    public void process(String rawJson) throws JsonProcessingException {

        long start = System.currentTimeMillis();
        Long logId = null;
        String txnId = null;

        log.info("Mosambee notification processing started");

        try {

            // Step 1: Parse DTO
            log.debug("Parsing Mosambee notification JSON");

            MosambeeNotificationDTO dto =
                    mapper.readValue(
                            rawJson,
                            MosambeeNotificationDTO.class);

            txnId = dto.getTransactionID();

            log.info("Parsed notification successfully. Transaction ID : {}", txnId);


            // Step 2: Validate Checksum
            log.debug("Validating checksum for txnId : {}", txnId);

            validateChecksum(dto);

            log.info("Checksum validated successfully for txnId : {}", txnId);


            // Step 3: Create initial log entry
            log.debug("Creating notification log entry for txnId : {}", txnId);

            logId = mosambeeNotificationLogService.logReceived(rawJson, txnId);

            log.info("Notification log created. LogId : {}, txnId : {}", logId, txnId);

            // Step 4: Find existing transaction
            log.debug("Checking existing transaction for txnId : {}", txnId);

            VendorTransactions existing =
                    vendorTransactionsRepository
                            .findByTransactionReferenceId(txnId)
                            .orElse(null);

            VendorTransactions tx =
                    (existing != null)
                            ? existing
                            : new VendorTransactions();

            log.info("Transaction {} found for txnId : {}",
                    existing != null ? "already" : "not",
                    txnId);


            // Step 5: Map fields
            log.debug("Applying DTO mapping for txnId : {}", txnId);

            applyMapping(dto, tx);

            log.debug("Mapping completed for txnId : {}", txnId);

            // Step 6: Save transaction
            vendorTransactionsRepository.save(tx);

            log.info("Transaction saved successfully. txnId : {}", txnId);

            // Step 7: Success log
            long time = System.currentTimeMillis() - start;

            mosambeeNotificationLogService.logSuccess(logId, time);

            log.info("Notification processed successfully. txnId : {}, Time Taken : {} ms",
                    txnId, time);

            // Step 8: Instant Settlement
            log.info("Checking instant settlement for txnId : {}", txnId);

            instantSettlementTrigger.checkAndTrigger(tx.getTransactionReferenceId());

            log.info("Instant settlement check completed for txnId : {}", txnId);

            // Step 9: Franchise / Merchant Notification
            log.info("Processing franchise/merchant notification for txnId : {}", txnId);

            processFranchiseOrMerchantNotification(tx, rawJson);

            log.info("Franchise/merchant notification completed for txnId : {}", txnId);

        } catch (Exception ex) {

            long time = System.currentTimeMillis() - start;

            log.error(
                    "Error while processing Mosambee notification. txnId : {}, logId : {}, error : {}",
                    txnId,
                    logId,
                    ex.getMessage(),
                    ex);

            if (logId != null) {

                mosambeeNotificationLogService
                        .logFailure(logId, ex.getMessage(), time);

            } else {

                mosambeeNotificationLogService
                        .logFailureNoDTO(rawJson, txnId, ex.getMessage(), time);
            }

            throw new RuntimeException(
                    "Failed to process Mosambee notification: " + ex.getMessage(),
                    ex);
        }
    }

    private void applyMapping(
            MosambeeNotificationDTO dto,
            VendorTransactions tx) {

        tx.setTransactionReferenceId(
                dto.getTransactionID());

        tx.setUsername(dto.getName());

        tx.setConsumer(
                dto.getCardHolderName());

        tx.setAmount(
                new BigDecimal(
                        dto.getTransactionAmount()));

        tx.setTip(
                new BigDecimal(
                        dto.getTipAmount()));

        tx.setCashAtPos(
                new BigDecimal(
                        dto.getCashBack()));

        tx.setTxnType(
                dto.getTransactionTypeName());

        tx.setType(
                dto.getTransactionTypeName());

        tx.setAuthCode(
                dto.getTransactionAuthCode());

        tx.setCard(
                dto.getTransactionCardNumber());

        tx.setCardType(
                dto.getCardType());

        tx.setRrn(
                dto.getTransactionRRN());

        tx.setInvoiceNumber(
                dto.getInvoiceNumber());

        tx.setMerchant(
                dto.getBusinessName());

        tx.setStatus(
                dto.getTransactionStatus());

        tx.setMid(
                dto.getMerchantId());

        tx.setTid(
                dto.getTransactionTerminalId());

        tx.setBatchNumber(
                dto.getTransactionBatchNumber());

        tx.setReferenceTransactionId(
                dto.getRefTxnId());

        tx.setAcquiringBank(
                dto.getAcquirerName());

        tx.setDate(
                parseDate(
                        dto.getTransactionDate(),
                        dto.getTransactionTime()));

        tx.setPaymentGateway("MOSAMBEE");

        tx.setCategory(
                getCategory(dto.getCreditDebitCardType()));

    }

    private String getCategory(String creditDebitCardType) {

        if (creditDebitCardType == null) {
            return null;
        }

        return switch (creditDebitCardType.toUpperCase()) {
            case "DD", "DC" -> "Domestic";
            case "FD", "FC" -> "Foreign";
            default -> "Unknown";
        };
    }


    private LocalDateTime parseDate(
            String date,
            String time) {

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "dd-MM-yyyy HH:mm:ss");

        return LocalDateTime.parse(
                date + " " + time,
                formatter);
    }

    public void processFranchiseOrMerchantNotification(
            VendorTransactions tx, String rawJson) {

        long start = System.currentTimeMillis();
        String txnId = tx.getTransactionReferenceId();

        log.info("Starting notification processing for txnId={}", txnId);

        try {
            // Resolve callback via product serial mapping
            FranchiseOrMerchantNotificationCallback callback =
                    resolveCallbackFromTx(tx);

            // No callback → no DB log, no call
            if (callback == null) {
                log.info("No callback configured for txnId={}, skipping notification", txnId);
                return;
            }

            log.info("Callback resolved for txnId={}, url={}", txnId, callback.getCallback());

            FranchiseOrMerchantNotificationLog logEntity =
                    new FranchiseOrMerchantNotificationLog();

            logEntity.setTxnId(txnId);
            logEntity.setRawJson(rawJson);

            // Call callback
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(rawJson, headers);

            log.info("Sending notification for txnId={}", txnId);

            restTemplate.postForEntity(
                    callback.getCallback(), entity, String.class);

            // Success
            logEntity.setSend(true);
            logEntity.setProcessingTimeMs(System.currentTimeMillis() - start);

            franchiseOrMerchantNotificationLogRepo.save(logEntity);

            log.info("Notification sent successfully for txnId={}, timeMs={}",
                    txnId, logEntity.getProcessingTimeMs());

        } catch (Exception ex) {

            log.error("Notification failed for txnId={}", txnId, ex);

            FranchiseOrMerchantNotificationLog logEntity =
                    new FranchiseOrMerchantNotificationLog();

            logEntity.setTxnId(txnId);
            logEntity.setRawJson(rawJson);
            logEntity.setSend(false);
            logEntity.setErrorMessage(ex.getMessage());
            logEntity.setProcessingTimeMs(System.currentTimeMillis() - start);

            franchiseOrMerchantNotificationLogRepo.save(logEntity);
        }
    }

    private FranchiseOrMerchantNotificationCallback resolveCallbackFromTx(
            VendorTransactions tx) {

        String mid = tx.getMid();

        log.debug("Resolving callback for MID={}", mid);

        ProductSerialNumbers psn = productSerialNumbersRepository
                .findByMid(mid)
                .orElse(null);

        if (psn == null) {
            log.warn("No ProductSerialNumbers found for MID={}", mid);
            return null;
        }

        if (psn.getFranchise() != null) {
            Long franchiseId = psn.getFranchise().getId();

            log.debug("Franchise found for MID={}, franchiseId={}", mid, franchiseId);

            return franchiseOrMerchantNotificationCallbackRepo
                    .findByFranchise_Id(franchiseId)
                    .orElseGet(() -> {
                        log.warn("No callback configured for franchiseId={}", franchiseId);
                        return null;
                    });
        }

        if (psn.getMerchant() != null) {
            Long merchantId = psn.getMerchant().getId();

            log.debug("Merchant found for MID={}, merchantId={}", mid, merchantId);

            return franchiseOrMerchantNotificationCallbackRepo
                    .findByMerchant_Id(merchantId)
                    .orElseGet(() -> {
                        log.warn("No callback configured for merchantId={}", merchantId);
                        return null;
                    });
        }

        log.warn("ProductSerialNumbers has no franchise or merchant for MID={}", mid);
        return null;
    }

    private void validateChecksum(
            MosambeeNotificationDTO dto) {

        String generated =
                mosambeeChecksumUtil.generateChecksum(
                        dto.getTransactionID(),
                        dto.getMerchantId(),
                        dto.getTransactionRRN(),
                        salt);

        if (!generated.equalsIgnoreCase(dto.getChecksum())) {

            throw new RuntimeException(
                    "Checksum validation failed for transaction "
                            + dto.getTransactionID());
        }
    }
}
