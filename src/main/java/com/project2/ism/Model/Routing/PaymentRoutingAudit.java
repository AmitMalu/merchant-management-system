package com.project2.ism.Model.Routing;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Who changed which routing, and what: every create / edit / delete / assignment is recorded. */
@Entity
@Table(name = "payment_routing_audit",
        indexes = @Index(name = "idx_routing_audit_profile", columnList = "profile_id"))
public class PaymentRoutingAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id")
    private Long profileId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "actor", length = 150)
    private String actor;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
