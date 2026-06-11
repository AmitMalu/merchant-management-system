package com.project2.ism.DTO.PaymentDTO;

import com.project2.ism.Enum.ChargeType;
import com.project2.ism.Model.Payment.PaymentMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PaymentChargeResponseDTO {

    private Long id;
    private PaymentMode mode;
    private Boolean status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SlabResponseDTO> slabs;

    // ================= SLAB RESPONSE DTO =================

    public static class SlabResponseDTO {

        private Long id;  // DB primary key
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private ChargeType chargeType;
        private BigDecimal chargeValue;

        public SlabResponseDTO() {}

        public SlabResponseDTO(Long id, BigDecimal minAmount, BigDecimal maxAmount,
                               ChargeType chargeType, BigDecimal chargeValue) {
            this.id = id;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.chargeType = chargeType;
            this.chargeValue = chargeValue;
        }

        // Getters & Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public BigDecimal getMinAmount() { return minAmount; }
        public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }

        public BigDecimal getMaxAmount() { return maxAmount; }
        public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }

        public ChargeType getChargeType() { return chargeType; }
        public void setChargeType(ChargeType chargeType) { this.chargeType = chargeType; }

        public BigDecimal getChargeValue() { return chargeValue; }
        public void setChargeValue(BigDecimal chargeValue) { this.chargeValue = chargeValue; }
    }

    // ================= MAIN DTO =================

    public PaymentChargeResponseDTO() {}

    public PaymentChargeResponseDTO(
            Long id,
            PaymentMode mode,
            Boolean status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<SlabResponseDTO> slabs
    ) {
        this.id = id;
        this.mode = mode;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.slabs = slabs;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PaymentMode getMode() { return mode; }
    public void setMode(PaymentMode mode) { this.mode = mode; }

    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<SlabResponseDTO> getSlabs() { return slabs; }
    public void setSlabs(List<SlabResponseDTO> slabs) { this.slabs = slabs; }
}
