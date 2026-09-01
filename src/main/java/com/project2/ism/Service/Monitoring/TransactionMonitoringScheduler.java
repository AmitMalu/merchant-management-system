package com.project2.ism.Service.Monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic sweep for the rule types that can't be evaluated per-event:
 * VELOCITY/FAILURE_RATE (need a rolling window of events) and STUCK_PENDING
 * (needs to notice the *absence* of a follow-up event, not react to one).
 * Runs every 2 minutes — cheap at current transaction volume; if volume grows
 * enough for this to matter, the window queries are the place to add caching
 * (e.g. Redis counters) without touching the rules engine's public shape.
 */
@Component
public class TransactionMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransactionMonitoringScheduler.class);

    private final RuleEvaluationService ruleEvaluationService;

    public TransactionMonitoringScheduler(RuleEvaluationService ruleEvaluationService) {
        this.ruleEvaluationService = ruleEvaluationService;
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    public void sweep() {
        try {
            ruleEvaluationService.evaluateWindowRules();
            ruleEvaluationService.evaluateStuckPending();
        } catch (Exception e) {
            log.error("Transaction monitoring sweep failed: {}", e.getMessage(), e);
        }
    }
}
