package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.PaymentChargeRequestDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentChargeResponseDTO;
import com.project2.ism.Enum.ChargeType;
import com.project2.ism.Enum.RequestedType;
import com.project2.ism.Model.Payment.PaymentChargeSlab;
import com.project2.ism.Model.Payment.PaymentCharges;
import com.project2.ism.Model.Payment.PaymentMode;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PaymentChargesRepository;
import com.project2.ism.Repository.PaymentModeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PaymentChargeService {


    private final Logger log = LoggerFactory.getLogger(PaymentChargeService.class);


    private final PaymentChargesRepository paymentChargeRepository;
    private final PaymentModeRepository paymentModeRepository;
    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;

    public PaymentChargeService(PaymentChargesRepository paymentChargeRepository, PaymentModeRepository paymentModeRepository, MerchantRepository merchantRepository, FranchiseRepository franchiseRepository) {
        this.paymentChargeRepository = paymentChargeRepository;
        this.paymentModeRepository = paymentModeRepository;
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
    }

    // ================= CREATE =================

    @Transactional
    public PaymentChargeResponseDTO createPaymentCharge(
            PaymentChargeRequestDTO dto
    ) {

        // 1. Load payment mode
        PaymentMode mode = paymentModeRepository
                .findById(dto.getModeId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid payment mode ID: " + dto.getModeId()
                        )
                );

        // 2. Validate scope and supplied IDs
        validateChargeScope(dto);

        // 3. Load merchant/franchise according to scope
        Merchant merchant = null;
        Franchise franchise = null;

        switch (dto.getChargeScope()) {

            case GLOBAL -> {
                // Both remain null
            }

            case DIRECT_MERCHANT -> {
                merchant = merchantRepository
                        .findById(dto.getMerchantId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid merchant ID: "
                                                + dto.getMerchantId()
                                )
                        );
            }

            case FRANCHISE -> {
                franchise = franchiseRepository
                        .findById(dto.getFranchiseId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid franchise ID: "
                                                + dto.getFranchiseId()
                                )
                        );
            }

            case FRANCHISE_MERCHANT -> {
                franchise = franchiseRepository
                        .findById(dto.getFranchiseId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid franchise ID: "
                                                + dto.getFranchiseId()
                                )
                        );

                merchant = merchantRepository
                        .findById(dto.getMerchantId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid merchant ID: "
                                                + dto.getMerchantId()
                                )
                        );

                validateMerchantBelongsToFranchise(
                        merchant,
                        franchise
                );
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported charge scope: "
                            + dto.getChargeScope()
            );
        }

        // 4. Check duplicate configuration
        validateDuplicateConfiguration(
                mode,
                dto.getChargeScope(),
                merchant,
                franchise
        );

        // 5. Validate slabs
        validateSlabs(dto.getSlabs());

        // 6. Create parent entity
        PaymentCharges charges = new PaymentCharges();

        charges.setMode(mode);
        charges.setChargeScope(dto.getChargeScope());
        charges.setMerchant(merchant);
        charges.setFranchise(franchise);
        charges.setStatus(dto.getStatus());

        // 7. Add slab entities
        dto.getSlabs().forEach(slabDTO -> {

            PaymentChargeSlab slab =
                    new PaymentChargeSlab(
                            slabDTO.getMinAmount(),
                            slabDTO.getMaxAmount(),
                            slabDTO.getChargeType(),
                            slabDTO.getChargeValue()
                    );

            charges.addSlab(slab);
        });

        // 8. Save
        PaymentCharges saved =
                paymentChargeRepository.save(charges);

        // Optional: flush so DB-generated slab IDs become available immediately
        paymentChargeRepository.flush();

        // 9. Return response
        return convertToResponse(saved);
    }

    // ================= READ BY ID =================

    @Transactional(readOnly = true)
    public PaymentChargeResponseDTO getPaymentChargeById(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Payout charge ID is required"
            );
        }

        PaymentCharges charges =
                paymentChargeRepository
                        .findByIdWithDetails(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "No payout charge found for id " + id
                                )
                        );

        return convertToResponse(charges);
    }

    // ================= LIST / PAGINATION =================

    @Transactional(readOnly = true)
    public Page<PaymentChargeResponseDTO> getAllPaymentCharges(
            int page,
            int size,
            String sortBy,
            String dir
    ) {

        validatePagination(page, size);

        Pageable pageable = createPageable(page, size, sortBy, dir);

        return paymentChargeRepository
                .findAll(pageable)
                .map(this::convertToResponse);
    }

    // ================= SEARCH =================

    @Transactional(readOnly = true)
    public Page<PaymentChargeResponseDTO> searchPayoutCharges(
            String term,
            int page,
            int size,
            String sortBy,
            String dir
    ) {

        validatePagination(page, size);

        String normalizedSearchTerm =
                term == null ? "" : term.trim();

        Pageable pageable = createPageable(page, size, sortBy, dir );

        return paymentChargeRepository
                .searchPaymentCharges(
                        normalizedSearchTerm,
                        pageable
                )
                .map(this::convertToResponse);
    }

    // ================= UPDATE =================

    // ================= UPDATE =================

    @Transactional
    public PaymentChargeResponseDTO updatePaymentCharge(
            Long id,
            PaymentChargeRequestDTO dto
    ) {

        // 1. Validate ID
        if (id == null) {
            throw new IllegalArgumentException(
                    "Charge configuration ID is required"
            );
        }

        // 2. Get existing configuration
        PaymentCharges existing =
                paymentChargeRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Charge configuration not found for ID: " + id
                                )
                        );

        // 3. Load payment mode
        PaymentMode newMode =
                paymentModeRepository.findById(dto.getModeId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid payment mode ID: " + dto.getModeId()
                                )
                        );

        // 4. Validate scope and request IDs
        validateChargeScope(dto);

        // 5. Resolve merchant and franchise
        Merchant merchant = null;
        Franchise franchise = null;

        switch (dto.getChargeScope()) {

            case GLOBAL -> {
                // Both remain null
            }

            case DIRECT_MERCHANT -> {

                merchant = merchantRepository
                        .findById(dto.getMerchantId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid merchant ID: "
                                                + dto.getMerchantId()
                                )
                        );
            }

            case FRANCHISE -> {

                franchise = franchiseRepository
                        .findById(dto.getFranchiseId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid franchise ID: "
                                                + dto.getFranchiseId()
                                )
                        );
            }

            case FRANCHISE_MERCHANT -> {

                franchise = franchiseRepository
                        .findById(dto.getFranchiseId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid franchise ID: "
                                                + dto.getFranchiseId()
                                )
                        );

                merchant = merchantRepository
                        .findById(dto.getMerchantId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid merchant ID: "
                                                + dto.getMerchantId()
                                )
                        );

                validateMerchantBelongsToFranchise(
                        merchant,
                        franchise
                );
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported charge scope: "
                            + dto.getChargeScope()
            );
        }

        // 6. Validate duplicate excluding this current record
        validateDuplicateConfigurationForUpdate(
                id,
                newMode,
                dto.getChargeScope(),
                merchant,
                franchise
        );

        // 7. Validate slabs
        validateSlabs(dto.getSlabs());

        // 8. Update parent fields
        existing.setMode(newMode);
        existing.setChargeScope(dto.getChargeScope());
        existing.setMerchant(merchant);
        existing.setFranchise(franchise);
        existing.setStatus(dto.getStatus());

        // 9. Remove old slabs
        existing.getSlabs().clear();

        // Optional but useful when orphanRemoval is enabled
        paymentChargeRepository.flush();

        // 10. Add updated slabs
        dto.getSlabs().forEach(slabDTO -> {

            PaymentChargeSlab slab =
                    new PaymentChargeSlab(
                            slabDTO.getMinAmount(),
                            slabDTO.getMaxAmount(),
                            slabDTO.getChargeType(),
                            slabDTO.getChargeValue()
                    );

            existing.addSlab(slab);
        });

        // 11. Save updated configuration
        PaymentCharges updated =
                paymentChargeRepository.save(existing);

        paymentChargeRepository.flush();

        // 12. Return response
        return convertToResponse(updated);
    }

    // ================= DELETE =================

    @Transactional
    public void deletePaymentCharge(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Payout charge ID is required"
            );
        }

        PaymentCharges paymentCharge =
                paymentChargeRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "No payout charge found for id " + id
                                )
                        );

        paymentChargeRepository.delete(paymentCharge);
    }

    // ================= COUNTS =================

    @Transactional(readOnly = true)
    public long getTotalCount() {
        return paymentChargeRepository.count();
    }

    @Transactional(readOnly = true)
    public long getActiveCount() {
        return paymentChargeRepository.countByStatus(true);
    }

    // ================= VALIDATION =================

    private void validateSlabs(List<PaymentChargeRequestDTO.SlabDTO> slabs) {

        // min < max
        for (int i = 0; i < slabs.size(); i++) {
            var slab = slabs.get(i);
            if (slab.getMinAmount().compareTo(slab.getMaxAmount()) >= 0) {
                throw new IllegalArgumentException("Min amount must be < max amount in slab #" + (i + 1));
            }
        }

        // overlapping check
        List<PaymentChargeRequestDTO.SlabDTO> sorted = slabs.stream()
                .sorted(Comparator.comparing(PaymentChargeRequestDTO.SlabDTO::getMinAmount))
                .toList();

        for (int i = 0; i < sorted.size() - 1; i++) {
            if (sorted.get(i).getMaxAmount().compareTo(sorted.get(i + 1).getMinAmount()) >= 0) {
                throw new IllegalArgumentException("Slabs have overlapping ranges");
            }
        }
    }


    /**
     * Calculate charges for given amount and payment mode
     */
    //@Transactional(readOnly = true)
