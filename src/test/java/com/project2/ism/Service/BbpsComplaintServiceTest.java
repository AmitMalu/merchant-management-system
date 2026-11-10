package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Model.Bbps.BbpsComplaint;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Repository.BbpsComplaintRepository;
import com.project2.ism.Repository.BbpsTransactionRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.util.BillAvenueApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Complaints go to Bill Avenue only for the merchant's own payments, with the fields the API expects.
 */
class BbpsComplaintServiceTest {

    private static final String GOOD_DISPOSITION = BbpsComplaintService.DISPOSITIONS.get(0);

    private final ObjectMapper mapper = new ObjectMapper();
    private BillAvenueApiClient client;
    private BbpsTransactionRepository txns;
    private BbpsComplaintRepository complaints;
    private BbpsComplaintService service;

    @BeforeEach
    void setUp() {
        client = mock(BillAvenueApiClient.class);
        txns = mock(BbpsTransactionRepository.class);
        complaints = mock(BbpsComplaintRepository.class);
        PaymentVendorRepository vendors = mock(PaymentVendorRepository.class);

        PaymentVendor vendor = new PaymentVendor();
        ReflectionTestUtils.setField(vendor, "id", 7L);
        when(vendors.findByVendorNameAndStatus("Bill Avenue", true)).thenReturn(Optional.of(vendor));
        when(complaints.save(any(BbpsComplaint.class))).thenAnswer(i -> i.getArgument(0));

        service = new BbpsComplaintService(vendors, client, txns, complaints, mapper);
    }

    private BbpsTransaction txnOf(long merchantId) {
        BbpsTransaction t = new BbpsTransaction();
        t.setId(11L);
        t.setMerchantId(merchantId);
        t.setTxnRefId("CC01TXN");
        return t;
    }

    @Test
    void registersAgainstOwnTransaction_andKeepsTheComplaintId() throws Exception {
        when(txns.findByTxnRefId("CC01TXN")).thenReturn(Optional.of(txnOf(5L)));
        when(client.call(eq(7L), eq(BillAvenueApiType.COMPLAINT_REGISTER), anyString(), anyString())).thenReturn(
                "{\"complaintAssigned\":\"\",\"complaintId\":\"CC0125301466289\","
                        + "\"complaintResponseCode\":\"000\",\"complaintResponseReason\":\"SUCCESS\"}");

        BbpsComplaint saved = service.register(5L, " CC01TXN ", GOOD_DISPOSITION, "  Money was debited  ");

        assertEquals("CC0125301466289", saved.getComplaintId());
        assertEquals(5L, saved.getMerchantId());
        assertEquals("CC01TXN", saved.getTxnRefId());
        assertNull(saved.getComplaintAssigned());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(client).call(eq(7L), eq(BillAvenueApiType.COMPLAINT_REGISTER), anyString(), body.capture());
        JsonNode sent = mapper.readTree(body.getValue());
        assertEquals("Transaction", sent.get("complaintType").asText());
        assertEquals("Money was debited", sent.get("complainDesc").asText());
        assertEquals("CC01TXN", sent.get("txnRefId").asText());
        assertEquals(GOOD_DISPOSITION, sent.get("complaintDisposition").asText());
    }

    @Test
    void cannotComplainAboutAnotherMerchantsTransaction_orOneThatDoesNotExist() throws Exception {
        when(txns.findByTxnRefId("CC01TXN")).thenReturn(Optional.of(txnOf(99L)));
        when(txns.findByTxnRefId("NOPE")).thenReturn(Optional.empty());

        IllegalArgumentException other = assertThrows(IllegalArgumentException.class,
                () -> service.register(5L, "CC01TXN", GOOD_DISPOSITION, "x"));
        IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
                () -> service.register(5L, "NOPE", GOOD_DISPOSITION, "x"));

        assertEquals(other.getMessage(), none.getMessage());
        verifyNoInteractions(client);
        verify(complaints, never()).save(any());
    }

    @Test
    void badInput_isRefusedBeforeAnythingIsSent() {
        assertThrows(IllegalArgumentException.class, () -> service.register(5L, "", GOOD_DISPOSITION, "x"));
        assertThrows(IllegalArgumentException.class, () -> service.register(5L, "CC01TXN", "made up", "x"));
        assertThrows(IllegalArgumentException.class, () -> service.register(5L, "CC01TXN", GOOD_DISPOSITION, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> service.register(5L, "CC01TXN", GOOD_DISPOSITION, "a".repeat(256)));
        verifyNoInteractions(client);
    }

    @Test
    void whenBillAvenueRefuses_nothingIsSaved_andTheReasonIsShown() throws Exception {
        when(txns.findByTxnRefId("CC01TXN")).thenReturn(Optional.of(txnOf(5L)));
        when(client.call(eq(7L), eq(BillAvenueApiType.COMPLAINT_REGISTER), anyString(), anyString())).thenReturn(
                "{\"complaintResponseCode\":\"001\",\"complaintResponseReason\":\"Duplicate complaint\"}");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.register(5L, "CC01TXN", GOOD_DISPOSITION, "x"));

        assertTrue(ex.getMessage().contains("Duplicate complaint"));
        verify(complaints, never()).save(any());
    }

    @Test
    void trackingIsOnlyForOwnComplaints() throws Exception {
        when(complaints.findByComplaintIdAndMerchantId("CMP1", 5L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.track(5L, "CMP1"));
        assertEquals("No complaint found for that ID", ex.getMessage());
        verifyNoInteractions(client);
    }

    @Test
    void trackingUpdatesTheStatusWeKeep() throws Exception {
        BbpsComplaint own = new BbpsComplaint();
        own.setMerchantId(5L);
        own.setComplaintId("CMP1");
        own.setStatus("REGISTERED");
        when(complaints.findByComplaintIdAndMerchantId("CMP1", 5L)).thenReturn(Optional.of(own));
        when(client.call(eq(7L), eq(BillAvenueApiType.COMPLAINT_TRACK), anyString(), eq("{\"complaintId\":\"CMP1\"}")))
                .thenReturn("{\"complaintAssigned\":\"BILLER\",\"complaintId\":\"CMP1\",\"complaintStatus\":\"ASSIGNED\","
                        + "\"complaintResponseCode\":\"000\",\"complaintResponseReason\":\"SUCCESS\",\"complaintRemarks\":\"NA\"}");

        BbpsComplaint result = service.track(5L, "CMP1");

        assertEquals("ASSIGNED", result.getStatus());
        assertEquals("BILLER", result.getComplaintAssigned());
        assertEquals("NA", result.getRemarks());
        assertNotNull(result.getLastCheckedAt());
    }
}
