// DTO for Franchise Stats
package com.project2.ism.DTO;

import java.math.BigDecimal;
import java.util.List;

public class FranchiseStatsDTO {
    private Long franchiseId;
    private Long merchantCount;
    private Long outwardTransactions;
    private Long returnTransactions;
    private BigDecimal walletBalance;
    private List<FranchiseProductSummaryDTO> products;

    public FranchiseStatsDTO(Long franchiseId, Long merchantCount, Long outwardTransactions,
                             BigDecimal walletBalance,
                             List<FranchiseProductSummaryDTO> products) {
        this.franchiseId = franchiseId;
        this.merchantCount = merchantCount;
        this.outwardTransactions = outwardTransactions;
        //this.returnTransactions = returnTransactions;
        this.walletBalance = walletBalance;
        this.products = products;
    }

    // getters & setters

    public Long getFranchiseId() {
        return franchiseId;
    }

    public void setFranchiseId(Long franchiseId) {
        this.franchiseId = franchiseId;
    }

    public Long getMerchantCount() {
        return merchantCount;
    }

    public void setMerchantCount(Long merchantCount) {
        this.merchantCount = merchantCount;
    }

    public Long getOutwardTransactions() {
        return outwardTransactions;
    }

    public void setOutwardTransactions(Long outwardTransactions) {
        this.outwardTransactions = outwardTransactions;
    }

    public Long getReturnTransactions() {
        return returnTransactions;
    }

    public void setReturnTransactions(Long returnTransactions) {
        this.returnTransactions = returnTransactions;
    }

    public BigDecimal getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(BigDecimal walletBalance) {
        this.walletBalance = walletBalance;
    }

    public List<FranchiseProductSummaryDTO> getProducts() {
        return products;
    }

    public void setProducts(List<FranchiseProductSummaryDTO> products) {
        this.products = products;
    }
}
