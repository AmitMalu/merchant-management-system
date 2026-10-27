package com.project2.ism.Service;

import com.project2.ism.Exception.CreditCardBillException;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The admin's Payout / Credit card switches, for merchants and franchises. */
class FeatureAccessServiceTest {

    private MerchantRepository merchantRepo;
    private FranchiseRepository franchiseRepo;
    private FeatureAccessService access;

    @BeforeEach
    void setUp() {
        merchantRepo = mock(MerchantRepository.class);
        franchiseRepo = mock(FranchiseRepository.class);
        access = new FeatureAccessService(merchantRepo, franchiseRepo);
    }

    private Merchant merchant(Boolean payout, Boolean creditCard) {
        Merchant m = new Merchant();
        m.setPayout(payout);
        m.setCreditCardBillPayment(creditCard);
        return m;
    }

    private Franchise franchise(Boolean payout, Boolean creditCard) {
        Franchise f = new Franchise();
        f.setPayout(payout);
        f.setCreditCardBillPayment(creditCard);
        return f;
    }

    @Test
    void merchant_followsItsSwitches() {
        when(merchantRepo.findById(1L)).thenReturn(Optional.of(merchant(true, false)));
        when(merchantRepo.findById(2L)).thenReturn(Optional.of(merchant(false, true)));

        assertTrue(access.isPayoutEnabled(CustomerRef.merchant(1L)));
        assertFalse(access.isCreditCardEnabled(CustomerRef.merchant(1L)));
        assertFalse(access.isPayoutEnabled(CustomerRef.merchant(2L)));
        assertTrue(access.isCreditCardEnabled(CustomerRef.merchant(2L)));
    }

    @Test
    void franchise_followsItsSwitches() {
        when(franchiseRepo.findById(1L)).thenReturn(Optional.of(franchise(true, true)));
        when(franchiseRepo.findById(2L)).thenReturn(Optional.of(franchise(false, false)));

        assertTrue(access.isPayoutEnabled(CustomerRef.franchise(1L)));
        assertTrue(access.isCreditCardEnabled(CustomerRef.franchise(1L)));
        assertFalse(access.isPayoutEnabled(CustomerRef.franchise(2L)));
        assertFalse(access.isCreditCardEnabled(CustomerRef.franchise(2L)));
    }

    @Test
    void merchantAndFranchiseWithTheSameId_areLookedUpSeparately() {
        when(merchantRepo.findById(5L)).thenReturn(Optional.of(merchant(false, false)));
        when(franchiseRepo.findById(5L)).thenReturn(Optional.of(franchise(true, true)));

        assertFalse(access.isCreditCardEnabled(CustomerRef.merchant(5L)));
        assertTrue(access.isCreditCardEnabled(CustomerRef.franchise(5L)));
    }

    @Test
    void oldFranchise_withNullSwitches_keepsPayout_butNotTheNewCreditCardFeature() {
        // franchises created before the switches existed have NULL in the database
        when(franchiseRepo.findById(9L)).thenReturn(Optional.of(franchise(null, null)));

        assertTrue(access.isPayoutEnabled(CustomerRef.franchise(9L)), "payout must not stop working at deploy");
        assertFalse(access.isCreditCardEnabled(CustomerRef.franchise(9L)));
    }

    @Test
    void unknownAccount_isNeverAllowed() {
        when(merchantRepo.findById(404L)).thenReturn(Optional.empty());
        when(franchiseRepo.findById(404L)).thenReturn(Optional.empty());

        assertFalse(access.isPayoutEnabled(CustomerRef.merchant(404L)));
        assertFalse(access.isCreditCardEnabled(CustomerRef.franchise(404L)));
        assertFalse(access.isPayoutEnabled(null));
        assertFalse(access.isCreditCardEnabled(new CustomerRef("MERCHANT", null)));
    }

    @Test
    void creditCardRefusal_is403_withAClearMessage() {
        when(merchantRepo.findById(1L)).thenReturn(Optional.of(merchant(true, false)));

        CreditCardBillException ex = assertThrows(CreditCardBillException.class,
                () -> access.requireCreditCardEnabled(CustomerRef.merchant(1L)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("not enabled"), ex.getMessage());
        // the front end logs the user out when a 403 message mentions these words
        assertFalse(ex.getMessage().toLowerCase().contains("token"));
        assertFalse(ex.getMessage().toLowerCase().contains("expired"));
    }

    @Test
    void payoutRefusal_isAnIllegalStateException_thatPayoutServiceTurnsIntoAFailedResult() {
        when(franchiseRepo.findById(2L)).thenReturn(Optional.of(franchise(false, true)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> access.requirePayoutEnabled(CustomerRef.franchise(2L)));

        assertTrue(ex.getMessage().contains("Payout is not enabled"), ex.getMessage());
    }

    @Test
    void whenTheSwitchIsOn_nothingIsThrown() {
        when(merchantRepo.findById(1L)).thenReturn(Optional.of(merchant(true, true)));

        assertDoesNotThrow(() -> access.requirePayoutEnabled(CustomerRef.merchant(1L)));
        assertDoesNotThrow(() -> access.requireCreditCardEnabled(CustomerRef.merchant(1L)));
    }
}
