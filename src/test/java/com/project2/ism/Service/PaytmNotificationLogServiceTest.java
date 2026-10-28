package com.project2.ism.Service;

import com.project2.ism.Model.Logs.PaytmNotificationLog;
import com.project2.ism.Repository.PaytmNotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class PaytmNotificationLogServiceTest {

    private PaytmNotificationLogRepository repository;
    private PaytmNotificationLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaytmNotificationLogRepository.class);
        when(repository.save(any(PaytmNotificationLog.class))).thenAnswer(i -> i.getArgument(0));
        service = new PaytmNotificationLogService(repository);
        ReflectionTestUtils.setField(service, "maxSavedPerMinute", 300);
    }

    private PaytmNotificationLog record(String body) {
        return service.record("10.0.0.1", "52.1.2.3", "application/json", "host=x", body, body.length(), false);
    }

    // ------------------------------------------------------------------ what is stored

    @Test
    void storesTheNotification_asReceived() {
        PaytmNotificationLog saved = service.record("10.0.0.1", "52.1.2.3", "application/json",
                "host=portal, content-type=application/json", "{\"a\":1}", 7, false);

        assertNotNull(saved);
        assertEquals("10.0.0.1", saved.getRemoteAddr());
        assertEquals("52.1.2.3", saved.getForwardedFor());
        assertEquals("application/json", saved.getContentType());
        assertEquals("{\"a\":1}", saved.getRawBody());
        assertEquals(7, saved.getBodyLength());
        assertFalse(saved.getBodyTruncated());
        assertEquals("RECEIVED", saved.getProcessStatus());
    }

    @Test
    void truncatedBody_isFlagged_andKeepsItsRealSize() {
        PaytmNotificationLog saved = service.record("1.1.1.1", null, "text/plain", "", "x".repeat(20_000), 200_000, true);

        assertTrue(saved.getBodyTruncated());
        assertEquals(200_000, saved.getBodyLength());
    }

    @Test
    void overlongFieldsAreCut_soOneCallCannotStoreAnythingHuge() {
        PaytmNotificationLog saved = service.record("r".repeat(500), "f".repeat(2000), "c".repeat(2000),
                "h".repeat(50_000), "{}", 2, false);

        assertEquals(64, saved.getRemoteAddr().length());
        assertEquals(255, saved.getForwardedFor().length());
        assertEquals(150, saved.getContentType().length());
        assertEquals(PaytmNotificationLogService.MAX_HEADERS_CHARS, saved.getHeaders().length());
    }

    // ------------------------------------------------------------------ order id / txn id

    @Test
    void jsonBody_orderIdAndTxnIdAreFound_whateverTheCase() {
        PaytmNotificationLog saved = record("{\"ORDERID\":\"ORD-9\",\"txnId\":\"TX-5\",\"status\":\"TXN_SUCCESS\"}");

        assertEquals("ORD-9", saved.getOrderId());
        assertEquals("TX-5", saved.getTxnId());
    }

    @Test
    void jsonBody_idsInsideTheBodyObject_areFound() {
        PaytmNotificationLog saved = record("{\"head\":{\"signature\":\"s\"},\"body\":{\"orderId\":\"ORD-1\",\"txnId\":\"TX-1\"}}");

        assertEquals("ORD-1", saved.getOrderId());
        assertEquals("TX-1", saved.getTxnId());
    }

    @Test
    void formBody_idsAreFound_andDecoded() {
        PaytmNotificationLog saved = record("ORDERID=ORD%2D7&TXNID=TX7&STATUS=TXN_SUCCESS&CHECKSUMHASH=abc");

        assertEquals("ORD-7", saved.getOrderId());
        assertEquals("TX7", saved.getTxnId());
    }

    @Test
    void unknownOrBrokenBody_isStoredWithoutIds() {
        for (String body : new String[]{"", "   ", "hello", "{broken json", "[1,2,3]", "a=b&c=d"}) {
            PaytmNotificationLog saved = record(body);

            assertNotNull(saved, body);
            assertNull(saved.getOrderId(), body);
            assertNull(saved.getTxnId(), body);
            assertEquals(body, saved.getRawBody());
        }
    }

    // ------------------------------------------------------------------ never breaks the call

    @Test
    void databaseProblem_isSwallowed() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        assertNull(record("{}"));   // no exception
    }

    @Test
    void floodGuard_stopsStoring_butDoesNotThrow() {
        when(repository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(300L);

        assertNull(record("{}"));

        verify(repository, never()).save(any());
    }

    @Test
    void belowTheLimit_stillStores() {
        when(repository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(299L);

        assertNotNull(record("{}"));
    }

    @Test
    void limitOfZero_switchesTheGuardOff() {
        ReflectionTestUtils.setField(service, "maxSavedPerMinute", 0);
        when(repository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(1_000_000L);

        assertNotNull(record("{}"));
    }

    // ------------------------------------------------------------------ admin search

    @Test
    void search_allMeansNoStatusFilter_andDatesCoverWholeDays() {
        when(repository.findLogsByFilters(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.search("all", "  ", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 27), 0, 100);

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findLogsByFilters(isNull(), isNull(), start.capture(), end.capture(), any(Pageable.class));
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), start.getValue());
        assertEquals(LocalDateTime.of(2026, 10, 27, 23, 59, 59), end.getValue());
    }

    @Test
    void search_statusAndReferenceArePassedOn_andPageSizeIsCapped() {
        when(repository.findLogsByFilters(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        service.search(" received ", " ORD-1 ", null, null, -5, 100_000);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findLogsByFilters(eq("ORD-1"), eq("RECEIVED"), isNull(), isNull(), page.capture());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(200, page.getValue().getPageSize());
    }

    // ------------------------------------------------------------------ cleanup

    @Test
    void cleanup_deletesOnlyRowsOlderThanNinetyDays() {
        when(repository.deleteOlderThan(any())).thenReturn(3);

        service.cleanupOldLogs();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteOlderThan(cutoff.capture());
        assertTrue(cutoff.getValue().isBefore(LocalDateTime.now().minusDays(89)));
        assertTrue(cutoff.getValue().isAfter(LocalDateTime.now().minusDays(91)));
    }

    @Test
    void cleanup_failure_doesNotThrow() {
        when(repository.deleteOlderThan(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.cleanupOldLogs());
    }
}
