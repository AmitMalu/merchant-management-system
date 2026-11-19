package com.project2.ism.Model.Routing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One real payment in the routing trial run: the vendor routing WOULD have chosen, next to the vendor
 * actually used. Kept only for a few hours (routing.trial-retention-hours), then deleted.
 */
@Entity
@Table(name = "payment_routing_trials",
        indexes = @Index(name = "idx_routing_trial_created", columnList = "created_at"))
public class PaymentRoutingTrial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 150)
    private String productName;

    /** PAYOUT, BILL_AVENUE or SUIVREPAY: which payment path this came through. */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    @Column(name = "account_type", length = 20)
    private String accountType;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "category", length = 150)
    private String category;

    @Column(name = "routing_id")
    private Long routingId;

    @Column(name = "routing_name", length = 100)
    private String routingName;

    @Column(name = "chosen_vendor_id")
    private Long chosenVendorId;

    @Column(name = "chosen_vendor_name", length = 150)
    private String chosenVendorName;

    @Column(name = "used_vendor_id")
    private Long usedVendorId;

    @Column(name = "used_vendor_name", length = 150)
    private String usedVendorName;

    @Column(name = "matched", nullable = false)
    private Boolean matched = false;

    /** The account had no routing for this product (or its routing was off). */
    @Column(name = "no_routing", nullable = false)
    private Boolean noRouting = false;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "reference", length = 100)
    private String reference;

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getRoutingId() { return routingId; }
    public void setRoutingId(Long routingId) { this.routingId = routingId; }
    public String getRoutingName() { return routingName; }
    public void setRoutingName(String routingName) { this.routingName = routingName; }
    public Long getChosenVendorId() { return chosenVendorId; }
    public void setChosenVendorId(Long chosenVendorId) { this.chosenVendorId = chosenVendorId; }
    public String getChosenVendorName() { return chosenVendorName; }
    public void setChosenVendorName(String chosenVendorName) { this.chosenVendorName = chosenVendorName; }
    public Long getUsedVendorId() { return usedVendorId; }
    public void setUsedVendorId(Long usedVendorId) { this.usedVendorId = usedVendorId; }
    public String getUsedVendorName() { return usedVendorName; }
    public void setUsedVendorName(String usedVendorName) { this.usedVendorName = usedVendorName; }
    public Boolean getMatched() { return matched; }
    public void setMatched(Boolean matched) { this.matched = matched; }
    public Boolean getNoRouting() { return noRouting; }
    public void setNoRouting(Boolean noRouting) { this.noRouting = noRouting; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
}
