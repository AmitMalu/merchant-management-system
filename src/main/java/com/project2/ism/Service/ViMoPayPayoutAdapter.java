package com.project2.ism.Service;

import com.project2.ism.DTO.PayoutDTO.PayoutRequest;
import com.project2.ism.DTO.PayoutDTO.PayoutResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * ViMoPay, the payout vendor in use today. It only wraps the existing client: the request, the encryption and the
 * answer handling are exactly the old code (VimoPayClientService.submitPayout).
 *
 * Carried over on purpose: VimoPayClientService turns an HTTP or submission error into a FAILED answer, and the
 * payout flow refunds on FAILED, as it always has. Telling a clear failure from an unclear one is Phase 4, which
 * needs ViMoPay's error codes.
 */
@Component
public class ViMoPayPayoutAdapter implements PayoutVendorAdapter {

    private final VimoPayClientService client;

    @Value("${payout.default.vendor.id}")
    private Long vendorId;

    public ViMoPayPayoutAdapter(VimoPayClientService client) {
        this.client = client;
    }

    @Override
    public String code() {
        return "VIMOPAY";
    }

    @Override
    public boolean supports(Long id) {
        return id != null && id.equals(vendorId);
    }

    @Override
    public AttemptResult submit(Long id, PayoutRequest request, BigDecimal charges) {
        PayoutResult r = client.submitPayout(id, request, charges);
        if (r == null || r.getStatus() == null) {
            return AttemptResult.unknown("No answer from the vendor");
        }
        Outcome outcome = switch (r.getStatus().toUpperCase()) {
            case "OK" -> Outcome.SUCCESS;
            case "PENDING" -> Outcome.PENDING;
            case "FAILED" -> Outcome.FAILED;
            default -> Outcome.UNKNOWN;
        };
        return new AttemptResult(outcome, r.getTxnId(), r.getRrn(), r.getMessage(), null, r.getVendorCharges());
    }
}
