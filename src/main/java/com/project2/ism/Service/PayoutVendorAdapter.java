package com.project2.ism.Service;

import com.project2.ism.DTO.PayoutDTO.PayoutRequest;

import java.math.BigDecimal;

/**
 * What a payout vendor looks like to the payout code. One implementation per vendor: adding a vendor means
 * adding one of these, not changing the payout flow.
 */
public interface PayoutVendorAdapter {

    /** A short code for logs, for example VIMOPAY. */
    String code();

    /** Does this adapter serve the vendor with this id? */
    boolean supports(Long vendorId);

    /** Send the payout. May throw: the caller treats that as an UNKNOWN outcome. */
    AttemptResult submit(Long vendorId, PayoutRequest request, BigDecimal charges);

    /** Can this vendor be asked "what happened to this reference?" (needed before any retry is safe). */
    default boolean supportsStatusCheck() {
        return false;
    }

    /**
     * SUCCESS and FAILED are final answers. PENDING: accepted, the answer comes later. UNKNOWN: nobody knows.
     * NOT_PROCESSED: the vendor clearly did NOT take this try, so it is safe to try again on the same vendor
     * (the routing's retry count). When the tries are used up it is a plain failure and the payout is refunded.
     * An adapter must return it only when it is certain; if there is any doubt it must say UNKNOWN or PENDING.
     */
    enum Outcome {SUCCESS, PENDING, FAILED, UNKNOWN, NOT_PROCESSED}

    record AttemptResult(Outcome outcome, String vendorTxnId, String rrn, String message, String responseCode,
                         BigDecimal vendorCharges) {

        public static AttemptResult unknown(String message) {
            return new AttemptResult(Outcome.UNKNOWN, null, null, message, null, null);
        }
    }
}
