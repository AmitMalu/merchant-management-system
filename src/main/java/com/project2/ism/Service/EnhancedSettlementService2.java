package com.project2.ism.Service;

import com.project2.ism.DTO.TempDTOs.*;
import com.project2.ism.Model.*;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.PricingScheme.CardRate;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;

import com.project2.ism.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EnhancedSettlementService2 {

    private static final Logger log = LoggerFactory.getLogger(EnhancedSettlementService2.class);

    private final ProductSerialsRepository serialRepo;
    private final ProductRepository productRepository;
    private final VendorTransactionsRepository vendorRepo;
    private final MerchantSettlementBatchRepository batchRepo;
    private final MerchantWalletRepository walletRepo;
    private final MerchantRepository merchantRepository;
    private final MerchantTransDetRepository merchantTxnRepo;
    private final CustomerSchemeAssignmentRepository schemeAssignRepo;
    private final CardRateRepository cardRateRepo;
    private final FranchiseWalletRepository franchiseWalletRepo;
    private final FranchiseTransDetRepository franchiseTxnRepo;
    private final SettlementBatchCandidateRepository candidateRepo;
    private final SettlementAsyncProcessor asyncProcessor;
    private final EntityHistoryRepository entityHistoryRepository;

    @Autowired
    public EnhancedSettlementService2(
            ProductSerialsRepository serialRepo,
            ProductRepository productRepository,
            VendorTransactionsRepository vendorRepo,
            MerchantSettlementBatchRepository batchRepo,
            MerchantWalletRepository walletRepo,
            MerchantRepository merchantRepository,
            MerchantTransDetRepository merchantTxnRepo,
            CustomerSchemeAssignmentRepository schemeAssignRepo,
            CardRateRepository cardRateRepo,
            FranchiseWalletRepository franchiseWalletRepo,
            FranchiseTransDetRepository franchiseTxnRepo,
            SettlementBatchCandidateRepository candidateRepo,
            SettlementAsyncProcessor asyncProcessor, EntityHistoryRepository entityHistoryRepository
    ) {
        this.serialRepo = serialRepo;
        this.productRepository = productRepository;
        this.vendorRepo = vendorRepo;
        this.batchRepo = batchRepo;
        this.walletRepo = walletRepo;
        this.merchantRepository = merchantRepository;
        this.merchantTxnRepo = merchantTxnRepo;
        this.schemeAssignRepo = schemeAssignRepo;
        this.cardRateRepo = cardRateRepo;
        this.franchiseWalletRepo = franchiseWalletRepo;
        this.franchiseTxnRepo = franchiseTxnRepo;
        this.candidateRepo = candidateRepo;
        this.asyncProcessor = asyncProcessor;
        this.entityHistoryRepository = entityHistoryRepository;
    }

    /**
     * Create or get active batch for direct merchants (with product_id support)
     */
    @Transactional
    public MerchantSettlementBatch getOrCreateActiveBatch(Long merchantId, Long productId, String cycleKey, String createdBy) {
        log.debug("Getting/creating active batch for merchant={}, cycle={}, product={}", merchantId, cycleKey, productId);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        // For direct merchants, reuse existing batches with same product
//        if (merchant.getFranchise() == null) {
//            Optional<MerchantSettlementBatch> existing = batchRepo
//                    .findByMerchantIdAndCycleKeyAndProductIdAndStatusIn(
//                            merchantId, cycleKey, productId, Arrays.asList("DRAFT", "OPEN"));
//
//            if (existing.isPresent()) {
//                log.debug("Found existing batch {} for merchant {}", existing.get().getId(), merchantId);
//                return existing.get();
//            }
//        }

        // Create new batch
        return createBatch(merchantId,productId, cycleKey, createdBy, null);
    }

    /**
     * Create new settlement batch with product_id filtering
     */
    public MerchantSettlementBatch createBatch(Long merchantId, Long productId, String cycleKey, String createdBy,Long franchiseBatchId) {
        log.debug("Creating new batch for merchant={}, cycle={}, product={}", merchantId, cycleKey, productId);

        LocalDateTime windowEnd = calculateWindowEnd(cycleKey);
        LocalDateTime windowStart = calculateWindowStart(merchantId, productId);

        MerchantSettlementBatch batch = new MerchantSettlementBatch();
        batch.setMerchantId(merchantId);
        if(franchiseBatchId!=null){
            batch.setFranchiseBatchId(franchiseBatchId);
        }
        batch.setWindowStart(windowStart);
        batch.setWindowEnd(windowEnd);
        batch.setCycleKey(cycleKey);
        batch.setCreatedBy(createdBy);
        batch.setProductId(productId); // NEW: Store product_id
        batch.setStatus("OPEN");

        MerchantSettlementBatch savedBatch = batchRepo.save(batch);
        log.info("Created batch {} for merchant {} with product {}", savedBatch.getId(), merchantId, productId);
        return savedBatch;
    }

    /**
     * Update batch candidates for direct merchants
     */
    @Transactional
    public MerchantSettlementBatch updateBatchCandidates(Long batchId, List<String> vendorTxIds) {
        log.debug("Updating batch {} candidates with {} transactions", batchId, vendorTxIds.size());

        MerchantSettlementBatch batch = getBatchById(batchId);
        validateBatchForUpdates(batch);

        // Clear existing candidates
        candidateRepo.deleteByBatchId(batchId);

        // Process new candidates
        BatchSummary summary = processCandidateTransactions(batch, vendorTxIds);

        // Update batch totals
        updateBatchTotals(batch, summary);

        // Trigger async processing
        asyncProcessor.processBatchWithTracking(batch.getId());

        log.info("Updated batch {} with {} valid candidates, total amount: {}",
                batchId, summary.validCount, summary.totalAmount);
        return batchRepo.save(batch);
    }

    /**
     * Get settlement candidates with product_id filtering
     */
    public List<SettlementCandidateDTO> listSettlementCandidates(Long merchantId, LocalDateTime from, LocalDateTime to, Long productId) {
        log.debug("Listing candidates for merchant={}, product={}, from={} to={}", merchantId, productId, from, to);

        // Get device identifiers for specific product
        Set<DeviceIdentifiers> deviceIds = getDeviceIdentifiersForProduct(merchantId, productId);

        if (deviceIds.isEmpty()) {
            log.warn("No devices found for merchant {} and product {}", merchantId, productId);
            return Collections.emptyList();
        } else {
            deviceIds.forEach(di -> log.debug("Fetched device → MID={}, TID={}", di.mid(), di.tid()));
        }

        // Extract MIDs and TIDs
        Set<String> mids = deviceIds.stream()
                .map(DeviceIdentifiers::mid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> tids = deviceIds.stream()
                .map(DeviceIdentifiers::tid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Find candidate transactions
        List<VendorTransactions> candidates = vendorRepo.findCandidates(
                from, to,
                mids.isEmpty() ? List.of("__NONE__") : new ArrayList<>(mids),
                tids.isEmpty() ? List.of("__NONE__") : new ArrayList<>(tids)
        );

        log.debug("Found {} candidate transactions for merchant {} product {}. First: {}, Last: {}",
                candidates.size(),
                merchantId,
                productId,
                candidates.isEmpty() ? null : candidates.get(0),
                candidates.isEmpty() ? null : candidates.get(candidates.size() - 1));

        return candidates.stream()
                .map(vt -> mapToSettlementCandidate(vt, merchantId, productId))
                .collect(Collectors.toList());
    }

    /**
     * Get settlement candidates by cycle with product filtering
     */
    public List<SettlementCandidateDTO> listSettlementCandidatesForCycle(Long merchantId, String cycleKey, Long productId) {
        log.debug("Listing candidates for merchant={}, cycle={}, product={}", merchantId, cycleKey, productId);

        LocalDateTime windowEnd = calculateWindowEnd(cycleKey);
        LocalDateTime windowStart = calculateWindowStart(merchantId, productId);

        return listSettlementCandidates(merchantId, windowStart, windowEnd, productId);
    }

    /**
     * Resume failed batch processing
     */
    public void resumeBatch(Long batchId) {
        log.info("Resuming batch {}", batchId);

        MerchantSettlementBatch batch = getBatchById(batchId);
        validateBatchForResume(batch);

        // Reset failed candidates to selected
        candidateRepo.updateFailedCandidatesToSelected(batchId);

        // Trigger async processing
        asyncProcessor.processBatchWithTracking(batchId);

        log.info("Resumed processing for batch {}", batchId);
    }

    /**
     * Settle single transaction with enhanced error handling
     */
    @Transactional
    public SettlementResultDTO settleOneEnhanced(Long merchantId, Long batchId, String vendorTxId) {
        log.debug("Processing settlement for merchant={}, batch={}, tx={}", merchantId, batchId, vendorTxId);

        try {
            VendorTransactions vt = getVendorTransaction(vendorTxId);

            if (Boolean.TRUE.equals(vt.getSettled())) {
                log.info("Transaction {} already settled, skipping", vendorTxId);
                return SettlementResultDTO.alreadySettled(vendorTxId);
            }

            Merchant merchant = getMerchant(merchantId);
            ProductSerialNumbers device = findDeviceForTransaction(vt, merchant);

            validateTransactionBelongsToMerchant(vt, device, merchantId);

            CardRate cardRate = getCardRateForTransaction(vt, device);
            BigDecimal amount = nvl(vt.getAmount());


            SettlementResultDTO temp;
            if (merchant.getFranchise() != null) {
                temp = processFranchiseMerchantSettlement(merchant, vt, cardRate, amount, batchId, device);
                log.debug("Franchise settlement result: {}", temp);
            } else {
                temp = processDirectMerchantSettlement(merchant, vt, cardRate, amount, batchId, device);
                log.debug("Direct settlement result: {}", temp);
            }
            SettlementResultDTO result = temp;

            log.info("Successfully settled transaction {} for merchant {}, amount: {}, fee: {}",
                    vendorTxId, merchantId, result.getAmount(), result.getFee());
            return result;

        } catch (Exception e) {
            log.error("Settlement failed for transaction {} merchant {}: {}", vendorTxId, merchantId, e.getMessage(), e);
            return SettlementResultDTO.failed(vendorTxId, e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    private LocalDateTime calculateWindowEnd(String cycleKey) {
        return switch (cycleKey == null ? "" : cycleKey.toUpperCase()) {
            case "T0" -> LocalDateTime.now();
            case "T1" -> LocalDate.now().minusDays(1).atTime(23, 59, 59);
            case "T2" -> LocalDate.now().minusDays(2).atTime(23, 59, 59);
            default -> throw new IllegalArgumentException("Unknown cycleKey: " + cycleKey);
        };
    }

    private LocalDateTime calculateWindowStart(Long merchantId, Long productId) {
        Set<DeviceIdentifiers> deviceIds = getDeviceIdentifiersForProduct(merchantId, productId);

        if (deviceIds.isEmpty()) {
            return merchantRepository.findById(merchantId)
                    .map(m -> m.getCreatedAt() != null ? m.getCreatedAt() : LocalDateTime.now())
                    .orElse(LocalDateTime.now());
        }

        Set<String> mids = deviceIds.stream()
                .map(DeviceIdentifiers::mid)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return mids.isEmpty()
                ? LocalDateTime.now().minusDays(30)
                : vendorRepo.findEarliestUnsettledDateByMids(new ArrayList<>(mids))
                .orElse(LocalDateTime.now().minusDays(30));
    }

    private Set<DeviceIdentifiers> getDeviceIdentifiersForProduct(Long merchantId, Long productId) {
        Merchant m = merchantRepository.findById(merchantId).orElseThrow();
        Product p = productRepository.findById(productId).orElseThrow();

        List<ProductSerialNumbers> list = serialRepo.findByMerchantAndProduct(m, p);

        return list.stream()
                .map(row -> new DeviceIdentifiers(row.getMid(), row.getTid()))
                .filter(di -> di.mid() != null || di.tid() != null)
                .collect(Collectors.toSet());
    }


    private void validateBatchForUpdates(MerchantSettlementBatch batch) {
        if (!"DRAFT".equals(batch.getStatus()) && !"OPEN".equals(batch.getStatus())) {
            throw new IllegalStateException("Cannot modify batch in " + batch.getStatus() + " state");
        }
    }

    private void validateBatchForResume(MerchantSettlementBatch batch) {
        if (!"PARTIALLY_COMPLETED".equals(batch.getStatus()) && !"FAILED".equals(batch.getStatus())) {
            throw new IllegalStateException("Batch cannot be resumed. Current status: " + batch.getStatus());
        }
    }

    private BatchSummary processCandidateTransactions(MerchantSettlementBatch batch, List<String> vendorTxIds) {
        BatchSummary summary = new BatchSummary();

        for (String vendorTxId : vendorTxIds) {
            try {
                VendorTransactions vt = getVendorTransaction(vendorTxId);
                SettlementCandidateDTO dto = mapToSettlementCandidate(vt, batch.getMerchantId(), batch.getProductId());

                if (dto.getError() == null) {
                    SettlementBatchCandidate candidate = new SettlementBatchCandidate(
                            batch.getId(), vendorTxId, dto.getAmount(), dto.getFee(), dto.getNetAmount());
                    candidateRepo.save(candidate);
                    summary.addValid(dto.getAmount(), dto.getFee(), dto.getNetAmount());
                } else {
                    log.warn("Invalid candidate {}: {}", vendorTxId, dto.getError());
                    summary.addInvalid();
                }
            } catch (Exception e) {
                log.error("Error processing candidate {}: {}", vendorTxId, e.getMessage());
                summary.addInvalid();
            }
        }

        return summary;
    }

    private void updateBatchTotals(MerchantSettlementBatch batch, BatchSummary summary) {
        batch.setTotalTransactions(summary.validCount);
        batch.setTotalAmount(summary.totalAmount);
        batch.setTotalFees(summary.totalFees);
        batch.setTotalNetAmount(summary.totalNet);
        batch.setStatus("PROCESSING");
    }

    private VendorTransactions getVendorTransaction(String vendorTxId) {
        return vendorRepo.findByTransactionReferenceId(vendorTxId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + vendorTxId));
    }

    private Merchant getMerchant(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
    }

    private ProductSerialNumbers findDeviceForTransaction(VendorTransactions vt, Merchant merchant) {
        Optional<ProductSerialNumbers> device = findDeviceForTransaction(vt);
        if (device.isEmpty()) {
            throw new IllegalStateException("Device not found for transaction: " + vt.getTransactionReferenceId());
        }
        return device.get();
    }

    private Optional<ProductSerialNumbers> findDeviceForTransaction(VendorTransactions vt) {
        // Try MID + TID first (most precise)
        if (vt.getMid() != null && vt.getTid() != null) {
            List<ProductSerialNumbers> devices = serialRepo.findByMidAndTid(vt.getMid(), vt.getTid());
            if (!devices.isEmpty()) {
                return Optional.of(devices.get(0));
            }
        }

        // Try MID only
        if (vt.getMid() != null) {
            Optional<ProductSerialNumbers> device = serialRepo.findByMid(vt.getMid());
            if (device.isPresent()) {
                return device;
            }
        }

        // Try TID only (least precise)
        if (vt.getTid() != null) {
            Optional<ProductSerialNumbers> device = serialRepo.findByTid(vt.getTid());
            if (device.isPresent()) {
                return device;
            }
        }

        return Optional.empty();
    }

    private void validateTransactionBelongsToMerchant(VendorTransactions vt, ProductSerialNumbers device, Long expectedMerchantId) {
        if (!expectedMerchantId.equals(device.getMerchant().getId())) {
            throw new IllegalStateException("Transaction " + vt.getTransactionReferenceId() +
                    " belongs to different merchant: " + device.getMerchant().getId());
        }
    }

    private CardRate getCardRateForTransaction(VendorTransactions vt, ProductSerialNumbers device) {

        LocalDate txnDate = vt.getDate() != null ? vt.getDate().toLocalDate() : LocalDate.now();

        // Get merchant from device directly
        Merchant merchant = device.getMerchant();
        Long franchiseId = merchant.getFranchise() != null ? merchant.getFranchise().getId() : null;

        Optional<CustomerSchemeAssignment> schemeAssignment;

        if (merchant.getId() != null) {
            schemeAssignment = schemeAssignRepo.findActiveSchemeForMerchantAndProduct(
                    merchant.getId(),
                    device.getProduct().getId(),
                    txnDate
            );
        } else {
            schemeAssignment = schemeAssignRepo.findActiveSchemeForFranchiseAndProduct(
                    franchiseId,
                    device.getProduct().getId(),
                    txnDate
            );
        }

        CustomerSchemeAssignment assignment = schemeAssignment
                .orElseThrow(() -> new IllegalStateException("No active pricing scheme found for device"));

        String normalized = normalizeCardName(vt.getBrandType(), vt.getCardType());
        final String cardName = "UNKNOWN".equals(normalized) ? "UPI" : normalized;

        Long schemeId = assignment.getScheme().getId();

        // Resolve product category from payment gateway → product brand → productCategory
        // This gives us the exact card rate row for this product type within the scheme
        ProductCategory productCategory = resolveProductCategoryFromGateway(vt, device);

        if (productCategory != null) {
            Long categoryId = productCategory.getId();
            Optional<CardRate> crOpt = cardRateRepo
                    .findByPricingScheme_IdAndProductCategory_IdAndCardNameIgnoreCase(schemeId, categoryId, cardName)
                    .or(() -> cardRateRepo
                            .findByPricingScheme_IdAndProductCategory_IdAndCardNameContainingIgnoreCase(schemeId, categoryId, "DEFAULT"));

            if (crOpt.isPresent()) {
                log.info("Card rate resolved via productCategory={} scheme={} card={}",
                        categoryId, schemeId, cardName);
                return crOpt.get();
            }
            log.warn("No card rate found with productCategory={}, falling back to scheme-only lookup for card={}",
                    categoryId, cardName);
        }

        // Fallback: scheme-only lookup (backward compatible for data without productCategory)
        return cardRateRepo.findByPricingScheme_IdAndCardNameContainingIgnoreCase(schemeId, cardName)
                .or(() -> cardRateRepo.findByPricingScheme_IdAndCardNameContainingIgnoreCase(schemeId, "DEFAULT"))
                .orElseThrow(() -> new IllegalStateException("No card rate found for card=" + cardName
                        + " in scheme=" + schemeId));
    }

    private SettlementResultDTO processDirectMerchantSettlement(Merchant merchant, VendorTransactions vt,
                                                                CardRate cr, BigDecimal amount, Long batchId, ProductSerialNumbers device) {
        log.debug("Processing direct merchant settlement for merchant {} amount {}", merchant.getId(), amount);

        // Check if rate was changed after transaction date
        LocalDateTime transactionDate = vt.getDate();
        Double historicalRate = getHistoricalRateIfChanged(cr, transactionDate, "rate");
        Double rateValue = historicalRate != null ? historicalRate : cr.getRate();

        if (rateValue == null) {
            throw new IllegalStateException("No rate configured for card: " + cr.getCardName());
        }

        if (historicalRate != null) {
            log.info("Using historical rate {} (current: {}) for transaction dated {} for card {}",
                    historicalRate, cr.getRate(), transactionDate, cr.getCardName());
        }
        BigDecimal feePct = amount.signum() > 0
                ? BigDecimal.valueOf(rateValue).movePointLeft(2)
                : BigDecimal.ZERO;
        BigDecimal fee = amount.multiply(feePct).setScale(2, RoundingMode.HALF_UP);
        if (fee.compareTo(amount.abs()) > 0) fee = amount.abs();
        BigDecimal net = amount.subtract(fee).setScale(2, RoundingMode.HALF_UP);

        // --- Lock & fetch wallet first ---
        MerchantWallet wallet = walletRepo.findByMerchantIdForUpdate(merchant.getId())
                .orElseGet(() -> {
                    MerchantWallet w = new MerchantWallet();
                    Merchant mRef = new Merchant();
                    mRef.setId(merchant.getId());
                    w.setMerchant(mRef);
                    w.setAvailableBalance(BigDecimal.ZERO);
                    w.setLastUpdatedAmount(BigDecimal.ZERO);
                    w.setLastUpdatedAt(LocalDateTime.now());
                    w.setTotalCash(BigDecimal.ZERO);
                    w.setCutOfAmount(BigDecimal.ZERO);
                    return walletRepo.save(w);
                });

        BigDecimal beforeBalance = nvl(wallet.getAvailableBalance());
        BigDecimal afterBalance = beforeBalance.add(net);

        System.out.println(merchantTxnRepo.existsByVendorTransactionId(vt.getInternalId().toString()));
        // --- First create transaction details ---
        if (!merchantTxnRepo.existsByVendorTransactionId(vt.getInternalId().toString())) {
            MerchantTransactionDetails mtd = new MerchantTransactionDetails();
            mtd.setMerchant(merchant);
            mtd.setGrossCharge(fee);
            mtd.setCharge(fee);
            mtd.setActionOnBalance("CREDIT");
            mtd.setVendorTransactionId(vt.getTransactionReferenceId());
            mtd.setTransactionDate(vt.getDate());
            mtd.setAmount(amount);
            mtd.setNetAmount(net);
            mtd.setFinalBalance(afterBalance);
            mtd.setBalBeforeTran(beforeBalance);
            mtd.setBalAfterTran(afterBalance);
            mtd.setTranStatus("SETTLED");
            mtd.setTransactionType("CREDIT");
            mtd.setRemarks("Batch " + batchId + " settlement");
            mtd.setService("Settlement");
            mtd.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
            merchantTxnRepo.save(mtd);
        }

        // --- Then update wallet ---
        wallet.setAvailableBalance(afterBalance);
        wallet.setLastUpdatedAmount(net);
        wallet.setLastUpdatedAt(LocalDateTime.now());
        wallet.setTotalCash(nvl(wallet.getTotalCash()).add(net.max(BigDecimal.ZERO)));
        walletRepo.save(wallet);

        // --- Mark transaction settled ---
        markTransactionSettled(vt, batchId);

        return SettlementResultDTO.ok(vt.getTransactionReferenceId(), amount, fee, net, wallet.getAvailableBalance());
    }

    private SettlementResultDTO processFranchiseMerchantSettlement(Merchant merchant, VendorTransactions vt,
                                                                   CardRate cr, BigDecimal amount, Long batchId, ProductSerialNumbers device) {
        log.debug("Processing franchise merchant settlement for merchant {} amount {}", merchant.getId(), amount);

        Franchise franchise = merchant.getFranchise();
        if (franchise == null) {
            throw new IllegalStateException("Merchant does not belong to a franchise");
        }

        LocalDateTime transactionDate = vt.getDate();

        // Check for historical rates
        Double historicalMerchantRate = getHistoricalRateIfChanged(cr, transactionDate, "merchantRate");
        Double historicalFranchiseRate = getHistoricalRateIfChanged(cr, transactionDate, "franchiseRate");

        Double merchantRatePercent = historicalMerchantRate != null ? historicalMerchantRate : cr.getMerchantRate();
        Double franchiseRatePercent = historicalFranchiseRate != null ? historicalFranchiseRate : cr.getFranchiseRate();

        if (merchantRatePercent == null || franchiseRatePercent == null) {
            log.warn("Dual rates not configured for card: {}. Using single rate fallback.", cr.getCardName());
            return processDirectMerchantSettlement(merchant, vt, cr, amount, batchId, device);
        }
        if (historicalMerchantRate == null || historicalFranchiseRate == null) {
            log.info("Using current rates - Merchant: {} (current: {}), Franchise: {} (current: {}) for transaction dated {} for card {}",
                    merchantRatePercent, cr.getMerchantRate(),
                    franchiseRatePercent, cr.getFranchiseRate(),
                    transactionDate, cr.getCardName());
        }

        if (historicalMerchantRate != null || historicalFranchiseRate != null) {
            log.info("Using historical rates - Merchant: {} (current: {}), Franchise: {} (current: {}) for transaction dated {} for card {}",
                    merchantRatePercent, cr.getMerchantRate(),
                    franchiseRatePercent, cr.getFranchiseRate(),
                    transactionDate, cr.getCardName());
        }

        BigDecimal merchantRate = BigDecimal.valueOf(merchantRatePercent).movePointLeft(2);
        BigDecimal franchiseRate = BigDecimal.valueOf(franchiseRatePercent).movePointLeft(2);
        BigDecimal rateDiff = merchantRate.subtract(franchiseRate);

        BigDecimal merchantFee = amount.multiply(merchantRate).setScale(2, RoundingMode.HALF_UP);

        BigDecimal merchantNet = amount.subtract(merchantFee);

        BigDecimal franchiseCommission = merchantNet.multiply(rateDiff).setScale(2, RoundingMode.HALF_UP);

        BigDecimal systemShare = merchantFee.subtract(franchiseCommission).setScale(2, RoundingMode.HALF_UP);


        // --- lock merchant wallet first ---
        MerchantWallet merchantWallet = walletRepo.findByMerchantIdForUpdate(merchant.getId())
                .orElseGet(() -> {
                    MerchantWallet w = new MerchantWallet();
                    Merchant mRef = new Merchant();
                    mRef.setId(merchant.getId());
                    w.setMerchant(mRef);
                    w.setAvailableBalance(BigDecimal.ZERO);
                    w.setLastUpdatedAmount(BigDecimal.ZERO);
                    w.setLastUpdatedAt(LocalDateTime.now());
                    w.setTotalCash(BigDecimal.ZERO);
                    w.setCutOfAmount(BigDecimal.ZERO);
                    return walletRepo.save(w);
                });

        BigDecimal merchantBefore = nvl(merchantWallet.getAvailableBalance());
        BigDecimal merchantAfter = merchantBefore.add(merchantNet);

        // --- lock franchise wallet if commission positive ---
        FranchiseWallet franchiseWallet = null;
        BigDecimal franchiseBefore = BigDecimal.ZERO;
        BigDecimal franchiseAfter = BigDecimal.ZERO;
        if (franchiseCommission.compareTo(BigDecimal.ZERO) > 0) {
            franchiseWallet = franchiseWalletRepo.findByFranchiseIdForUpdate(franchise.getId())
                    .orElseGet(() -> {
                        FranchiseWallet w = new FranchiseWallet();
                        Franchise fRef = new Franchise();
                        fRef.setId(franchise.getId());
                        w.setFranchise(fRef);
                        w.setAvailableBalance(BigDecimal.ZERO);
                        w.setLastUpdatedAmount(BigDecimal.ZERO);
                        w.setLastUpdatedAt(LocalDateTime.now());
                        w.setTotalCash(BigDecimal.ZERO);
                        w.setCutOfAmount(BigDecimal.ZERO);
                        return franchiseWalletRepo.save(w);
                    });
            franchiseBefore = nvl(franchiseWallet.getAvailableBalance());
            franchiseAfter = franchiseBefore.add(franchiseCommission);
        }

        // --- create transaction details first ---
        MerchantTransactionDetails mtd = new MerchantTransactionDetails();
        if (!merchantTxnRepo.existsByVendorTransactionId(vt.getInternalId().toString())) {

//            MerchantTransactionDetails mtd = new MerchantTransactionDetails();
            mtd.setMerchant(merchant);
            mtd.setGrossCharge(merchantFee);
            mtd.setCharge(systemShare);
            mtd.setActionOnBalance("CREDIT");
            mtd.setVendorTransactionId(vt.getTransactionReferenceId());
            mtd.setTransactionDate(vt.getDate());
            mtd.setAmount(amount);
            mtd.setFinalBalance(merchantAfter);
            mtd.setBalBeforeTran(merchantBefore);
            mtd.setBalAfterTran(merchantAfter);
            mtd.setNetAmount(merchantNet);
            mtd.setTranStatus("SETTLED");
            mtd.setTransactionType("CREDIT");
            mtd.setRemarks("Batch " + batchId + " settlement");
            mtd.setService("Settlement");
            mtd.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
            mtd = merchantTxnRepo.save(mtd);
        }

        if (franchiseCommission.compareTo(BigDecimal.ZERO) > 0) {
            String vendorTxKey = vt.getTransactionReferenceId();
            if (!franchiseTxnRepo.existsByVendorTransactionId(vendorTxKey)) {
                FranchiseTransactionDetails ftd = new FranchiseTransactionDetails();
                ftd.setFranchise(franchise);
                ftd.setVendorTransactionId(vendorTxKey);
                ftd.setMerchantTransactionDetail(mtd);
                ftd.setActionOnBalance("CREDIT");
                ftd.setTransactionDate(vt.getDate());
                ftd.setAmount(amount);
                ftd.setFinalBalance(franchiseAfter);
                ftd.setBalBeforeTran(franchiseBefore);
                ftd.setBalAfterTran(franchiseAfter);
                ftd.setTranStatus("SETTLED");
                ftd.setTransactionType("CREDIT");
                ftd.setService("COMMISSION");
                ftd.setUpdatedDateAndTimeOfTransaction(LocalDateTime.now());
                ftd.setNetAmount(franchiseCommission);
                ftd.setRemarks("Batch " + batchId + " commission from merchant transaction " + vt.getTransactionReferenceId());
                franchiseTxnRepo.save(ftd);
            }
        }

        // --- then update wallets ---
        merchantWallet.setAvailableBalance(merchantAfter);
        merchantWallet.setLastUpdatedAmount(merchantNet);
        merchantWallet.setLastUpdatedAt(LocalDateTime.now());
        merchantWallet.setTotalCash(nvl(merchantWallet.getTotalCash()).add(merchantNet.max(BigDecimal.ZERO)));
        walletRepo.save(merchantWallet);

        if (franchiseWallet != null) {
            franchiseWallet.setAvailableBalance(franchiseAfter);
            franchiseWallet.setLastUpdatedAmount(franchiseCommission);
            franchiseWallet.setLastUpdatedAt(LocalDateTime.now());
            franchiseWallet.setTotalCash(nvl(franchiseWallet.getTotalCash()).add(franchiseCommission.max(BigDecimal.ZERO)));
            franchiseWalletRepo.save(franchiseWallet);
        }

        // --- mark settled ---


        markTransactionSettled(vt, batchId);

        return SettlementResultDTO.ok(vt.getTransactionReferenceId(), amount, merchantFee, merchantNet, merchantWallet.getAvailableBalance(),franchiseCommission);
    }


    private void markTransactionSettled(VendorTransactions vt, Long batchId) {
        vt.setSettled(true);
        vt.setSettledAt(LocalDateTime.now());
        vt.setSettlementBatchId(batchId);
        vt.setStatus("SETTLED");
        vendorRepo.save(vt);
    }

    private SettlementCandidateDTO mapToSettlementCandidate(VendorTransactions vt, Long expectedMerchantId, Long productId) {

        log.info("[SETTLEMENT] Mapping transaction | internalId={} | txnRef={} | expectedMerchant={} | productId={}",
                vt.getInternalId(),
                vt.getTransactionReferenceId(),
                expectedMerchantId,
                productId);

        try {

            Optional<ProductSerialNumbers> deviceOpt = findDeviceForTransaction(vt);

            if (deviceOpt.isEmpty()) {
                log.warn("[SETTLEMENT] Device not found for txnRef={}", vt.getTransactionReferenceId());
                return SettlementCandidateDTO.notFound(vt, "DEVICE_NOT_FOUND");
            }

            ProductSerialNumbers device = deviceOpt.get();

            log.info("[SETTLEMENT] Device found | serialNo={} | merchantId={} | productId={}",
                    device.getProduct().getModel(),
                    device.getMerchant().getId(),
                    device.getProduct().getId());

            if (!expectedMerchantId.equals(device.getMerchant().getId())) {
                log.warn("[SETTLEMENT] Merchant mismatch | expected={} | actual={} | txnRef={}",
                        expectedMerchantId,
                        device.getMerchant().getId(),
                        vt.getTransactionReferenceId());

                return SettlementCandidateDTO.notFound(vt, "WRONG_MERCHANT");
            }

            if (productId != null && !productId.equals(device.getProduct().getId())) {

                log.warn("[SETTLEMENT] Product mismatch | expected={} | actual={} | txnRef={}",
                        productId,
                        device.getProduct().getId(),
                        vt.getTransactionReferenceId());

                return SettlementCandidateDTO.notFound(vt, "WRONG_PRODUCT");
            }

            Merchant merchant = device.getMerchant();

            LocalDate txnDate =
                    vt.getDate() != null
                            ? vt.getDate().toLocalDate()
                            : LocalDate.now();

            log.info("[SETTLEMENT] Finding active scheme | merchantId={} | productId={} | txnDate={}",
                    merchant.getId(),
                    device.getProduct().getId(),
                    txnDate);

            Optional<CustomerSchemeAssignment> schemeOpt =
                    schemeAssignRepo.findActiveSchemeForMerchantAndProduct(
                            merchant.getId(),
                            device.getProduct().getId(),
                            txnDate
                    );

            if (schemeOpt.isEmpty()) {
                log.warn("[SETTLEMENT] No pricing scheme found | merchantId={} | productId={}",
                        merchant.getId(),
                        device.getProduct().getId());

                return SettlementCandidateDTO.notFound(vt, "NO_PRICING_SCHEME");
            }

            CustomerSchemeAssignment scheme = schemeOpt.get();

            Long schemeId = scheme.getScheme().getId();

            log.info("[SETTLEMENT] Pricing scheme resolved | schemeId={}", schemeId);

            String cardName = normalizeCardName(vt.getBrandType(), vt.getCardType());

            if ("UNKNOWN".equals(cardName)) {
                cardName = "UPI";
            }

            log.info("[SETTLEMENT] Normalized card | brand={} | cardType={} | normalized={}",
                    vt.getBrandType(),
                    vt.getCardType(),
                    cardName);

            ProductCategory productCategory =
                    resolveProductCategoryFromGateway(vt, device);

            if (productCategory != null) {
                log.info("[SETTLEMENT] Product category resolved | id={} | name={}",
                        productCategory.getId(),
                        productCategory.getCategoryName());
            } else {
                log.warn("[SETTLEMENT] Product category could not be resolved for txn={}",
                        vt.getTransactionReferenceId());
            }

            Optional<CardRate> crOpt;

            if (productCategory != null) {

                Long categoryId = productCategory.getId();

                crOpt = cardRateRepo
                        .findByPricingScheme_IdAndProductCategory_IdAndCardNameIgnoreCase(
                                schemeId,
                                categoryId,
                                cardName
                        )
                        .or(() -> cardRateRepo
                                .findByPricingScheme_IdAndProductCategory_IdAndCardNameContainingIgnoreCase(
                                        schemeId,
                                        categoryId,
                                        "DEFAULT"));

                if (crOpt.isEmpty()) {

                    log.warn("[SETTLEMENT] Card rate not found for category={} card={}, trying scheme fallback",
                            categoryId,
                            cardName);

                    crOpt = cardRateRepo
                            .findByPricingScheme_IdAndCardNameContainingIgnoreCase(
                                    schemeId,
                                    cardName
                            )
                            .or(() -> cardRateRepo
                                    .findByPricingScheme_IdAndCardNameContainingIgnoreCase(
                                            schemeId,
                                            "DEFAULT"));

                } else {

                    log.info("[SETTLEMENT] Card rate found using product category");
                }

            } else {

                log.warn("[SETTLEMENT] paymentGateway blank, using scheme-only lookup");

                crOpt = cardRateRepo
                        .findByPricingScheme_IdAndCardNameContainingIgnoreCase(
                                schemeId,
                                cardName
                        )
                        .or(() -> cardRateRepo
                                .findByPricingScheme_IdAndCardNameContainingIgnoreCase(
                                        schemeId,
                                        "DEFAULT"));
            }

            if (crOpt.isEmpty()) {

                log.warn("[SETTLEMENT] No card rate configured | schemeId={} | card={}",
                        schemeId,
                        cardName);

                return SettlementCandidateDTO.notFound(vt, "NO_CARD_RATE");
            }

            CardRate cr = crOpt.get();

            log.info("[SETTLEMENT] Card rate resolved | rate={} | merchantRate={}",
                    cr.getRate(),
                    cr.getMerchantRate());

            BigDecimal amount =
                    vt.getAmount() == null
                            ? BigDecimal.ZERO
                            : vt.getAmount();

            boolean isFranchiseMerchant =
                    merchant.getFranchise() != null;

            LocalDateTime transactionDateTime =
                    vt.getDate();

            Double rateToUse;

            if (isFranchiseMerchant && cr.getMerchantRate() != null) {

                Double historicalRate =
                        getHistoricalRateIfChanged(
                                cr,
                                transactionDateTime,
                                "merchant_rate"
                        );

                rateToUse =
                        historicalRate != null
                                ? historicalRate
                                : cr.getMerchantRate();

                log.info("[SETTLEMENT] Franchise merchant | applicableRate={}",
                        rateToUse);

            } else {

                Double historicalRate =
                        getHistoricalRateIfChanged(
                                cr,
                                transactionDateTime,
                                "rate"
                        );

                rateToUse =
                        historicalRate != null
                                ? historicalRate
                                : cr.getRate();

                log.info("[SETTLEMENT] Direct merchant | applicableRate={}",
                        rateToUse);
            }

            if (rateToUse == null) {

                log.warn("[SETTLEMENT] No applicable rate configured");

                return SettlementCandidateDTO.notFound(vt, "NO_RATE_CONFIGURED");
            }

            BigDecimal feePct =
                    amount.signum() > 0
                            ? BigDecimal.valueOf(rateToUse).movePointLeft(2)
                            : BigDecimal.ZERO;

            BigDecimal fee =
                    amount.multiply(feePct)
                            .setScale(2, RoundingMode.HALF_UP);

            if (fee.compareTo(amount.abs()) > 0) {
                fee = amount.abs();
            }

            BigDecimal net =
                    amount.subtract(fee)
                            .setScale(2, RoundingMode.HALF_UP);

            log.info("[SETTLEMENT] Calculation completed | amount={} | rate={} | fee={} | net={}",
                    amount,
                    rateToUse,
                    fee,
                    net);

            log.info("[SETTLEMENT] Settlement candidate created successfully | txnRef={}",
                    vt.getTransactionReferenceId());

            return new SettlementCandidateDTO(
                    vt.getInternalId(),
                    vt.getTransactionReferenceId(),
                    vt.getDate(),
                    amount,
                    vt.getCardType(),
                    vt.getBrandType(),
                    cardName,
                    rateToUse,
                    fee,
                    net,
                    null
            );

        } catch (Exception ex) {

            log.error("[SETTLEMENT] Error mapping transaction | internalId={} | txnRef={}",
                    vt.getInternalId(),
                    vt.getTransactionReferenceId(),
                    ex);

            return SettlementCandidateDTO.notFound(
                    vt,
                    "MAPPING_ERROR: " + ex.getMessage()
            );
        }
    }

    /**
     * Resolves the ProductCategory for a vendor transaction using the lookup chain:
     *   VendorTransactions.paymentGateway  →  matches Product.brand (case-insensitive)
     *   →  Product.productCategory
     *
     * The device is already resolved, so we use it directly.
     * Returns null (not throws) so callers can fall back gracefully.
     */
    private ProductCategory resolveProductCategoryFromGateway(VendorTransactions vt, ProductSerialNumbers device) {
        try {
            // First try: use the product already associated with the device — it carries productCategory
            Product deviceProduct = device.getProduct();
            if (deviceProduct != null && deviceProduct.getProductCategory() != null) {
                // Verify the product's brand matches the transaction's paymentGateway
                String gateway = vt.getPaymentGateway();
                if (gateway != null && !gateway.isBlank()) {
                    if (gateway.equalsIgnoreCase(deviceProduct.getBrand())) {
                        log.debug("productCategory resolved from device product: brand={} categoryId={}",
                                deviceProduct.getBrand(), deviceProduct.getProductCategory().getId());
                        return deviceProduct.getProductCategory();
                    }
                    // Gateway present but doesn't match — look up by brand from product table
                    log.debug("paymentGateway={} does not match device product brand={}, looking up by brand",
                            gateway, deviceProduct.getBrand());
                    List<Product> byBrand = productRepository.findByBrandIgnoreCaseOrderByProductNameAsc(gateway);
                    if (!byBrand.isEmpty() && byBrand.get(0).getProductCategory() != null) {
                        log.debug("productCategory resolved from brand lookup: brand={} categoryId={}",
                                gateway, byBrand.get(0).getProductCategory().getId());
                        return byBrand.get(0).getProductCategory();
                    }
                } else {
                    // No paymentGateway on transaction — use device product's category directly
                    log.debug("No paymentGateway on txn, using device product category: categoryId={}",
                            deviceProduct.getProductCategory().getId());
                    return deviceProduct.getProductCategory();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve productCategory for txn={}: {}",
                    vt.getTransactionReferenceId(), e.getMessage());
        }
        return null;
    }

    // ==================== READ-ONLY METHODS ====================

    @Transactional(readOnly = true)
    public MerchantSettlementBatch getBatchById(Long batchId) {
        return batchRepo.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
    }

    @Transactional(readOnly = true)
    public BatchProgressDTO getBatchProgress(Long batchId) {
        MerchantSettlementBatch batch = getBatchById(batchId);

        BatchProgressDTO progress = new BatchProgressDTO();
        progress.setBatchId(batchId);
        progress.setStatus(batch.getStatus());
        progress.setTotalTransactions(batch.getTotalTransactions());
        progress.setProcessedTransactions(batch.getProcessedTransactions());
        progress.setFailedTransactions(batch.getFailedTransactions());
        progress.setTotalAmount(batch.getTotalAmount());
        progress.setTotalFees(batch.getTotalFees());
        progress.setTotalNetAmount(batch.getTotalNetAmount());
        progress.setProcessingStartedAt(batch.getProcessingStartedAt());
        progress.setProcessingCompletedAt(batch.getProcessingCompletedAt());
        progress.setErrorMessage(batch.getErrorMessage());

        if (batch.getTotalTransactions() != null && batch.getTotalTransactions() > 0) {
            int totalProcessed = (batch.getProcessedTransactions() != null ? batch.getProcessedTransactions() : 0) +
                    (batch.getFailedTransactions() != null ? batch.getFailedTransactions() : 0);
            progress.setProgressPercentage((totalProcessed * 100.0) / batch.getTotalTransactions());
        } else {
            progress.setProgressPercentage(0.0);
        }

        return progress;
    }

    @Transactional(readOnly = true)
    public List<MerchantSettlementBatch> getAllBatchesByMerchant(Long merchantId, int limit) {
        return batchRepo.findByMerchantIdOrderByCreatedAtDesc(merchantId, PageRequest.of(0, limit)).getContent();
    }

    // ==================== UTILITY METHODS ====================

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeCardName(String brandType, String cardType) {
        if (brandType == null) brandType = "";
        if (cardType == null) cardType = "";
        return (brandType + " " + cardType).trim();
    }

    @Transactional
    public void markBatchClosed(Long id) {
        batchRepo.updateStatusById(id, "CLOSED");
    }


    // ==================== METHOD TO CHECK IF RATES WHERE EDIT OR NOT ====================
    private Double getHistoricalRateIfChanged(CardRate cardRate, LocalDateTime transactionDate, String rateField) {
        // Check if rate was changed after transaction date
        List<EntityHistory> changes = entityHistoryRepository.findChangesAfterDate(
                "CardRate",
                cardRate.getId(),
                rateField,
                transactionDate
        );
    for(EntityHistory e: changes){
        System.out.println(e.getId());
    }
        if (!changes.isEmpty()) {
            // Rate was changed after transaction - use the old value
            EntityHistory firstChange = changes.get(0);
            String oldValue = firstChange.getOldValue();

            if (oldValue != null && !oldValue.isEmpty()) {
                try {
                    return Double.parseDouble(oldValue);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse old rate value: {}", oldValue, e);
                }
            }
        }

        // No changes after transaction date, or parsing failed - use current rate
        return null;
    }
    // ==================== INNER CLASSES ====================

    private record DeviceIdentifiers(String mid, String tid) {

    }

    private static class BatchSummary {
        int validCount = 0;
        int invalidCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        void addValid(BigDecimal amount, BigDecimal fee, BigDecimal net) {
            validCount++;
            totalAmount = totalAmount.add(amount);
            totalFees = totalFees.add(fee);
            totalNet = totalNet.add(net);
        }

        void addInvalid() {
            invalidCount++;
        }
    }
}