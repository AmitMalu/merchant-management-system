package com.project2.ism.Model.Monitoring;

import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Append-only, normalized log of every money-movement status change across
 * all payment rails (Payout, BBPS, Settlement, Wallet Adjustment, ...).
 * This is the single feed the Transaction Monitoring rules engine and
 * dashboard read from, so a new payment rail added later only needs one more
 * write call here, not a bespoke monitoring integration.
 *
 * No JPA relations on purpose (plain Long ids only) — mirrors the existing
 * PayoutTransaction/BbpsTransaction convention of loosely-coupled references,
 * and avoids adding new FK constraints on a live production schema.
 */
@Entity
@Table(name = "transaction_events", indexes = {
        @Index(name = "idx_txn_event_source", columnList = "sourceType,sourceId"),
        @Index(name = "idx_txn_event_initiator", columnList = "initiatorType,initiatorId,occurredAt"),
        @Index(name = "idx_txn_event_occurred_at", columnList = "occurredAt")
})
public class TransactionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionSourceType sourceType;

    // Primary key of the originating row (PayoutTransaction.id, BbpsTransaction.id,
    // MerchantTransactionDetails.transactionId, ...) — not a JPA relation.
    @Column(nullable = false)
    private Long sourceId;

    @Column(length = 20)
    private String initiatorType; // MERCHANT | FRANCHISE

    private Long initiatorId;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    // Card network (Visa/Mastercard/RuPay/Amex/...), populated only for
    // SETTLEMENT/COMMISSION events that originated from a card-present
    // transaction (VendorTransactions.brandType). Null for every other
    // source type (Payout, BBPS, wallet adjustment have no card involved).
    @Column(length = 50)
    private String cardBrand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionEventStatus status;

    @Column(length = 500)
    private String metadata; // free-form context, e.g. "paymentMode=NEFT;vendorTxnId=..."

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public TransactionEvent() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(TransactionSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public TransactionEventStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionEventStatus status) {
        this.status = status;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
