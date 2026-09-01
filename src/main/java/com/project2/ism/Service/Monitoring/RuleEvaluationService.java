package com.project2.ism.Service.Monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Enum.AlertSeverity;
import com.project2.ism.Enum.MonitoringRuleType;
import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.Monitoring.MonitoringRule;
import com.project2.ism.Model.Monitoring.TransactionEvent;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Repository.BbpsTransactionRepository;
import com.project2.ism.Repository.MonitoringRuleRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import com.project2.ism.Repository.TransactionEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The rules engine. Two entry points:
 *  - evaluateInstantRules(event): called synchronously right after a
 *    TransactionEvent is written, for rules cheap enough to check per-event
 *    (currently AMOUNT_THRESHOLD).
 *  - evaluateWindowRules() / evaluateStuckPending(): called by the scheduled
 *    sweep, for rules that need to look across a rolling window of events
 *    (VELOCITY, FAILURE_RATE) or scan source tables directly (STUCK_PENDING).
 */
@Service
public class RuleEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RuleEvaluationService.class);

    private final MonitoringRuleRepository monitoringRuleRepository;
    private final TransactionEventRepository transactionEventRepository;
    private final PayoutTransactionRepository payoutTransactionRepository;
    private final BbpsTransactionRepository bbpsTransactionRepository;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;

    public RuleEvaluationService(MonitoringRuleRepository monitoringRuleRepository,
                                  TransactionEventRepository transactionEventRepository,
                                  PayoutTransactionRepository payoutTransactionRepository,
                                  BbpsTransactionRepository bbpsTransactionRepository,
                                  AlertService alertService,
                                  ObjectMapper objectMapper) {
        this.monitoringRuleRepository = monitoringRuleRepository;
        this.transactionEventRepository = transactionEventRepository;
        this.payoutTransactionRepository = payoutTransactionRepository;
        this.bbpsTransactionRepository = bbpsTransactionRepository;
        this.alertService = alertService;
        this.objectMapper = objectMapper;
    }

    // ==================== INSTANT (PER-EVENT) RULES ====================

    public void evaluateInstantRules(TransactionEvent event) {
        List<MonitoringRule> rules = monitoringRuleRepository.findByActiveTrueAndRuleType(MonitoringRuleType.AMOUNT_THRESHOLD);

        for (MonitoringRule rule : rules) {
            if (rule.getSourceType() != null && rule.getSourceType() != event.getSourceType()) {
                continue;
            }
            if (event.getAmount() == null) {
                continue;
            }

            try {
                JsonNode params = objectMapper.readTree(rule.getParameters());
                BigDecimal minAmount = params.path("minAmount").decimalValue();

                if (minAmount != null && event.getAmount().compareTo(minAmount) >= 0) {
                    String message = String.format("%s of %s meets/exceeds threshold of %s",
                            event.getSourceType(), event.getAmount().toPlainString(), minAmount.toPlainString());

                    alertService.raiseAlert(rule.getId(), rule.getName(), event.getId(), event.getSourceType(),
                            event.getInitiatorType(), event.getInitiatorId(), event.getAmount(),
                            rule.getSeverity(), message);
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate AMOUNT_THRESHOLD rule id={}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    // ==================== WINDOW RULES (VELOCITY / FAILURE_RATE) ====================

    public void evaluateWindowRules() {
        evaluateVelocityRules();
        evaluateFailureRateRules();
    }

    private void evaluateVelocityRules() {
        List<MonitoringRule> rules = monitoringRuleRepository.findByActiveTrueAndRuleType(MonitoringRuleType.VELOCITY);
        if (rules.isEmpty()) {
            return;
        }

        for (MonitoringRule rule : rules) {
            try {
                JsonNode params = objectMapper.readTree(rule.getParameters());
                int windowMinutes = params.path("windowMinutes").asInt(60);
                int maxCount = params.path("maxCount").asInt(Integer.MAX_VALUE);
                LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);

                for (Object[] initiator : transactionEventRepository.findDistinctInitiatorsSince(since)) {
                    String initiatorType = (String) initiator[0];
                    Long initiatorId = (Long) initiator[1];

                    List<TransactionEvent> events = transactionEventRepository
                            .findByInitiatorTypeAndInitiatorIdAndOccurredAtAfter(initiatorType, initiatorId, since);

                    List<TransactionEvent> scoped = rule.getSourceType() == null
                            ? events
                            : events.stream().filter(e -> e.getSourceType() == rule.getSourceType()).toList();

                    if (scoped.size() > maxCount) {
                        raiseWindowAlert(rule, initiatorType, initiatorId, scoped, since,
                                String.format("%d transactions from %s #%d in the last %d minute(s) — exceeds %d",
                                        scoped.size(), initiatorType, initiatorId, windowMinutes, maxCount));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate VELOCITY rule id={}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    private void evaluateFailureRateRules() {
        List<MonitoringRule> rules = monitoringRuleRepository.findByActiveTrueAndRuleType(MonitoringRuleType.FAILURE_RATE);
        if (rules.isEmpty()) {
            return;
        }

        for (MonitoringRule rule : rules) {
            try {
                JsonNode params = objectMapper.readTree(rule.getParameters());
                int windowMinutes = params.path("windowMinutes").asInt(60);
                int maxFailures = params.path("maxFailures").asInt(Integer.MAX_VALUE);
                LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);

                for (Object[] initiator : transactionEventRepository.findDistinctInitiatorsSince(since)) {
                    String initiatorType = (String) initiator[0];
                    Long initiatorId = (Long) initiator[1];

                    List<TransactionEvent> failed = transactionEventRepository
                            .findByInitiatorTypeAndInitiatorIdAndStatusAndOccurredAtAfter(
                                    initiatorType, initiatorId, TransactionEventStatus.FAILED, since);

                    List<TransactionEvent> scoped = rule.getSourceType() == null
                            ? failed
                            : failed.stream().filter(e -> e.getSourceType() == rule.getSourceType()).toList();

                    if (scoped.size() > maxFailures) {
                        raiseWindowAlert(rule, initiatorType, initiatorId, scoped, since,
                                String.format("%d failed transactions from %s #%d in the last %d minute(s) — exceeds %d",
                                        scoped.size(), initiatorType, initiatorId, windowMinutes, maxFailures));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate FAILURE_RATE rule id={}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    private void raiseWindowAlert(MonitoringRule rule, String initiatorType, Long initiatorId,
                                   List<TransactionEvent> scopedEvents, LocalDateTime windowStart, String message) {
        // Dedup: skip if an alert for this rule+initiator raised in this same
        // window is still open, so a sustained spike doesn't spam one alert
        // per sweep tick.
        if (alertService.hasRecentOpenAlert(rule.getId(), initiatorType, initiatorId, windowStart)) {
            return;
        }

        TransactionEvent latest = scopedEvents.stream()
                .max((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()))
                .orElse(null);

        BigDecimal totalAmount = scopedEvents.stream()
                .map(TransactionEvent::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        alertService.raiseAlert(rule.getId(), rule.getName(),
                latest != null ? latest.getId() : null,
                latest != null ? latest.getSourceType() : rule.getSourceType(),
                initiatorType, initiatorId, totalAmount, rule.getSeverity(), message);
    }

    // ==================== STUCK-PENDING RULES ====================

    public void evaluateStuckPending() {
        List<MonitoringRule> rules = monitoringRuleRepository.findByActiveTrueAndRuleType(MonitoringRuleType.STUCK_PENDING);

        for (MonitoringRule rule : rules) {
            try {
                JsonNode params = objectMapper.readTree(rule.getParameters());
                int stuckMinutes = params.path("stuckMinutes").asInt(30);
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes(stuckMinutes);

                if (rule.getSourceType() == null || rule.getSourceType() == TransactionSourceType.PAYOUT) {
                    for (PayoutTransaction txn : payoutTransactionRepository
                            .findByStatusAndCreatedAtBefore(PayoutTransaction.PayoutStatus.PENDING, cutoff)) {

                        if (alertService.hasOpenAlertForEvent(rule.getId(), txn.getId())) {
                            continue;
                        }
                        String message = String.format("Payout %s stuck in PENDING since %s",
                                txn.getMerchantRefId(), txn.getCreatedAt());
                        alertService.raiseAlert(rule.getId(), rule.getName(), txn.getId(), TransactionSourceType.PAYOUT,
                                txn.getInitiatorType(), txn.getInitiatorId(), txn.getAmount(), rule.getSeverity(), message);
                    }
                }

                if (rule.getSourceType() == null || rule.getSourceType() == TransactionSourceType.BBPS) {
                    for (BbpsTransaction txn : bbpsTransactionRepository
                            .findByStatusAndCreatedAtBefore(BbpsTransaction.BbpsStatus.PENDING, cutoff)) {

                        if (alertService.hasOpenAlertForEvent(rule.getId(), txn.getId())) {
                            continue;
                        }
                        String message = String.format("BBPS transaction %s stuck in PENDING since %s",
                                txn.getRequestId(), txn.getCreatedAt());
                        alertService.raiseAlert(rule.getId(), rule.getName(), txn.getId(), TransactionSourceType.BBPS,
                                "MERCHANT", txn.getMerchantId(), txn.getAmount(), rule.getSeverity(), message);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate STUCK_PENDING rule id={}: {}", rule.getId(), e.getMessage());
            }
        }
    }
}
