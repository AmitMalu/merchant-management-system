package com.project2.ism.Enum;

/**
 * AMOUNT_THRESHOLD  - single transaction amount >= parameters.minAmount
 * VELOCITY          - more than parameters.maxCount events from the same
 *                      initiator within parameters.windowMinutes
 * STUCK_PENDING     - a PAYOUT/BBPS transaction still PENDING after
 *                      parameters.stuckMinutes
 * FAILURE_RATE      - more than parameters.maxFailures failed events from the
 *                      same initiator within parameters.windowMinutes
 */
public enum MonitoringRuleType {
    AMOUNT_THRESHOLD,
    VELOCITY,
    STUCK_PENDING,
    FAILURE_RATE
}
