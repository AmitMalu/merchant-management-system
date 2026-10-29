package com.project2.ism.Service;

import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationCallback;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseOrMerchantNotificationCallbackRepository;
import com.project2.ism.Repository.ProductSerialNumbersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Which URL a payment notification goes to. Direct merchants and franchise devices keep behaving
 * as before; a franchise merchant uses its own URL when it has one, else the franchise's.
 */
class NotificationCallbackResolverTest {

    private static final String MID = "MID123";

    private ProductSerialNumbersRepository psnRepo;
    private FranchiseOrMerchantNotificationCallbackRepository callbackRepo;
    private NotificationCallbackResolver resolver;

    @BeforeEach
    void setUp() {
        psnRepo = mock(ProductSerialNumbersRepository.class);
        callbackRepo = mock(FranchiseOrMerchantNotificationCallbackRepository.class);
        resolver = new NotificationCallbackResolver(psnRepo, callbackRepo);
        when(callbackRepo.findByMerchant_Id(anyLong())).thenReturn(Optional.empty());
        when(callbackRepo.findByFranchise_Id(anyLong())).thenReturn(Optional.empty());
    }

    private Franchise franchise(long id) {
        Franchise f = new Franchise();
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }

    private Merchant merchant(long id) {
        Merchant m = new Merchant();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private void device(Franchise f, Merchant m) {
        ProductSerialNumbers psn = new ProductSerialNumbers();
        psn.setFranchise(f);
        psn.setMerchant(m);
        when(psnRepo.findByMid(MID)).thenReturn(Optional.of(psn));
    }

    private FranchiseOrMerchantNotificationCallback url(String callback) {
        FranchiseOrMerchantNotificationCallback c = new FranchiseOrMerchantNotificationCallback();
        c.setCallback(callback);
        return c;
    }

    @Test
    void unknownDevice_hasNoCallback() {
        when(psnRepo.findByMid(MID)).thenReturn(Optional.empty());

        assertNull(resolver.resolveForMid(MID));
        verifyNoInteractions(callbackRepo);
    }

    @Test
    void deviceWithNobody_hasNoCallback() {
        device(null, null);

        assertNull(resolver.resolveForMid(MID));
    }

    // ------------------------------------------------------------------ direct merchant (unchanged)

    @Test
    void directMerchant_usesItsOwnUrl() {
        device(null, merchant(7));
        when(callbackRepo.findByMerchant_Id(7L)).thenReturn(Optional.of(url("https://direct.example/notify")));

        assertEquals("https://direct.example/notify", resolver.resolveForMid(MID).getCallback());
        verify(callbackRepo, never()).findByFranchise_Id(anyLong());
    }

    @Test
    void directMerchantWithoutUrl_hasNoCallback() {
        device(null, merchant(7));

        assertNull(resolver.resolveForMid(MID));
    }

    // ------------------------------------------------------------------ franchise's own device (unchanged)

    @Test
    void franchiseDevice_usesTheFranchiseUrl() {
        device(franchise(3), null);
        when(callbackRepo.findByFranchise_Id(3L)).thenReturn(Optional.of(url("https://franchise.example/notify")));

        assertEquals("https://franchise.example/notify", resolver.resolveForMid(MID).getCallback());
        verify(callbackRepo, never()).findByMerchant_Id(anyLong());
    }

    @Test
    void franchiseDeviceWithoutUrl_hasNoCallback() {
        device(franchise(3), null);

        assertNull(resolver.resolveForMid(MID));
    }

    // ------------------------------------------------------------------ franchise merchant (the new rule)

    @Test
    void franchiseMerchantWithItsOwnUrl_usesItsOwnUrl_notTheFranchises() {
        device(franchise(3), merchant(7));
        when(callbackRepo.findByMerchant_Id(7L)).thenReturn(Optional.of(url("https://merchant.example/notify")));
        when(callbackRepo.findByFranchise_Id(3L)).thenReturn(Optional.of(url("https://franchise.example/notify")));

        assertEquals("https://merchant.example/notify", resolver.resolveForMid(MID).getCallback());
        verify(callbackRepo, never()).findByFranchise_Id(anyLong());   // only one URL is called
    }

    @Test
    void franchiseMerchantWithoutItsOwnUrl_fallsBackToTheFranchiseUrl_asBefore() {
        device(franchise(3), merchant(7));
        when(callbackRepo.findByFranchise_Id(3L)).thenReturn(Optional.of(url("https://franchise.example/notify")));

        assertEquals("https://franchise.example/notify", resolver.resolveForMid(MID).getCallback());
    }

    @Test
    void franchiseMerchantWhenNeitherHasAUrl_hasNoCallback() {
        device(franchise(3), merchant(7));

        assertNull(resolver.resolveForMid(MID));
    }

    @Test
    void anotherMerchantsUrl_isNeverUsed() {
        device(franchise(3), merchant(7));
        when(callbackRepo.findByMerchant_Id(8L)).thenReturn(Optional.of(url("https://other.example/notify")));

        assertNull(resolver.resolveForMid(MID));
        verify(callbackRepo, never()).findByMerchant_Id(8L);
    }
}
