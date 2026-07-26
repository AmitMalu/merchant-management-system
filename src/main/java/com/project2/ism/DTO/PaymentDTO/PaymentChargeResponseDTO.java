package com.project2.ism.DTO.PaymentDTO;

import com.project2.ism.Enum.ChargeType;
import com.project2.ism.Enum.RequestedType;
import com.project2.ism.Model.Payment.PaymentMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PaymentChargeResponseDTO {

    private Long id;
    private PaymentMode mode;
    private RequestedType chargeScope;

    private Long merchantId;
    private String merchantName;

    private Long franchiseId;
    private String franchiseName;

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
            RequestedType chargeScope,
            Long merchantId,
            String merchantName,
            Long franchiseId,
            String franchiseName,
            Boolean status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<SlabResponseDTO> slabs
    ) {
        this.id = id;
        this.mode = mode;
        this.chargeScope = chargeScope;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.franchiseId = franchiseId;
        this.franchiseName = franchiseName;
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

    public RequestedType getChargeScope() { return chargeScope; }
    public void setChargeScope(RequestedType chargeScope) { this.chargeScope = chargeScope; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public Long getFranchiseId() {return franchiseId;}
    public void setFranchiseId(Long franchiseId) {this.franchiseId = franchiseId;}

    public String getFranchiseName() {return franchiseName;}
    public void setFranchiseName(String franchiseName) {this.franchiseName = franchiseName;}

    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<SlabResponseDTO> getSlabs() { return slabs; }
    public void setSlabs(List<SlabResponseDTO> slabs) { this.slabs = slabs; }
}
