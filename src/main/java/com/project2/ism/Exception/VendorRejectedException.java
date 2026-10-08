package com.project2.ism.Exception;

/**
 * The payment vendor answered, but refused the request outright (non-"000"
 * response code, no encrypted payload) — e.g. "Partner is not active for the
 * service". It's a definite "no", as opposed to a timeout where the outcome is
 * unknown, so callers can treat it as a plain failure and show the vendor's
 * own message instead of crashing on the missing data.
 */
public class VendorRejectedException extends RuntimeException {

    private final String responseCode;
    private final String vendorMessage;

    public VendorRejectedException(String responseCode, String vendorMessage) {
        super("Vendor rejected the request (code " + responseCode + "): " + vendorMessage);
        this.responseCode = responseCode;
        this.vendorMessage = vendorMessage;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public String getVendorMessage() {
        return vendorMessage;
    }
}
