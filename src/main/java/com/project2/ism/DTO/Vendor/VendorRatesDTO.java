package com.project2.ism.DTO.Vendor;

import com.project2.ism.Model.Vendor.VendorCardRates;
import com.project2.ism.Model.Vendor.VendorRates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VendorRatesDTO(
        Long id,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        BigDecimal monthlyRent,
        Long vendorId,
        String vendorName,
        Long productId,
        String productName,
        String productCode,
        String remark,
        List<VendorCardRateDTO> vendorCardRates
) {
    public static VendorRatesDTO fromEntity(VendorRates entity) {
        return new VendorRatesDTO(
                entity.getId(),
                entity.getEffectiveDate(),
                entity.getExpiryDate(),
                entity.getMonthlyRent(),
                entity.getVendor().getId(),
                entity.getVendor().getName(),
                entity.getProduct().getId(),
                entity.getProduct().getProductName(),
                entity.getProduct().getProductCode(),
                entity.getRemark(),
                entity.getVendorCardRates().stream()
                        .map(VendorCardRateDTO::fromEntity)
                        .toList()
        );
    }
}

// Nested record (package-private, no `public` keyword)
record VendorCardRateDTO(
        Long id,
        String cardType,
        BigDecimal rate
) {
    public static VendorCardRateDTO fromEntity(VendorCardRates entity) {
        return new VendorCardRateDTO(
                entity.getId(),
                entity.getCardType(),
                entity.getRate()
        );
    }
}
