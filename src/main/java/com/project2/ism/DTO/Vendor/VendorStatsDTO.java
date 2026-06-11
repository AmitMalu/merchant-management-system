package com.project2.ism.DTO.Vendor;

import java.math.BigDecimal;
import java.util.Map;

public class VendorStatsDTO {
    public Long totalVendors;
    public Long activeVendors;
    public Long inactiveVendors;
    public Long totalVendorRates;
    public Long activeVendorRates;
    public BigDecimal totalMonthlyRent; // sum of active vendor rents
    public Map<String, Long> cardTypeDistribution; // e.g. {"DEBIT": 12, "CREDIT": 20}
}
