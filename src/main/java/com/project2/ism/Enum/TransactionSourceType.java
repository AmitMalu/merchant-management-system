package com.project2.ism.Enum;

/**
 * Discriminates which money-movement flow a TransactionEvent / MonitoringRule
 * originated from. Mirrors the "service" strings already used ad hoc across
 * PayoutService, BbpsPaymentService, EnhancedSettlementService2 and
 * WalletAdjustmentService, but as a real enum so monitoring can filter/group
 * reliably.
 */
public enum TransactionSourceType {
    PAYOUT,
    PAYOUT_REFUND,
    BBPS,
    BBPS_REFUND,
    SETTLEMENT,
    COMMISSION,
    WALLET_ADJUSTMENT
}