//    public BigDecimal calculateCharges(BigDecimal amount, String paymentMode) {
//        log.debug("Calculating charges for amount={} mode={}", amount, paymentMode);
//
//        // Get payment mode
//        var mode = paymentModeRepository.findByCode(paymentMode)
//                .orElseThrow(() -> new IllegalArgumentException("Invalid payment mode: " + paymentMode));
//
//        // Get active charge configuration for this mode
//        PaymentCharges charges = paymentChargeRepository.findByModeAndStatusTrue(mode)
//                .orElseThrow(() -> new IllegalStateException(
//                        "No active charges configured for payment mode: " + paymentMode));
//
//        // Find applicable slab
//        PaymentChargeSlab applicableSlab = charges.getSlabs().stream()
//                .filter(slab -> amount.compareTo(slab.getMinAmount()) >= 0 &&
//                        amount.compareTo(slab.getMaxAmount()) <= 0)
//                .findFirst()
//                .orElseThrow(() -> new IllegalArgumentException(
//                        "Amount " + amount + " does not fall in any configured slab for mode: " + paymentMode));
//
//        // Calculate charge based on type
//        BigDecimal charge;
//        if (applicableSlab.getChargeType() == ChargeType.PERCENTAGE) {
//            charge = amount.multiply(applicableSlab.getChargeValue())
//                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
//        } else {
//            charge = applicableSlab.getChargeValue();
//        }
//
//        log.debug("Calculated charge={} for amount={} using slab: min={} max={} type={} value={}",
//                charge, amount, applicableSlab.getMinAmount(), applicableSlab.getMaxAmount(),
//                applicableSlab.getChargeType(), applicableSlab.getChargeValue());
//
//        return charge.setScale(2, RoundingMode.HALF_UP);
//    }

    //@Transactional(readOnly = true)
    public BigDecimal calculateCharges(
            BigDecimal amount,
            String paymentMode,
            Long merchantId
    ) {

        log.debug(
                "Calculating payout charges | amount={} | paymentMode={} | merchantId={}",
                amount,
                paymentMode,
                merchantId
        );

        // 1. Validate request
        if (amount == null) {
            throw new IllegalArgumentException(
                    "Amount is required for charge calculation"
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }

        if (paymentMode == null || paymentMode.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Payment mode is required"
            );
        }

        if (merchantId == null) {
            throw new IllegalArgumentException(
                    "Merchant ID is required for charge calculation"
            );
        }

        // 2. Load payment mode
        PaymentMode mode = paymentModeRepository
                .findByCode(paymentMode.trim())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid payment mode: " + paymentMode
                        )
                );

        // 3. Load merchant
        Merchant merchant = merchantRepository
                .findById(merchantId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid merchant ID: " + merchantId
                        )
                );

        // 4. Resolve configuration according to merchant type
        PaymentCharges charges =
                resolveApplicableChargeConfiguration(
                        mode,
                        merchant
                );

        // 5. Find amount slab
        PaymentChargeSlab applicableSlab =
                charges.getSlabs()
                        .stream()
                        .filter(slab ->
                                amount.compareTo(
                                        slab.getMinAmount()
                                ) >= 0
                                        &&
                                        amount.compareTo(
                                                slab.getMaxAmount()
                                        ) <= 0
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Amount "
                                                + amount
                                                + " does not fall in any configured slab"
                                                + " for payment mode "
                                                + paymentMode
                                                + " and charge scope "
                                                + charges.getChargeScope()
                                )
                        );

        // 6. Calculate charge
        BigDecimal charge;

        if (applicableSlab.getChargeType()
                == ChargeType.PERCENTAGE) {

            charge = amount
                    .multiply(
                            applicableSlab.getChargeValue()
                    )
                    .divide(
                            BigDecimal.valueOf(100),
                            2,
                            RoundingMode.HALF_UP
                    );

        } else {

            charge = applicableSlab.getChargeValue();
        }

        BigDecimal finalCharge =
                charge.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        log.info(
                "Payout charge calculated successfully | merchantId={} | paymentMode={} | amount={} | scope={} | chargeConfigId={} | slabId={} | charge={}",
                merchantId,
                mode.getCode(),
                amount,
                charges.getChargeScope(),
                charges.getId(),
                applicableSlab.getId(),
                finalCharge
        );

        return finalCharge;
    }

    //=================== Add configuration resolver ====================

    private PaymentCharges resolveApplicableChargeConfiguration(
            PaymentMode mode,
            Merchant merchant
    ) {

        /*
         * Franchise merchant priority:
         *
         * 1. Specific franchise + merchant rate
         * 2. Franchise-wide rate
         * 3. Global rate
         */
        if (merchant.getFranchise() != null) {

            Franchise franchise =
                    merchant.getFranchise();

            Optional<PaymentCharges> franchiseMerchantCharge =
                    paymentChargeRepository
                            .findActiveFranchiseMerchantCharge(
                                    mode,
                                    RequestedType.FRANCHISE_MERCHANT,
                                    merchant,
                                    franchise
                            );

            if (franchiseMerchantCharge.isPresent()) {

                log.debug(
                        "Using FRANCHISE_MERCHANT charge | mode={} | merchantId={} | franchiseId={} | chargeConfigId={}",
                        mode.getCode(),
                        merchant.getId(),
                        franchise.getId(),
                        franchiseMerchantCharge.get().getId()
                );

                return franchiseMerchantCharge.get();
            }

            Optional<PaymentCharges> franchiseCharge =
                    paymentChargeRepository
                            .findActiveFranchiseCharge(
                                    mode,
                                    RequestedType.FRANCHISE,
                                    franchise
                            );

            if (franchiseCharge.isPresent()) {

                log.debug(
                        "Using FRANCHISE charge | mode={} | merchantId={} | franchiseId={} | chargeConfigId={}",
                        mode.getCode(),
                        merchant.getId(),
                        franchise.getId(),
                        franchiseCharge.get().getId()
                );

                return franchiseCharge.get();
            }
        }

        /*
         * Direct merchant:
         *
         * 1. Specific direct-merchant rate
         * 2. Global rate
         */
        else {

            Optional<PaymentCharges> directMerchantCharge =
                    paymentChargeRepository
                            .findActiveDirectMerchantCharge(
                                    mode,
                                    RequestedType.DIRECT_MERCHANT,
                                    merchant
                            );

            if (directMerchantCharge.isPresent()) {

                log.debug(
                        "Using DIRECT_MERCHANT charge | mode={} | merchantId={} | chargeConfigId={}",
                        mode.getCode(),
                        merchant.getId(),
                        directMerchantCharge.get().getId()
                );

                return directMerchantCharge.get();
            }
        }

        // Final fallback for both merchant types
        Optional<PaymentCharges> globalCharge =
                paymentChargeRepository
                        .findActiveGlobalCharge(
                                mode,
                                RequestedType.GLOBAL
                        );

        if (globalCharge.isPresent()) {

            log.debug(
                    "Using GLOBAL charge | mode={} | merchantId={} | chargeConfigId={}",
                    mode.getCode(),
                    merchant.getId(),
                    globalCharge.get().getId()
            );

            return globalCharge.get();
        }

        throw new IllegalStateException(
                buildMissingChargeConfigurationMessage(
                        mode,
                        merchant
                )
        );
    }

    //==================== Add error-message helper ==================

    private String buildMissingChargeConfigurationMessage(
            PaymentMode mode,
            Merchant merchant
    ) {

        if (merchant.getFranchise() != null) {

            return "No active payout charge configured for payment mode "
                    + mode.getCode()
                    + ", merchant ID "
                    + merchant.getId()
                    + ", or franchise ID "
                    + merchant.getFranchise().getId()
                    + ". Global configuration is also unavailable.";
        }

        return "No active payout charge configured for payment mode "
                + mode.getCode()
                + " and direct merchant ID "
                + merchant.getId()
                + ". Global configuration is also unavailable.";
    }

    // ================= MAPPER =================

    private PaymentChargeResponseDTO convertToResponse(
            PaymentCharges charges
    ) {

        List<PaymentChargeResponseDTO.SlabResponseDTO> slabs =
                charges.getSlabs()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        PaymentChargeSlab::getMinAmount
                                )
                        )
                        .map(slab ->
                                new PaymentChargeResponseDTO.SlabResponseDTO(
                                        slab.getId(),
                                        slab.getMinAmount(),
                                        slab.getMaxAmount(),
                                        slab.getChargeType(),
                                        slab.getChargeValue()
                                )
                        )
                        .collect(Collectors.toList());

        Long merchantId = null;
        String merchantName = null;

        if (charges.getMerchant() != null) {
            merchantId = charges.getMerchant().getId();
            merchantName =
                    charges.getMerchant().getBusinessName();
        }

        Long franchiseId = null;
        String franchiseName = null;

        if (charges.getFranchise() != null) {
            franchiseId = charges.getFranchise().getId();
            franchiseName =
                    charges.getFranchise().getFranchiseName();
        }

        return new PaymentChargeResponseDTO(
                charges.getId(),
                charges.getMode(),
                charges.getChargeScope(),
                merchantId,
                merchantName,
                franchiseId,
                franchiseName,
                charges.getStatus(),
                charges.getCreatedAt(),
                charges.getUpdatedAt(),
                slabs
        );
    }


    //=============================== scope validation method ===============================

    private void validateChargeScope(
            PaymentChargeRequestDTO dto
    ) {

        if (dto.getChargeScope() == null) {
            throw new IllegalArgumentException(
                    "Charge scope is required"
            );
        }

        switch (dto.getChargeScope()) {

            case GLOBAL -> {

                if (dto.getMerchantId() != null) {
                    throw new IllegalArgumentException(
                            "Merchant ID must be empty for GLOBAL scope"
                    );
                }

                if (dto.getFranchiseId() != null) {
                    throw new IllegalArgumentException(
                            "Franchise ID must be empty for GLOBAL scope"
                    );
                }
            }

            case DIRECT_MERCHANT -> {

                if (dto.getMerchantId() == null) {
                    throw new IllegalArgumentException(
                            "Merchant ID is required for DIRECT_MERCHANT scope"
                    );
                }

                if (dto.getFranchiseId() != null) {
                    throw new IllegalArgumentException(
                            "Franchise ID must be empty for DIRECT_MERCHANT scope"
                    );
                }
            }

            case FRANCHISE -> {

                if (dto.getFranchiseId() == null) {
                    throw new IllegalArgumentException(
                            "Franchise ID is required for FRANCHISE scope"
                    );
                }

                if (dto.getMerchantId() != null) {
                    throw new IllegalArgumentException(
                            "Merchant ID must be empty for FRANCHISE scope"
                    );
                }
            }

            case FRANCHISE_MERCHANT -> {

                if (dto.getFranchiseId() == null) {
                    throw new IllegalArgumentException(
                            "Franchise ID is required for FRANCHISE_MERCHANT scope"
                    );
                }

                if (dto.getMerchantId() == null) {
                    throw new IllegalArgumentException(
                            "Merchant ID is required for FRANCHISE_MERCHANT scope"
                    );
                }
            }

            default -> throw new IllegalArgumentException(
                    "Invalid charge scope: "
                            + dto.getChargeScope()
            );
        }
    }


    //====================== Add duplicate validation ================================

    private void validateDuplicateConfiguration(
            PaymentMode mode,
            RequestedType chargeScope,
            Merchant merchant,
            Franchise franchise
    ) {

        boolean exists;

        switch (chargeScope) {

            case GLOBAL -> exists =
                    paymentChargeRepository
                            .existsByModeAndChargeScopeAndMerchantIsNullAndFranchiseIsNull(
                                    mode,
                                    chargeScope
                            );

            case DIRECT_MERCHANT -> exists =
                    paymentChargeRepository
                            .existsByModeAndChargeScopeAndMerchantAndFranchiseIsNull(
                                    mode,
                                    chargeScope,
                                    merchant
                            );

            case FRANCHISE -> exists =
                    paymentChargeRepository
                            .existsByModeAndChargeScopeAndMerchantIsNullAndFranchise(
                                    mode,
                                    chargeScope,
                                    franchise
                            );

            case FRANCHISE_MERCHANT -> exists =
                    paymentChargeRepository
                            .existsByModeAndChargeScopeAndMerchantAndFranchise(
                                    mode,
                                    chargeScope,
                                    merchant,
                                    franchise
                            );

            default -> throw new IllegalArgumentException(
                    "Unsupported charge scope: " + chargeScope
            );
        }

        if (exists) {
            throw new IllegalArgumentException(
                    "Charge configuration already exists for mode: "
                            + mode.getCode()
                            + " and scope: "
                            + chargeScope
            );
        }
    }


    //======================== Validate that merchant belongs to selected franchise  ===================

    private void validateMerchantBelongsToFranchise(
            Merchant merchant,
            Franchise franchise
    ) {

        if (merchant.getFranchise() == null) {
            throw new IllegalArgumentException(
                    "Selected merchant is not assigned to any franchise"
            );
        }

        if (!merchant.getFranchise().getId()
                .equals(franchise.getId())) {

            throw new IllegalArgumentException(
                    "Selected merchant does not belong to selected franchise"
            );
        }
    }

    //====================  safe pageable helper ========================

    private Pageable createPageable(
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {

        String safeSortProperty = switch (
                sortBy == null ? "" : sortBy.trim()
                ) {
            case "id" -> "id";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            case "chargeScope" -> "chargeScope";
            case "modeCode" -> "mode.code";
            case "modeDescription" -> "mode.description";
            case "merchantName" -> "merchant.merchantName";
            case "franchiseName" -> "franchise.franchiseName";
            default -> "id";
        };

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(safeSortProperty).ascending()
                : Sort.by(safeSortProperty).descending();

        return PageRequest.of(page, size, sort);
    }

    //==================== pagination validation ==================

    private void validatePagination(
            int page,
            int size
    ) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size <= 0) {
            throw new IllegalArgumentException(
                    "Page size must be greater than zero"
            );
        }

        if (size > 100) {
            throw new IllegalArgumentException(
                    "Page size cannot be greater than 100"
            );
        }
    }

    //================ Add duplicate validation for update =========================

    private void validateDuplicateConfigurationForUpdate(
            Long currentId,
            PaymentMode mode,
            RequestedType chargeScope,
            Merchant merchant,
            Franchise franchise
    ) {

        boolean exists;

        switch (chargeScope) {

            case GLOBAL -> exists =
                    paymentChargeRepository
                            .existsByModeAndChargeScopeAndMerchantIsNullAndFranchiseIsNullAndIdNot(
                                    mode,
                                    chargeScope,
                                    currentId
                            );

            case DIRECT_MERCHANT -> exists =
                    paymentChargeRepository
                            .existsByModeAndChargeScopeAndMerchantAndFranchiseIsNullAndIdNot(
                                    mode,
                                    chargeScope,
                                    merchant,
                                    currentId
                            );

            case FRANCHISE -> exists =
                    paymentChargeRepository
                            .existsByModeAndChargeScopeAndMerchantIsNullAndFranchiseAndIdNot(
                                    mode,
                                    chargeScope,
                                    franchise,
                                    currentId
                            );

            case FRANCHISE_MERCHANT -> exists =
                    paymentChargeRepository
                            .existsByModeAndChargeScopeAndMerchantAndFranchiseAndIdNot(
                                    mode,
                                    chargeScope,
                                    merchant,
                                    franchise,
                                    currentId
                            );

            default -> throw new IllegalArgumentException(
                    "Unsupported charge scope: " + chargeScope
            );
        }

        if (exists) {
            throw new IllegalArgumentException(
                    "Another charge configuration already exists for mode: "
                            + mode.getCode()
                            + ", scope: "
                            + chargeScope
            );
        }
    }
}
