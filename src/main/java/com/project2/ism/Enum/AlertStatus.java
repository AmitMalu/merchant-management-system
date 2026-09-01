package com.project2.ism.Enum;

public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    FALSE_POSITIVE,
    // Closed automatically by the system — e.g. a STUCK_PENDING alert whose
    // transaction later got a final SUCCESS/FAILED status via a late vendor
    // callback. Deliberately distinct from RESOLVED so the audit trail can
    // still show which closures were a human decision vs. the system noticing
    // the underlying condition no longer holds.
    AUTO_RESOLVED
}
