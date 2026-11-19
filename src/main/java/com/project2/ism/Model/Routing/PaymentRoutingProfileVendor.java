package com.project2.ism.Model.Routing;

import com.project2.ism.Model.Payment.PaymentVendor;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * A vendor inside a routing: its place in the order (1 = first, mandatory), whether it is switched on, and
 * its limits. Empty limit = no limit. Limits are per vendor (not per merchant).
 */
@Entity
@Table(name = "payment_routing_profile_vendors",
        indexes = @Index(name = "idx_routing_profile_vendor_profile", columnList = "profile_id"))
public class PaymentRoutingProfileVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private PaymentRoutingProfile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private PaymentVendor vendor;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "daily_transaction_limit")
    private Integer dailyTransactionLimit;

    @Column(name = "daily_amount_limit", precision = 15, scale = 2)
    private BigDecimal dailyAmountLimit;

    // Monthly and yearly limits: the same idea as the daily ones, counted over the calendar month and over the
    // year (see routing.year-start-month). Empty = no limit. They were added later: existing rows have none.
    @Column(name = "monthly_transaction_limit")
    private Integer monthlyTransactionLimit;

    @Column(name = "monthly_amount_limit", precision = 15, scale = 2)
    private BigDecimal monthlyAmountLimit;

    @Column(name = "yearly_transaction_limit")
    private Integer yearlyTransactionLimit;

    @Column(name = "yearly_amount_limit", precision = 15, scale = 2)
    private BigDecimal yearlyAmountLimit;

    public Long getId() { return id; }
    public PaymentRoutingProfile getProfile() { return profile; }
    public void setProfile(PaymentRoutingProfile profile) { this.profile = profile; }
    public PaymentVendor getVendor() { return vendor; }
    public void setVendor(PaymentVendor vendor) { this.vendor = vendor; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
    public Integer getDailyTransactionLimit() { return dailyTransactionLimit; }
    public void setDailyTransactionLimit(Integer dailyTransactionLimit) { this.dailyTransactionLimit = dailyTransactionLimit; }
    public BigDecimal getDailyAmountLimit() { return dailyAmountLimit; }
    public void setDailyAmountLimit(BigDecimal dailyAmountLimit) { this.dailyAmountLimit = dailyAmountLimit; }
    public Integer getMonthlyTransactionLimit() { return monthlyTransactionLimit; }
    public void setMonthlyTransactionLimit(Integer monthlyTransactionLimit) { this.monthlyTransactionLimit = monthlyTransactionLimit; }
    public BigDecimal getMonthlyAmountLimit() { return monthlyAmountLimit; }
    public void setMonthlyAmountLimit(BigDecimal monthlyAmountLimit) { this.monthlyAmountLimit = monthlyAmountLimit; }
    public Integer getYearlyTransactionLimit() { return yearlyTransactionLimit; }
    public void setYearlyTransactionLimit(Integer yearlyTransactionLimit) { this.yearlyTransactionLimit = yearlyTransactionLimit; }
    public BigDecimal getYearlyAmountLimit() { return yearlyAmountLimit; }
    public void setYearlyAmountLimit(BigDecimal yearlyAmountLimit) { this.yearlyAmountLimit = yearlyAmountLimit; }
}
