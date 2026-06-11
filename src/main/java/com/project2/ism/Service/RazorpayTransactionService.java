package com.project2.ism.Service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.DTO.RazorPay.RazorpayNotificationDTO;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationCallback;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationLog;
import com.project2.ism.Model.VendorTransactions;
import com.project2.ism.Repository.*;
import com.project2.ism.Service.InstantSettlement.InstantSettlementTrigger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RazorpayTransactionService {

    private static final Logger log =
            LoggerFactory.getLogger(RazorpayTransactionService.class);

    private final VendorTransactionsRepository vendorTransactionsRepository;
    private final ObjectMapper mapper ;
    private final RazorpayNotificationLogService razorpayNotificationLogService;
    private final InstantSettlementTrigger instantSettlementTrigger; // NEW
    private final FranchiseOrMerchantNotificationCallbackRepository franchiseOrMerchantNotificationCallbackRepo;
    private final FranchiseOrMerchantNotificationLogRepository franchiseOrMerchantNotificationLogRepo;
    private final MerchantRepository merchantRepository;
    private final ProductSerialNumbersRepository productSerialNumbersRepository;
    private final RestTemplate restTemplate;


    public RazorpayTransactionService(VendorTransactionsRepository vendorTransactionsRepository, ObjectMapper mapper, RazorpayNotificationLogService razorpayNotificationLogService, InstantSettlementTrigger instantSettlementTrigger,  FranchiseOrMerchantNotificationCallbackRepository franchiseOrMerchantNotificationCallbackRepo, FranchiseOrMerchantNotificationLogRepository franchiseOrMerchantNotificationLogRepo, MerchantRepository merchantRepository, ProductSerialNumbersRepository productSerialNumbersRepository, RestTemplate restTemplate) {
        this.vendorTransactionsRepository = vendorTransactionsRepository;
        this.mapper = mapper;
        this.razorpayNotificationLogService = razorpayNotificationLogService;
        this.instantSettlementTrigger = instantSettlementTrigger;
        this.franchiseOrMerchantNotificationCallbackRepo = franchiseOrMerchantNotificationCallbackRepo;
        this.franchiseOrMerchantNotificationLogRepo = franchiseOrMerchantNotificationLogRepo;
        this.merchantRepository = merchantRepository;
        this.productSerialNumbersRepository = productSerialNumbersRepository;
        this.restTemplate = restTemplate;
    }


    public void process(String rawJson) {

        long start = System.currentTimeMillis();
        Long logId = null;
        String txnId = null;

        try {
            // Step 1: Parse DTO
            RazorpayNotificationDTO dto =
                    mapper.readValue(rawJson, RazorpayNotificationDTO.class);

            txnId = dto.getTxnId();   // txnId extracted

            // Step 2: create initial log entry (RECEIVED)
            logId = razorpayNotificationLogService.logReceived(rawJson, txnId);

            // Step 3: find existing transaction
            VendorTransactions existing =
                    vendorTransactionsRepository.findByTransactionReferenceId(dto.getTxnId())
                            .orElse(null);

            VendorTransactions tx =
                    (existing != null) ? existing : new VendorTransactions();

            // Step 4: map fields
            applyMapping(dto, tx);

            // Step 5: save final transaction
            vendorTransactionsRepository.save(tx);

            // Step 6: success log
            long time = System.currentTimeMillis() - start;
            razorpayNotificationLogService.logSuccess(logId, time);

            //  NEW: Step 7: Check and trigger instant settlement if enabled
            instantSettlementTrigger.checkAndTrigger(tx.getTransactionReferenceId());

            // Step 8: Trigger franchise / merchant notification
            processFranchiseOrMerchantNotification(tx, rawJson);

        } catch (Exception ex) {

            long time = System.currentTimeMillis() - start;

            // If log entry already created, update it
            if (logId != null) {
                razorpayNotificationLogService.logFailure(logId, ex.getMessage(), time);
            } else {
                // If log entry was NOT created due to parsing error,
                // create a failure log NOW with txnId = null
                razorpayNotificationLogService.logFailureNoDTO(rawJson, txnId, ex.getMessage(), time);
            }

            // Re-throw for controller to log error but NOT affect Razorpay response.
            throw new RuntimeException("Failed to process Razorpay notification: " + ex.getMessage(), ex);
        }
    }


    private void applyMapping(RazorpayNotificationDTO dto, VendorTransactions tx) {

        tx.setTransactionReferenceId(dto.getTxnId());
        tx.setUsername(dto.getUsername());
        tx.setMobile(dto.getUserMobile());
        tx.setConsumer(dto.getCustomerName());
        tx.setPayer(dto.getPayerName());

        tx.setAmount(big(dto.getAmount()));
        tx.setTip(big(dto.getAmountAdditional()));
        tx.setCashAtPos(big(dto.getAmountCashBack()));
        tx.setAmountOriginal(big(dto.getAmountOriginal()));

        tx.setTxnType(dto.getTxnType());
        tx.setType(dto.getTxnType());
        tx.setMode(dto.getPaymentMode());

        tx.setAuthCode(dto.getAuthCode());
        tx.setRrn(dto.getRrNumber());
        tx.setBatchNumber(dto.getBatchNumber());
        tx.setEmail(dto.getCustomerEmail());
        tx.setCard(dto.getFormattedPan());
        tx.setCardLastFourDigit(dto.getCardLastFourDigit());
        tx.setPaymentCardBin(dto.getPaymentCardBin());
        tx.setBrandType(dto.getPaymentCardBrand());
        tx.setCardType(dto.getPaymentCardType());

        // Card internal metadata
        tx.setCardClassification(dto.getCardClassification());
        tx.setCardTxnType(dto.getCardTxnType());
        //tx.setCardTxnTypeDesc(dto.getCardTxnTypeDesc());

        tx.setMerchant(dto.getMerchantName());
        tx.setOrgCode(dto.getOrgCode());
        tx.setMerchantCode(dto.getMerchantCode());

        // Razorpay reference numbers
        tx.setRef(dto.getExternalRefNumber());
        tx.setRef1(dto.getExternalRefNumber2());
        tx.setRef2(dto.getExternalRefNumber3());
        tx.setRef3(dto.getExternalRefNumber4());
        tx.setRef4(dto.getExternalRefNumber5());
        tx.setRef5(dto.getExternalRefNumber6());
        tx.setRef6(dto.getExternalRefNumber7());

        tx.setMid(dto.getMid());
        tx.setTid(dto.getTid());
        tx.setDeviceSerial(dto.getDeviceSerial());

        tx.setIssuingBank(dto.getBankName());

        tx.setStatus(dto.getStatus());
        tx.setSettlementStatus(dto.getSettlementStatus());

        tx.setOriginalTransactionId(dto.getReferenceTransactionId());
        tx.setReferenceTransactionId(dto.getReferenceTransactionId());

        tx.setPaymentGateway(dto.getPaymentGateway());

        // Date — convert Razorpay ISO string → LocalDateTime
        tx.setDate(parseDate(dto.getChargeSlipDate()));
    }

    private BigDecimal big(Double val) {
        return val == null ? BigDecimal.ZERO : BigDecimal.valueOf(val);
    }

    private LocalDateTime parseDate(String iso) {
        try {
            return OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDateTime();
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
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



}
