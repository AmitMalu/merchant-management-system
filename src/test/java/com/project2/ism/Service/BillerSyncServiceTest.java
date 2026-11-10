package com.project2.ism.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Model.Bbps.BillAvenueConfig;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payment.PaymentVendorCredentials;
import com.project2.ism.Repository.BillAvenueConfigRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.util.BillAvenueApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Reloading the biller list: a preview changes nothing; old billers are removed only when asked.
 */
class BillerSyncServiceTest {

    private BillAvenueApiClient client;
    private BillAvenueConfigRepository repo;
    private BillerSyncService service;

    @BeforeEach
    void setUp() {
        client = mock(BillAvenueApiClient.class);
        repo = mock(BillAvenueConfigRepository.class);
        PaymentVendorRepository vendors = mock(PaymentVendorRepository.class);
        PaymentVendorCredentialsService creds = mock(PaymentVendorCredentialsService.class);

        PaymentVendor vendor = new PaymentVendor();
        ReflectionTestUtils.setField(vendor, "id", 7L);
        when(vendors.findByVendorNameAndStatus("Bill Avenue", true)).thenReturn(Optional.of(vendor));
        PaymentVendorCredentials c = new PaymentVendorCredentials();
        c.setActiveEnvironment("PROD");
        when(creds.getActiveEnvironmentCredentials(7L)).thenReturn(c);

        service = new BillerSyncService(vendors, client, repo, creds, new ObjectMapper());
    }

    private static final String ANSWER = "{\"responseCode\":\"000\",\"biller\":["
            + "{\"billerId\":\"HDFC00000NAT01\",\"billerName\":\"HDFC Bank Credit Card\",\"billerCategory\":\"Credit Card\"},"
            + "{\"billerId\":\"NEW000000NAT01\",\"billerName\":\"New Bank Card\",\"billerCategory\":\"Credit Card\"}]}";

    private BillAvenueConfig saved(String id, String name, String category, String raw) {
        BillAvenueConfig c = new BillAvenueConfig();
        c.setProviderId(id);
        c.setProviderName(name);
        c.setServiceName(category);
        c.setRawConfig(raw);
        return c;
    }

    private void savedRows(BillAvenueConfig... rows) {
        when(repo.findByPaymentVendor_Id(7L)).thenReturn(List.of(rows));
    }

