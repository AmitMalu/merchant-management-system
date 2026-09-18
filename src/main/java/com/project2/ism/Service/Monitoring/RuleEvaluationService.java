package com.project2.ism.Service.Monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Enum.AlertSeverity;
import com.project2.ism.Enum.MonitoringRuleType;
import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Model.Monitoring.MonitoringRule;
import com.project2.ism.Model.Monitoring.TransactionEvent;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Model.VendorTransactions;
import com.project2.ism.Repository.BbpsTransactionRepository;
import com.project2.ism.Repository.MonitoringRuleRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import com.project2.ism.Repository.ProductSerialsRepository;
import com.project2.ism.Repository.TransactionEventRepository;
import com.project2.ism.Repository.VendorTransactionsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final VendorTransactionsRepository vendorTransactionsRepository;
    private final ProductSerialsRepository productSerialsRepository;
    private final AlertService alertService;
    private final ObjectMapper objectMapper;

    public RuleEvaluationService(MonitoringRuleRepository monitoringRuleRepository,
                                  TransactionEventRepository transactionEventRepository,
                                  PayoutTransactionRepository payoutTransactionRepository,
                                  BbpsTransactionRepository bbpsTransactionRepository,
                                  VendorTransactionsRepository vendorTransactionsRepository,
                                  ProductSerialsRepository productSerialsRepository,
                                  AlertService alertService,
                                  ObjectMapper objectMapper) {
        this.monitoringRuleRepository = monitoringRuleRepository;
        this.transactionEventRepository = transactionEventRepository;
        this.payoutTransactionRepository = payoutTransactionRepository;
        this.bbpsTransactionRepository = bbpsTransactionRepository;
        this.vendorTransactionsRepository = vendorTransactionsRepository;
        this.productSerialsRepository = productSerialsRepository;
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
        evaluateCardVelocityRules();
        evaluateTidVolumeCapRules();
        evaluateRepeatedAmountPatternRules();
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

    // ==================== CARD VELOCITY (Risk SOP Rule 1) ====================
    // Alert-only: flags a card (BIN + last 4 digits) used more than
    // parameters.maxCount times across vendor_transactions within
    // parameters.windowMinutes (default 24h/2, matching the SOP). Does not
    // touch the payout/settlement release path — Risk reviews the alert and
    // decides manually whether to hold anything, same as every other rule
    // type here.
    private void evaluateCardVelocityRules() {
        List<MonitoringRule> rules = monitoringRuleRepository.findByActiveTrueAndRuleType(MonitoringRuleType.CARD_VELOCITY);
        if (rules.isEmpty()) {
            return;
        }

        for (MonitoringRule rule : rules) {
            try {
                JsonNode params = objectMapper.readTree(rule.getParameters());
                int windowMinutes = params.path("windowMinutes").asInt(1440);
                int maxCount = params.path("maxCount").asInt(2);
                LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);

                List<VendorTransactions> txns = vendorTransactionsRepository.findByDateBetween(since, LocalDateTime.now());

                // Group by card identity (BIN + last 4) — rows missing either
                // can't be attributed to a specific card, so they're skipped.
                Map<String, List<VendorTransactions>> byCard = new HashMap<>();
                for (VendorTransactions vt : txns) {
                    String bin = vt.getPaymentCardBin();
                    String last4 = vt.getCardLastFourDigit();
                    if (bin == null || bin.isBlank() || last4 == null || last4.isBlank()) {
                        continue;
                    }
                    byCard.computeIfAbsent(bin + ":" + last4, k -> new ArrayList<>()).add(vt);
                }

                for (Map.Entry<String, List<VendorTransactions>> entry : byCard.entrySet()) {
                    List<VendorTransactions> cardTxns = entry.getValue();
                    if (cardTxns.size() <= maxCount) {
                        continue;
                    }

                    // Oldest-first, so the 1st/2nd swipe of the day settle
                    // normally and only the 3rd+ (SOP wording) get held.
                    cardTxns.sort((a, b) -> a.getDate().compareTo(b.getDate()));
                    List<VendorTransactions> overLimit = cardTxns.subList(maxCount, cardTxns.size());

                    // Hold every over-limit transaction that hasn't already
                    // settled — idempotent, so re-running this every sweep is
                    // safe. A transaction that already settled before this
                    // rule caught it can't be un-credited here; that's a
                    // separate recovery process, out of scope for this
                    // alert-time check. If another rule (e.g. TID_VOLUME_CAP)
                    // already holds this transaction, its original reason is
                    // left alone rather than overwritten — the hold itself is
                    // what matters to settlement, not which rule said it first.
                    String reason = String.format("CARD_VELOCITY: card BIN %s ending %s exceeded %d/day",
                            entry.getKey().split(":")[0], entry.getKey().split(":")[1], maxCount);
                    for (VendorTransactions vt : overLimit) {
                        if (Boolean.TRUE.equals(vt.getSettled()) || Boolean.TRUE.equals(vt.getRiskHold())) {
                            continue;
                        }
                        vt.setRiskHold(Boolean.TRUE);
                        vt.setRiskHoldReason(reason);
                        vendorTransactionsRepository.save(vt);
                    }

                    VendorTransactions latest = cardTxns.get(cardTxns.size() - 1);
                    Merchant merchant = resolveMerchantForVendorTxn(latest);
                    Long merchantId = merchant != null ? merchant.getId() : null;

                    // Dedup: skip raising ANOTHER alert if one for this
                    // rule+merchant is still open in this window — the hold
                    // above still gets (re)applied every sweep regardless, so
                    // a newly-arriving over-limit transaction is never missed
                    // just because we already alerted once.
                    if (merchantId != null && alertService.hasRecentOpenAlert(rule.getId(), "MERCHANT", merchantId, since)) {
                        continue;
                    }

                    BigDecimal totalAmount = cardTxns.stream()
                            .map(VendorTransactions::getAmount)
                            .filter(a -> a != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    String message = String.format(
                            "Card BIN %s ending %s used %d times in the last %d minute(s) — exceeds %d/day limit. " +
                                    "%d transaction(s) held from settlement pending review (possible card testing/cloning).",
                            latest.getPaymentCardBin(), latest.getCardLastFourDigit(), cardTxns.size(), windowMinutes,
                            maxCount, overLimit.size());

                    alertService.raiseAlert(rule.getId(), rule.getName(), latest.getInternalId(),
                            TransactionSourceType.CARD_TRANSACTION, "MERCHANT", merchantId, totalAmount,
                            rule.getSeverity(), message);
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate CARD_VELOCITY rule id={}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    // ==================== TID VOLUME CAP (Risk SOP Rule 2) ====================
    // Alert-only for the alert, but same as CARD_VELOCITY: also holds the
    // over-limit transactions from settlement. Uses a calendar day (midnight
    // to midnight), not a rolling window, per the SOP's "aggregate daily
    // gross volumes" wording — deliberately different from CARD_VELOCITY's
    // rolling 24h, which matches that rule's own "within 24 hours" wording.
    private void evaluateTidVolumeCapRules() {
        List<MonitoringRule> rules = monitoringRuleRepository.findByActiveTrueAndRuleType(MonitoringRuleType.TID_VOLUME_CAP);
        if (rules.isEmpty()) {
            return;
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        List<VendorTransactions> todaysTxns = vendorTransactionsRepository.findByDateBetween(startOfDay, endOfDay);

        // Group by TID once per sweep — shared across every active
        // TID_VOLUME_CAP rule, since the underlying data is the same.
        Map<String, List<VendorTransactions>> byTid = new HashMap<>();
        for (VendorTransactions vt : todaysTxns) {
            String tid = vt.getTid();
            if (tid == null || tid.isBlank()) {
                continue;
            }
            byTid.computeIfAbsent(tid, k -> new ArrayList<>()).add(vt);
        }

        for (MonitoringRule rule : rules) {
            try {
                JsonNode params = objectMapper.readTree(rule.getParameters());
                BigDecimal maxDailyAmount = params.path("maxDailyAmount").decimalValue();
                if (maxDailyAmount == null || maxDailyAmount.signum() <= 0) {
                    continue;
                }

                for (Map.Entry<String, List<VendorTransactions>> entry : byTid.entrySet()) {
                    List<VendorTransactions> tidTxns = entry.getValue();

                    // Oldest-first running total — find the first transaction
                    // that pushes the terminal's day over the cap; that one
                    // and everything after it (same terminal, same day) are
                    // "the excess" the SOP asks to pause.
                    tidTxns.sort((a, b) -> a.getDate().compareTo(b.getDate()));
                    BigDecimal running = BigDecimal.ZERO;
                    int breachIndex = -1;
                    for (int i = 0; i < tidTxns.size(); i++) {
                        BigDecimal amt = tidTxns.get(i).getAmount();
                        running = running.add(amt != null ? amt : BigDecimal.ZERO);
                        if (running.compareTo(maxDailyAmount) > 0) {
                            breachIndex = i;
                            break;
                        }
                    }
                    if (breachIndex == -1) {
                        continue;
                    }

                    List<VendorTransactions> overLimit = tidTxns.subList(breachIndex, tidTxns.size());

                    String reason = String.format("TID_VOLUME_CAP: terminal %s exceeded %s/day", entry.getKey(), maxDailyAmount.toPlainString());
                    for (VendorTransactions vt : overLimit) {
                        if (Boolean.TRUE.equals(vt.getSettled()) || Boolean.TRUE.equals(vt.getRiskHold())) {
                            continue;
                        }
                        vt.setRiskHold(Boolean.TRUE);
                        vt.setRiskHoldReason(reason);
                        vendorTransactionsRepository.save(vt);
                    }

                    VendorTransactions latest = tidTxns.get(tidTxns.size() - 1);
                    Merchant merchant = resolveMerchantForVendorTxn(latest);
                    Long merchantId = merchant != null ? merchant.getId() : null;

                    if (merchantId != null && alertService.hasRecentOpenAlert(rule.getId(), "MERCHANT", merchantId, startOfDay)) {
                        continue;
                    }

                    String message = String.format(
                            "Terminal %s processed %s today — exceeds the %s/day cap. " +
                                    "%d transaction(s) held from settlement pending EDD/KYC review.",
                            entry.getKey(), running.toPlainString(), maxDailyAmount.toPlainString(), overLimit.size());

                    alertService.raiseAlert(rule.getId(), rule.getName(), latest.getInternalId(),
                            TransactionSourceType.CARD_TRANSACTION, "MERCHANT", merchantId, running,
                            rule.getSeverity(), message);
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate TID_VOLUME_CAP rule id={}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    // ==================== REPEATED AMOUNT PATTERN (Risk SOP Rule 3) ====================
    // Purely informational — never touches riskHold, never affects
    // settlement. Rule 3 in the SOP is a reconciliation-discipline rule
    // ("don't match entries by amount alone, use the unique txn ID/RRN and
    // timestamp instead"), not a fraud-blocking rule like Rules 1/2 — round
    // amounts (₹99, ₹999, subscription fees, etc.) are common and legitimate,
    // so this only ever nudges the reconciliation team to look closer, it
    // never holds money.
    private void evaluateRepeatedAmountPatternRules() {
        List<MonitoringRule> rules = monitoringRuleRepository.findByActiveTrueAndRuleType(MonitoringRuleType.REPEATED_AMOUNT_PATTERN);
        if (rules.isEmpty()) {
            return;
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        List<VendorTransactions> todaysTxns = vendorTransactionsRepository.findByDateBetween(startOfDay, endOfDay);

        // Group by merchant (MID) + exact amount — the same amount repeating
        // for different merchants is not meaningful, but repeating for the
        // same merchant is what the SOP is warning about.
        Map<String, List<VendorTransactions>> byMerchantAndAmount = new HashMap<>();
        for (VendorTransactions vt : todaysTxns) {
            String mid = vt.getMid();
            BigDecimal amount = vt.getAmount();
            if (mid == null || mid.isBlank() || amount == null) {
                continue;
            }
            byMerchantAndAmount.computeIfAbsent(mid + ":" + amount.toPlainString(), k -> new ArrayList<>()).add(vt);
        }

        for (MonitoringRule rule : rules) {
            try {
                JsonNode params = objectMapper.readTree(rule.getParameters());
                int minCount = params.path("minCount").asInt(3);

                for (Map.Entry<String, List<VendorTransactions>> entry : byMerchantAndAmount.entrySet()) {
                    List<VendorTransactions> group = entry.getValue();
                    if (group.size() < minCount) {
                        continue;
                    }

                    VendorTransactions latest = group.stream()
                            .max((a, b) -> a.getDate().compareTo(b.getDate()))
                            .orElse(null);
                    if (latest == null) {
                        continue;
                    }

                    Merchant merchant = resolveMerchantForVendorTxn(latest);
                    Long merchantId = merchant != null ? merchant.getId() : null;

                    if (merchantId != null && alertService.hasRecentOpenAlert(rule.getId(), "MERCHANT", merchantId, startOfDay)) {
                        continue;
                    }

                    String message = String.format(
                            "%d transactions of exactly ₹%s today for this merchant — during reconciliation, " +
                                    "cross-check by transaction ID/RRN and timestamp, not amount alone.",
                            group.size(), latest.getAmount().toPlainString());

                    alertService.raiseAlert(rule.getId(), rule.getName(), latest.getInternalId(),
                            TransactionSourceType.CARD_TRANSACTION, "MERCHANT", merchantId, latest.getAmount(),
                            rule.getSeverity(), message);
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate REPEATED_AMOUNT_PATTERN rule id={}: {}", rule.getId(), e.getMessage());
            }
        }
    }

    // Same MID/TID → device → merchant resolution order already used by
    // EnhancedSettlementService2, so a flagged card lines up with the same
    // merchant settlement would attribute the transaction to.
    private Merchant resolveMerchantForVendorTxn(VendorTransactions vt) {
        if (vt.getMid() != null && vt.getTid() != null) {
            List<ProductSerialNumbers> devices = productSerialsRepository.findByMidAndTid(vt.getMid(), vt.getTid());
            if (!devices.isEmpty()) {
                return devices.get(0).getMerchant();
            }
        }
        if (vt.getMid() != null) {
            Optional<ProductSerialNumbers> device = productSerialsRepository.findByMid(vt.getMid());
            if (device.isPresent()) {
                return device.get().getMerchant();
            }
        }
        if (vt.getTid() != null) {
            Optional<ProductSerialNumbers> device = productSerialsRepository.findByTid(vt.getTid());
            if (device.isPresent()) {
                return device.get().getMerchant();
            }
        }
        return null;
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
