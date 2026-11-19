package com.project2.ism.Model.Routing;

import com.project2.ism.Model.Payment.PaymentProduct;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One routing for a product: its vendors in priority order, with limits and retry settings. Merchants and
 * franchises are assigned to a routing (PaymentRoutingAssignment). A product can have several routings, and
 * an account is assigned to at most one routing per product.
 */
@Entity
@Table(name = "payment_routing_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_routing_profile_name", columnNames = {"product_id", "name"}))
public class PaymentRoutingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private PaymentProduct product;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "status", nullable = false)
    private Boolean status = true;

    /** Retries on the same vendor before moving to the next one. */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 3;

    /** Seconds to wait between attempts. */
    @Column(name = "retry_wait_seconds", nullable = false)
    private Integer retryWaitSeconds = 3;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC")
    private List<PaymentRoutingProfileVendor> vendors = new ArrayList<>();

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addVendor(PaymentRoutingProfileVendor v) {
        vendors.add(v);
        v.setProfile(this);
    }

    public Long getId() { return id; }
    public PaymentProduct getProduct() { return product; }
    public void setProduct(PaymentProduct product) { this.product = product; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getRetryWaitSeconds() { return retryWaitSeconds; }
    public void setRetryWaitSeconds(Integer retryWaitSeconds) { this.retryWaitSeconds = retryWaitSeconds; }
    public List<PaymentRoutingProfileVendor> getVendors() { return vendors; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
