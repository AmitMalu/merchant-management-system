package com.project2.ism.Service;

import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The credit card report's vendor filter: only BILLAVENUE, SUIVREPAY or all. Anything else is
 * refused before any query runs.
 */
class CreditCardReportVendorTest {

    private final CreditCardReportService service =
            new CreditCardReportService(mock(MerchantRepository.class), mock(FranchiseRepository.class));

    @Test
    void anUnknownVendor_isRefused() {
        CreditCardReportService.Viewer admin = new CreditCardReportService.Viewer(true, null);
        CreditCardReportService.Filters filters =
                new CreditCardReportService.Filters(null, null, null, null, null, null, "PAYTM");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.getReport(admin, filters, 0, 10));
        assertTrue(ex.getMessage().contains("Invalid vendor"), ex.getMessage());
    }
}
