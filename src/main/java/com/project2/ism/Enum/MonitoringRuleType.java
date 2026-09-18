package com.project2.ism.Enum;

/**
 * AMOUNT_THRESHOLD  - single transaction amount >= parameters.minAmount
 * VELOCITY          - more than parameters.maxCount events from the same
 *                      initiator within parameters.windowMinutes
 * STUCK_PENDING     - a PAYOUT/BBPS transaction still PENDING after
 *                      parameters.stuckMinutes
 * FAILURE_RATE      - more than parameters.maxFailures failed events from the
 *                      same initiator within parameters.windowMinutes
 * CARD_VELOCITY     - more than parameters.maxCount POS/card transactions on
 *                      the same card (BIN + last 4 digits) within
 *                      parameters.windowMinutes, across the vendor_transactions
 *                      feed — per Risk SOP Rule 1 (card velocity).
 * TID_VOLUME_CAP    - a single Terminal ID's (TID) transactions total more
 *                      than parameters.maxDailyAmount on a calendar day —
 *                      per Risk SOP Rule 2 (terminal volume cap).
 * REPEATED_AMOUNT_PATTERN - a merchant has parameters.minCount or more
 *                      transactions of the exact same amount on a calendar
 *                      day — informational only, never holds anything; a
 *                      nudge for reconciliation to key off transaction
 *                      ID/RRN + timestamp, not amount — per Risk SOP Rule 3.
 */
public enum MonitoringRuleType {
    AMOUNT_THRESHOLD,
    VELOCITY,
    STUCK_PENDING,
    FAILURE_RATE,
    CARD_VELOCITY,
    TID_VOLUME_CAP,
    REPEATED_AMOUNT_PATTERN
}