    @Test
    @SuppressWarnings("unchecked")
    void preview_countsEverything_butChangesNothing() throws Exception {
        savedRows(
                saved("HDFC00000NAT01", "HDFC Credit Card OLD NAME", "Credit Card", "{}"),
                saved("DUMMY0000DIG08", "Staging biller", "Broadband Postpaid", "{}"));
        when(client.call(eq(7L), eq(BillAvenueApiType.BILLER_INFO), any(), eq("{\"billerId\":[]}"))).thenReturn(ANSWER);

        Map<String, Object> r = service.sync(null, true, true);   // even with removeStale, a preview changes nothing

        assertEquals("PROD", r.get("environment"));
        assertEquals(2, r.get("fetched"));
        assertEquals(1, r.get("added"));
        assertEquals(1, r.get("updated"));
        assertEquals(1, r.get("notInBillAvenueList"));
        assertEquals(1L, r.get("notInBillAvenueListLookingLikeStaging"));
        assertEquals(0, r.get("removed"));
        assertEquals(Map.of("Credit Card", 2), r.get("categories"));
        verify(repo, never()).saveAll(any());
        verify(repo, never()).deleteAll(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void apply_savesNewAndChanged_andKeepsOldOnesUnlessAskedToRemoveThem() throws Exception {
        BillAvenueConfig old = saved("HDFC00000NAT01", "HDFC Credit Card OLD NAME", "Credit Card", "{}");
        BillAvenueConfig staging = saved("DUMMY0000DIG08", "Staging biller", "Broadband Postpaid", "{}");
        savedRows(old, staging);
        when(client.call(any(), any(), any(), any())).thenReturn(ANSWER);

        Map<String, Object> r = service.sync(null, false, false);

        ArgumentCaptor<List<BillAvenueConfig>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(saveCaptor.capture());
        assertEquals(2, saveCaptor.getValue().size());
        assertEquals("HDFC Bank Credit Card", old.getProviderName());
        assertEquals(0, r.get("removed"));
        verify(repo, never()).deleteAll(any());

        Map<String, Object> removing = service.sync(null, false, true);
        verify(repo).deleteAll(List.of(staging));
        assertEquals(1, removing.get("removed"));
    }

    @Test
    void unchangedBillers_areNotRewritten_evenIfTheDatabaseReordersTheJson() throws Exception {
        savedRows(saved("HDFC00000NAT01", "HDFC Bank Credit Card", "Credit Card",
                "{ \"billerCategory\" : \"Credit Card\", \"billerName\":\"HDFC Bank Credit Card\", \"billerId\":\"HDFC00000NAT01\" }"));
        when(client.call(any(), any(), any(), any())).thenReturn(ANSWER);

        Map<String, Object> r = service.sync(null, true, false);

        assertEquals(1, r.get("unchanged"));
        assertEquals(1, r.get("added"));
        assertEquals(0, r.get("updated"));
    }

    @Test
    void aSyncOfSpecificBillers_neverTreatsTheOthersAsOld() throws Exception {
        savedRows(saved("OTHER0000NAT01", "Other", "Credit Card", "{}"));
        when(client.call(eq(7L), eq(BillAvenueApiType.BILLER_INFO), any(), eq("{\"billerId\":[\"HDFC00000NAT01\"]}")))
                .thenReturn(ANSWER);

        Map<String, Object> r = service.sync(List.of("HDFC00000NAT01"), false, true);

        assertEquals(0, r.get("notInBillAvenueList"));
        verify(repo, never()).deleteAll(any());
    }

    @Test
    void anEmptyOrRefusedAnswer_changesNothing() throws Exception {
        savedRows(saved("HDFC00000NAT01", "x", "Credit Card", "{}"));
        when(client.call(any(), any(), any(), any()))
                .thenReturn("{\"responseCode\":\"001\",\"errorInfo\":{\"error\":[{\"errorMessage\":\"No access\"}]}}");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.sync(null, false, true));

        assertTrue(ex.getMessage().contains("No access"));
        verify(repo, never()).saveAll(any());
        verify(repo, never()).deleteAll(any());
    }

    @Test
    void billerIds_mustBeFourteenCharacters_checkedBeforeAnyRequest() throws Exception {
        savedRows();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.sync(List.of("HDFC00000NAT01", "short"), true, false));
        assertTrue(ex.getMessage().contains("short"));
        verifyNoInteractions(client);
    }

    @Test
    void manyIds_areSentInBatchesOfTwoThousand_andAnOversizedListIsRefusedUpFront() throws Exception {
        savedRows();
        List<String> ids = java.util.stream.IntStream.range(0, 2001)
                .mapToObj(i -> String.format("ABCD%010d", i)).toList();
        when(client.call(any(), any(), any(), any())).thenReturn(ANSWER);

        service.sync(ids, true, false);
        verify(client, times(2)).call(any(), any(), any(), any());

        List<String> tooMany = java.util.stream.IntStream.range(0, 20001)
                .mapToObj(i -> String.format("ABCD%010d", i)).toList();
        assertThrows(IllegalArgumentException.class, () -> service.sync(tooMany, true, false));
        verify(client, times(2)).call(any(), any(), any(), any());   // no further request was made
    }

    @Test
    void whenBillAvenueRefusesTheCompleteListRequest_theMessageSaysToSendIds_andNothingChanges() throws Exception {
        savedRows(saved("HDFC00000NAT01", "x", "Credit Card", "{}"));
        when(client.call(any(), any(), any(), any())).thenThrow(new RuntimeException(
                "Received JSON error response from Bill Avenue: {\"responseCode\":\"205\"}"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.sync(null, false, true));

        assertTrue(ex.getMessage().contains("biller IDs"), ex.getMessage());
        verify(repo, never()).saveAll(any());
        verify(repo, never()).deleteAll(any());
    }
}
