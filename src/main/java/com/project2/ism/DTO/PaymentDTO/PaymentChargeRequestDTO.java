package com.project2.ism.DTO.PaymentDTO;

import com.project2.ism.Enum.ChargeType;
import com.project2.ism.Model.Payment.PaymentMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public class PaymentChargeRequestDTO {

    @NotNull(message = "Mode ID is required")
    private Long modeId;

    @NotNull(message = "Status is required")
    private Boolean status;

    @NotEmpty(message = "At least one slab is required")
    @Valid
    private List<SlabDTO> slabs;

    // ========= Nested Slab DTO =========

    public static class SlabDTO {

        // NOT REQUIRED — database generates slabId
        private Integer slabId;

        @NotNull(message = "Min amount is required")
        @DecimalMin(value = "0.00", message = "Min amount must be >= 0")
        private BigDecimal minAmount;

        @NotNull(message = "Max amount is required")
        @DecimalMin(value = "0.00", message = "Max amount must be >= 0")
        private BigDecimal maxAmount;

        @NotNull(message = "Charge type is required")
        private ChargeType chargeType;

        @NotNull(message = "Charge value is required")
        @DecimalMin(value = "0.00", message = "Charge value must be >= 0")
        private BigDecimal chargeValue;

        // Constructors
        public SlabDTO() { }

        public SlabDTO(Integer slabId, BigDecimal minAmount, BigDecimal maxAmount,
                       ChargeType chargeType, BigDecimal chargeValue) {
            this.slabId = slabId;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.chargeType = chargeType;
            this.chargeValue = chargeValue;
        }

        // getters & setters...

        public Integer getSlabId() {
            return slabId;
        }

        public void setSlabId(Integer slabId) {
            this.slabId = slabId;
        }

        public BigDecimal getMinAmount() {
            return minAmount;
        }

        public void setMinAmount(BigDecimal minAmount) {
            this.minAmount = minAmount;
        }

        public BigDecimal getMaxAmount() {
            return maxAmount;
        }

        public void setMaxAmount(BigDecimal maxAmount) {
            this.maxAmount = maxAmount;
        }

        public ChargeType getChargeType() {
            return chargeType;
        }

        public void setChargeType(ChargeType chargeType) {
            this.chargeType = chargeType;
        }

        public BigDecimal getChargeValue() {
            return chargeValue;
        }

        public void setChargeValue(BigDecimal chargeValue) {
            this.chargeValue = chargeValue;
        }
    }

    // Constructors
    public PaymentChargeRequestDTO() {}



    // getters & setters...


    public Long getModeId() {
        return modeId;
    }

    public void setModeId(Long modeId) {
        this.modeId = modeId;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public List<SlabDTO> getSlabs() {
        return slabs;
    }

    public void setSlabs(List<SlabDTO> slabs) {
        this.slabs = slabs;
    }
}
