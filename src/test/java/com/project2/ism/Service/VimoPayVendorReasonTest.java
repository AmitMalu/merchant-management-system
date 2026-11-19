package com.project2.ism.Service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The reason shown for a refused payout is the vendor's own words when it gave any, never an empty message. */
class VimoPayVendorReasonTest {

    @Test
    void theVendorsOwnReasonIsShown() {
        String decrypted = "{\"txnStatus\":\"Failed\",\"txnStatusCode\":\"003\",\"rrn\":null,"
                + "\"responseMessage\":\"Beneficiary Daily Limit exhausted\",\"amount\":150.0}";

        assertEquals("Beneficiary Daily Limit exhausted", VimoPayClientService.vendorReason(decrypted, "Failed"));
    }

    @Test
    void withoutAReason_theOuterMessageIsKept() {
        assertEquals("Failed", VimoPayClientService.vendorReason("{\"txnStatus\":\"Failed\"}", "Failed"));
        assertEquals("Failed", VimoPayClientService.vendorReason("{\"responseMessage\":null}", "Failed"));
        assertEquals("Failed", VimoPayClientService.vendorReason("{\"responseMessage\":\"  \"}", "Failed"));
        assertEquals("Failed", VimoPayClientService.vendorReason("", "Failed"));
        assertEquals("Failed", VimoPayClientService.vendorReason(null, "Failed"));
    }

    @Test
    void unreadableDataNeverThrows() {
        assertEquals("Failed", VimoPayClientService.vendorReason("not json at all {", "Failed"));
    }
}
