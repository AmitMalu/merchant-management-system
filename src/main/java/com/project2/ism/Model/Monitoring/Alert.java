package com.project2.ism.Model.Monitoring;

import com.project2.ism.Enum.AlertSeverity;
import com.project2.ism.Enum.AlertStatus;
import com.project2.ism.Enum.TransactionSourceType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A flagged transaction (or pattern) raised by the rules engine. This is both
 * the ops work-queue ("here's what needs review") and the compliance
 * evidence trail (who reviewed it, what was decided, when) — regulators care
 * about a demonstrable review process, not just detection.
 */
@Entity
@Table(name = "monitoring_alerts", indexes = {
        @Index(name = "idx_alert_status", columnList = "status"),
        @Index(name = "idx_alert_initiator", columnList = "initiatorType,initiatorId")
})
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ruleId; // nullable — rule may be edited/deleted later

    @Column(length = 150)
    private String ruleName; // denormalized snapshot, stays meaningful even if the rule changes later

    // The id of the specific row this alert is about — a TransactionEvent.id for
    // event-triggered rules (AMOUNT_THRESHOLD/VELOCITY/FAILURE_RATE), or the
    // source transaction's own id (PayoutTransaction.id/BbpsTransaction.id) for
    // STUCK_PENDING, which scans source tables directly rather than the event
    // log. Either way it's what dedup checks key off of.
    private Long transactionEventId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TransactionSourceType sourceType;

    @Column(length = 20)
    private String initiatorType;

    private Long initiatorId;

    // Denormalized business/franchise name, resolved once at alert-creation
    // time so the dashboard reads "TATTHA MITRA KENDRA" instead of "MERCHANT
    // #2" without a lookup on every render. Nullable — falls back to the raw
    // id in the UI if the merchant/franchise was deleted since.
    @Column(length = 200)
    private String initiatorName;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 150)
    private String assignedTo;

    @Column(length = 1000)
    private String resolutionNotes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    public Alert() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Long getTransactionEventId() {
        return transactionEventId;
    }

    public void setTransactionEventId(Long transactionEventId) {
        this.transactionEventId = transactionEventId;
    }

    public TransactionSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(TransactionSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getInitiatorType() {
        return initiatorType;
    }

    public void setInitiatorType(String initiatorType) {
        this.initiatorType = initiatorType;
    }

    public Long getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(Long initiatorId) {
        this.initiatorId = initiatorId;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
