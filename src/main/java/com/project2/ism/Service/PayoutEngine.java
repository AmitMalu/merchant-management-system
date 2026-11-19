package com.project2.ism.Service;

import com.project2.ism.DTO.PayoutDTO.PayoutRequest;
import com.project2.ism.DTO.PayoutDTO.PayoutResult;
import com.project2.ism.DTO.PayoutDTO.SimplePayoutRequest;
import org.springframework.stereotype.Service;

/**
 * The one door the payout endpoints use. It picks the old flow or the new one (V2) for each payout, and goes
 * back to the old flow whenever the new one is not available. With the switch on OLD (the default) it calls
 * exactly what the controller called before, in exactly the same way.
 */
@Service
public class PayoutEngine {

    private final PayoutService payoutService;
    private final PayoutEngineSwitch engineSwitch;

    public PayoutEngine(PayoutService payoutService, PayoutEngineSwitch engineSwitch) {
        this.payoutService = payoutService;
        this.engineSwitch = engineSwitch;
    }

    /** A payout with the bank details given in full. */
    public PayoutResult initiate(PayoutRequest request, Long vendorId) {
        if (engineSwitch.mode() != PayoutEngineSwitch.Mode.OLD
                && engineSwitch.useV2(request.getInitiatorType(), request.getInitiatorId())
                && payoutService.v2Available(vendorId)) {
            return payoutService.initiatePayoutV2(request, vendorId);
        }
        return payoutService.initiatePayout(request, vendorId);
    }

    /** A payout to a saved, verified bank account (the merchant screen uses this one). */
    public PayoutResult initiateSimple(SimplePayoutRequest simple, Long vendorId) {
        if (engineSwitch.mode() == PayoutEngineSwitch.Mode.OLD) {
            return payoutService.initiateSimplePayout(simple, vendorId);
        }

        CustomerRef owner = null;
        try {
            owner = payoutService.savedBankOwner(simple.getPayoutBankId());
        } catch (Exception ignored) {
            // the old flow reports a bad bank in its own words
        }
        if (owner == null || !engineSwitch.useV2(owner.type(), owner.id()) || !payoutService.v2Available(vendorId)) {
            return payoutService.initiateSimplePayout(simple, vendorId);
        }

        PayoutRequest request;
        try {
            request = payoutService.toPayoutRequest(simple);
        } catch (Exception e) {
            return PayoutResult.failed(null, e.getMessage());     // same answer as the old flow gives
        }
        return payoutService.initiatePayoutV2(request, vendorId);
    }
}
