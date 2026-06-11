package com.project2.ism.DTO;

import java.math.BigDecimal;
import java.util.Map;

public class FranchiseMerchantStatsDTO {
    public Long totalFranchises;
    public Long totalMerchants;
    public Long totalDirectMerchants;
    public Long totalFranchiseMerchants;

    public Map<String, Long> merchantsPerFranchise;   // franchiseId -> merchant count
    public BigDecimal totalFranchiseWalletBalance;
    public BigDecimal totalDirectMerchantWalletBalance;
    public BigDecimal totalFranchiseMerchantWalletBalance;
}
