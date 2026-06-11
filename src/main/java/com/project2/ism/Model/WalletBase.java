package com.project2.ism.Model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class WalletBase {

    @Column(nullable = false, columnDefinition = "DECIMAL(38,2) DEFAULT 0.00")
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "DECIMAL(38,2) DEFAULT 0.00")
    private BigDecimal cutOfAmount = BigDecimal.ZERO;

    @NotNull
    private BigDecimal lastUpdatedAmount;

    @NotNull
    private LocalDateTime lastUpdatedAt;

    @Column(nullable = false, columnDefinition = "DECIMAL(38,2) DEFAULT 0.00")
    private BigDecimal totalCash = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "DECIMAL(38,2) DEFAULT 0.00")
    private BigDecimal usedCash = BigDecimal.ZERO;

    @Version
    @Column(name = "version")
    private Long version;

    public WalletBase() {
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getCutOfAmount() {
        return cutOfAmount;
    }

    public void setCutOfAmount(BigDecimal cutOfAmount) {
        this.cutOfAmount = cutOfAmount;
    }

    public BigDecimal getLastUpdatedAmount() {
        return lastUpdatedAmount;
    }

    public void setLastUpdatedAmount(BigDecimal lastUpdatedAmount) {
        this.lastUpdatedAmount = lastUpdatedAmount;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public BigDecimal getTotalCash() {
        return totalCash;
    }

    public void setTotalCash(BigDecimal totalCash) {
        this.totalCash = totalCash;
    }

    public BigDecimal getUsedCash() {
        return usedCash;
    }

    public void setUsedCash(BigDecimal usedCash) {
        this.usedCash = usedCash;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
