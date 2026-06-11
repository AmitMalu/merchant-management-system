package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.PaymentChargeRequestDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentChargeResponseDTO;
import com.project2.ism.Enum.ChargeType;
import com.project2.ism.Model.Payment.PaymentChargeSlab;
import com.project2.ism.Model.Payment.PaymentCharges;
import com.project2.ism.Model.Payment.PaymentMode;
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
import java.util.stream.Collectors;

@Service
public class PaymentChargeService {


    private final Logger log = LoggerFactory.getLogger(PaymentChargeService.class);


    private final PaymentChargesRepository paymentChargeRepository;
    private final PaymentModeRepository paymentModeRepository;

    public PaymentChargeService(PaymentChargesRepository paymentChargeRepository, PaymentModeRepository paymentModeRepository) {
        this.paymentChargeRepository = paymentChargeRepository;
        this.paymentModeRepository = paymentModeRepository;
    }

    // ================= CREATE =================

    @Transactional
    public PaymentChargeResponseDTO createPaymentCharge(PaymentChargeRequestDTO dto) {

        // 1. Load mode using modeId (DTO no longer carries PaymentMode)
        PaymentMode mode = paymentModeRepository.findById(dto.getModeId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid payment mode ID: " + dto.getModeId()));

        // 2. uniqueness: only 1 charge config per mode
        if (paymentChargeRepository.existsByMode(mode)) {
            throw new IllegalArgumentException("Charge configuration already exists for mode: " + mode.getCode());
        }

        validateSlabs(dto.getSlabs());

        PaymentCharges charges = new PaymentCharges(mode, dto.getStatus());

        // add slabs
        dto.getSlabs().forEach(slabDTO -> {
            PaymentChargeSlab slab = new PaymentChargeSlab(
                    slabDTO.getMinAmount(),
                    slabDTO.getMaxAmount(),
                    slabDTO.getChargeType(),
                    slabDTO.getChargeValue()
            );
            charges.addSlab(slab);
        });

        PaymentCharges saved = paymentChargeRepository.save(charges);

        return convertToResponse(saved);
    }

    // ================= READ BY ID =================

    @Transactional(readOnly = true)
    public PaymentChargeResponseDTO getPaymentChargeById(Long id) {
        PaymentCharges charges = paymentChargeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No payout charge found for id " + id));
        return convertToResponse(charges);
    }

    // ================= LIST / PAGINATION =================

    @Transactional(readOnly = true)
    public Page<PaymentChargeResponseDTO> getAllPaymentCharges(int page, int size, String sortBy, String dir) {
        Pageable pageable = PageRequest.of(page, size,
                dir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

        return paymentChargeRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    // ================= SEARCH =================

    @Transactional(readOnly = true)
    public Page<PaymentChargeResponseDTO> searchPayoutCharges(String term, int page, int size, String sortBy, String dir) {

        Pageable pageable = PageRequest.of(page, size,
                dir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

        return paymentChargeRepository.searchByMode(term, pageable).map(this::convertToResponse);
    }

    // ================= UPDATE =================

    @Transactional
    public PaymentChargeResponseDTO updatePaymentCharge(Long id, PaymentChargeRequestDTO dto) {

       // 1. Get existing configuration
        PaymentCharges existing = paymentChargeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Charge config not found: " + id));

        // 2. Fetch mode via DTO modeId
        PaymentMode newMode = paymentModeRepository.findById(dto.getModeId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid payment mode ID: " + dto.getModeId()));

        // 3. Enforce uniqueness if mode has changed
        if (!existing.getMode().getId().equals(dto.getModeId())) {
            if (paymentChargeRepository.existsByModeAndIdNot(newMode, id)) {
                throw new IllegalArgumentException("Charge configuration already exists for mode: " + newMode.getCode());
            }
        }

        validateSlabs(dto.getSlabs());

        existing.setMode(newMode);
        existing.setStatus(dto.getStatus());

        // replace slabs using helper method
        existing.getSlabs().clear();
        dto.getSlabs().forEach(slabDTO -> {
            PaymentChargeSlab slab = new PaymentChargeSlab(
                    slabDTO.getMinAmount(),
                    slabDTO.getMaxAmount(),
                    slabDTO.getChargeType(),
                    slabDTO.getChargeValue()
            );
            existing.addSlab(slab);
        });

        return convertToResponse(existing);
    }

    // ================= DELETE =================

    @Transactional
    public void deletePaymentCharge(Long id) {
        if (!paymentChargeRepository.existsById(id)) {
            throw new IllegalArgumentException("No payout charge found for id " + id);
        }
        paymentChargeRepository.deleteById(id);
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
    public BigDecimal calculateCharges(BigDecimal amount, String paymentMode) {
        log.debug("Calculating charges for amount={} mode={}", amount, paymentMode);

        // Get payment mode
        var mode = paymentModeRepository.findByCode(paymentMode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid payment mode: " + paymentMode));

        // Get active charge configuration for this mode
        PaymentCharges charges = paymentChargeRepository.findByModeAndStatusTrue(mode)
                .orElseThrow(() -> new IllegalStateException(
                        "No active charges configured for payment mode: " + paymentMode));

        // Find applicable slab
        PaymentChargeSlab applicableSlab = charges.getSlabs().stream()
                .filter(slab -> amount.compareTo(slab.getMinAmount()) >= 0 &&
                        amount.compareTo(slab.getMaxAmount()) <= 0)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Amount " + amount + " does not fall in any configured slab for mode: " + paymentMode));

        // Calculate charge based on type
        BigDecimal charge;
        if (applicableSlab.getChargeType() == ChargeType.PERCENTAGE) {
            charge = amount.multiply(applicableSlab.getChargeValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            charge = applicableSlab.getChargeValue();
        }

        log.debug("Calculated charge={} for amount={} using slab: min={} max={} type={} value={}",
                charge, amount, applicableSlab.getMinAmount(), applicableSlab.getMaxAmount(),
                applicableSlab.getChargeType(), applicableSlab.getChargeValue());

        return charge.setScale(2, RoundingMode.HALF_UP);
    }

    // ================= MAPPER =================

    private PaymentChargeResponseDTO convertToResponse(PaymentCharges charges) {
        var slabs = charges.getSlabs().stream()
                .sorted(Comparator.comparing(PaymentChargeSlab::getMinAmount))
                .map(slab -> new PaymentChargeResponseDTO.SlabResponseDTO(
                        slab.getId(),
                        slab.getMinAmount(),
                        slab.getMaxAmount(),
                        slab.getChargeType(),
                        slab.getChargeValue()
                ))
                .collect(Collectors.toList());

        return new PaymentChargeResponseDTO(
                charges.getId(),
                charges.getMode(),
                charges.getStatus(),
                charges.getCreatedAt(),
                charges.getUpdatedAt(),
                slabs
        );
    }
}
