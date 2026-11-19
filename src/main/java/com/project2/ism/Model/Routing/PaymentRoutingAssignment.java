package com.project2.ism.Model.Routing;

import com.project2.ism.Model.Payment.PaymentProduct;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A merchant or franchise assigned to a routing. An account has at most one routing per product, and sees
 * the product only when it has one (when the menu rule is switched on).
 */
@Entity
@Table(name = "payment_routing_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uk_routing_assignment",
                columnNames = {"product_id", "target_type", "target_id"}),
        indexes = @Index(name = "idx_routing_assignment_profile", columnList = "profile_id"))
public class PaymentRoutingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private PaymentRoutingProfile profile;

    /** Kept here too so "one routing per account per product" is enforced by the database. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private PaymentProduct product;

    /** MERCHANT or FRANCHISE (their ids overlap, so the type is part of the key). */
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public PaymentRoutingProfile getProfile() { return profile; }
    public void setProfile(PaymentRoutingProfile profile) { this.profile = profile; }
    public PaymentProduct getProduct() { return product; }
    public void setProduct(PaymentProduct product) { this.product = product; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
