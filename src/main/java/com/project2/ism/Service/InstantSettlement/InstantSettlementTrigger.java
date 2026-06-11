package com.project2.ism.Service.InstantSettlement;

import com.project2.ism.DTO.TempDTOs.SettlementResultDTO;
import com.project2.ism.Model.VendorTransactions;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Product;
import com.project2.ism.Repository.VendorTransactionsRepository;
import com.project2.ism.Repository.ProductSerialsRepository;
import com.project2.ism.Service.EnhancedSettlementService2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InstantSettlementTrigger {

    private static final Logger log = LoggerFactory.getLogger(InstantSettlementTrigger.class);

    private final VendorTransactionsRepository vendorTransactionsRepository;
    private final ProductSerialsRepository productSerialsRepository;
    private final EnhancedSettlementService2 settlementService;

    public InstantSettlementTrigger(VendorTransactionsRepository vendorTransactionsRepository,
                                    ProductSerialsRepository productSerialsRepository,
                                    EnhancedSettlementService2 settlementService) {
        this.vendorTransactionsRepository = vendorTransactionsRepository;
        this.productSerialsRepository = productSerialsRepository;
        this.settlementService = settlementService;
    }

    /**
     * Check if instant settlement is enabled for this transaction's product
     * and trigger settlement if yes
     */
    @Async("instantSettlementExecutor")
    public void checkAndTrigger(String transactionReferenceId) {
        log.debug("Checking instant settlement eligibility for transaction: {}", transactionReferenceId);

        try {
            // Step 1: Get the transaction
            VendorTransactions vt = vendorTransactionsRepository
                    .findByTransactionReferenceId(transactionReferenceId)
                    .orElseThrow(() -> new IllegalStateException("Transaction not found: " + transactionReferenceId));

            // Step 2: Check if already settled
            if (Boolean.TRUE.equals(vt.getSettled())) {
                log.info("Transaction {} already settled, skipping instant settlement", transactionReferenceId);
                return;
            }

            // Step 3: Find device (MID/TID lookup)
            Optional<ProductSerialNumbers> deviceOpt = findDeviceForTransaction(vt);
            if (deviceOpt.isEmpty()) {
                log.warn("No device found for transaction {}, skipping instant settlement", transactionReferenceId);
                return;
            }

            ProductSerialNumbers device = deviceOpt.get();
            Product product = device.getProduct();

            // Step 4: Check if instant settlement is enabled for this product
            if (!Boolean.TRUE.equals(product.getInstantSettlementEnabled())) {
                log.debug("Instant settlement not enabled for product {} ({}), skipping",
                        product.getId(), product.getProductName());
                return;
            }

            log.info("Instant settlement enabled for product {} ({}), triggering T+0 settlement for transaction {}",
                    product.getId(), product.getProductName(), transactionReferenceId);

            // Step 5: Get merchant ID
            Long merchantId = device.getMerchant().getId();

            // Step 6: Create T+0 batch automatically
            String createdBy = "INSTANT_SETTLEMENT_SYSTEM";
            Long productId = product.getId();

            var batch = settlementService.getOrCreateActiveBatch(merchantId, productId, "T0", createdBy);

            // Step 7: Trigger settlement using existing engine
            SettlementResultDTO result = settlementService.settleOneEnhanced(
                    merchantId,
                    batch.getId(),
                    transactionReferenceId
            );

            // Step 8: Log result
            if ("OK".equals(result.getStatus())) {
                log.info("✅ Instant settlement SUCCESS for transaction {}: amount={}, fee={}, net={}",
                        transactionReferenceId, result.getAmount(), result.getFee(), result.getNet());
            } else {
                log.error("❌ Instant settlement FAILED for transaction {}: {}",
                        transactionReferenceId, result.getMessage());
            }

        } catch (Exception e) {
            log.error("❌ Exception during instant settlement check for transaction {}: {}",
                    transactionReferenceId, e.getMessage(), e);
        }
    }

    /**
     * Find device using MID/TID from transaction
     * Reuses same logic as settlement service
     */
    private Optional<ProductSerialNumbers> findDeviceForTransaction(VendorTransactions vt) {
        // Try MID + TID first (most precise)
        if (vt.getMid() != null && vt.getTid() != null) {
            List<ProductSerialNumbers> devices = productSerialsRepository.findByMidAndTid(vt.getMid(), vt.getTid());
            if (!devices.isEmpty()) {
                return Optional.of(devices.get(0));
            }
        }

        // Try MID only
        if (vt.getMid() != null) {
            Optional<ProductSerialNumbers> device = productSerialsRepository.findByMid(vt.getMid());
            if (device.isPresent()) {
                return device;
            }
        }

        // Try TID only (least precise)
        if (vt.getTid() != null) {
            Optional<ProductSerialNumbers> device = productSerialsRepository.findByTid(vt.getTid());
            if (device.isPresent()) {
                return device;
            }
        }

        return Optional.empty();
    }
}