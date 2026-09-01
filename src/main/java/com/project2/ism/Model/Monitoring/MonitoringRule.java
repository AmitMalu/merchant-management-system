package com.project2.ism.Model.Monitoring;

import com.project2.ism.Enum.AlertSeverity;
import com.project2.ism.Enum.MonitoringRuleType;
import com.project2.ism.Enum.TransactionSourceType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A DB-configurable monitoring rule — deliberately not hardcoded, since fraud
 * thresholds and patterns need to be tunable by ops without a redeploy.
 * `parameters` is a small JSON blob whose shape depends on `ruleType`:
 *   AMOUNT_THRESHOLD -> {"minAmount": 100000}
 *   VELOCITY         -> {"windowMinutes": 60, "maxCount": 5}
 *   FAILURE_RATE      -> {"windowMinutes": 60, "maxFailures": 3}
 *   STUCK_PENDING     -> {"stuckMinutes": 30}
 */
@Entity
@Table(name = "monitoring_rules")
public class MonitoringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MonitoringRuleType ruleType;

    // Null = applies across every source type
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TransactionSourceType sourceType;

    @Lob
    @Column(nullable = false)
    private String parameters; // JSON

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public MonitoringRule() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MonitoringRuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(MonitoringRuleType ruleType) {
        this.ruleType = ruleType;
    }

    public TransactionSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(TransactionSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
