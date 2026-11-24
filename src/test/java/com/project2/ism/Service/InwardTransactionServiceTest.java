package com.project2.ism.Service;

import com.project2.ism.DTO.InwardTransactionDTO;
import com.project2.ism.DTO.ProductSerialDTO;
import com.project2.ism.Exception.DuplicateResourceException;
import com.project2.ism.Model.InventoryTransactions.InwardTransactions;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Vendor.Vendor;
import com.project2.ism.Repository.InwardTransactionRepository;
import com.project2.ism.Repository.ProductRepository;
import com.project2.ism.Repository.ProductSerialsRepository;
import com.project2.ism.Repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Saving an inward entry: the same MID / TID / SID / VPAID twice in one entry must be refused with a
 * clear message (the database would otherwise refuse it later with a vague one), and blank values count
 * as "not entered".
 */
class InwardTransactionServiceTest {

    private InwardTransactionRepository inwardRepo;
    private ProductSerialsRepository serialRepo;
    private InwardTransactionService service;

    @BeforeEach
    void setUp() {
        inwardRepo = mock(InwardTransactionRepository.class);
        serialRepo = mock(ProductSerialsRepository.class);
        VendorRepository vendorRepo = mock(VendorRepository.class);
        ProductRepository productRepo = mock(ProductRepository.class);
        service = new InwardTransactionService(inwardRepo, serialRepo, vendorRepo, productRepo);

        when(vendorRepo.findById(1L)).thenReturn(Optional.of(new Vendor()));
        when(productRepo.findById(2L)).thenReturn(Optional.of(new Product()));
        when(inwardRepo.save(any(InwardTransactions.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static ProductSerialDTO serial(String mid, String tid, String sid, String vpaid) {
        ProductSerialDTO dto = new ProductSerialDTO();
        dto.mid = mid;
        dto.tid = tid;
        dto.sid = sid;
        dto.vpaid = vpaid;
        return dto;
    }

    private static InwardTransactionDTO entry(ProductSerialDTO... serials) {
        InwardTransactionDTO dto = new InwardTransactionDTO();
        dto.vendorId = 1L;
        dto.productId = 2L;
        dto.quantity = serials.length;
        dto.serialNumbers = new ArrayList<>(List.of(serials));
        return dto;
    }

    @Test
    void theSameMidTwiceInOneEntry_isRefusedWithAClearMessage() {
        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> service.createTransaction(entry(serial("MID1", "T1", null, null), serial("MID1", "T2", null, null))));

        assertEquals("MID MID1 is entered more than once in this entry", ex.getMessage());
        verify(inwardRepo, never()).save(any());
    }

    @Test
    void repeatsAreFoundWhateverTheCaseOrSpaces() {
        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> service.createTransaction(entry(serial(null, "tid-9", null, null), serial(null, " TID-9 ", null, null))));

        assertEquals("TID TID-9 is entered more than once in this entry", ex.getMessage());
    }

    @Test
    void theSameSidOrVpaidTwice_isRefusedToo() {
        assertThrows(DuplicateResourceException.class,
                () -> service.createTransaction(entry(serial(null, null, "S1", null), serial(null, null, "S1", null))));
        assertThrows(DuplicateResourceException.class,
                () -> service.createTransaction(entry(serial(null, null, null, "V1"), serial(null, null, null, "V1"))));
    }

    @Test
    void blankValuesAreNotEntered_soManyBlanksAreFine_andValuesAreTrimmed() {
        InwardTransactionDTO dto = entry(serial("M1", "", "  ", null), serial("M2", " ", null, ""));

        InwardTransactionDTO saved = service.createTransaction(dto);

        assertEquals("M1", saved.serialNumbers.get(0).mid);
        assertNull(saved.serialNumbers.get(0).tid);
        assertNull(saved.serialNumbers.get(0).sid);
        assertNull(saved.serialNumbers.get(1).tid);
        assertNull(saved.serialNumbers.get(1).vpaid);
    }

    @Test
    void aValueAlreadySavedIsStillRefusedByTheOldCheck() {
        when(serialRepo.existsByMid("M1")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> service.createTransaction(entry(serial("M1", null, null, null))));

        assertEquals("MID already exists: M1", ex.getMessage());
    }
}
