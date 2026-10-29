package com.project2.ism.Service;

import com.project2.ism.DTO.NotificationCallbackDtos.CreateRequest;
import com.project2.ism.DTO.NotificationCallbackDtos.UpdateRequest;
import com.project2.ism.DTO.NotificationCallbackDtos.View;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationCallback;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseOrMerchantNotificationCallbackRepository;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationCallbackServiceTest {

    private static final String URL = "https://example.com/notify";

    private FranchiseOrMerchantNotificationCallbackRepository repo;
    private FranchiseRepository franchiseRepo;
    private MerchantRepository merchantRepo;
    private NotificationCallbackService service;

    @BeforeEach
    void setUp() {
        repo = mock(FranchiseOrMerchantNotificationCallbackRepository.class);
        franchiseRepo = mock(FranchiseRepository.class);
        merchantRepo = mock(MerchantRepository.class);
        service = new NotificationCallbackService(repo, franchiseRepo, merchantRepo);

        when(repo.save(any(FranchiseOrMerchantNotificationCallback.class))).thenAnswer(i -> {
            FranchiseOrMerchantNotificationCallback c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(99L);
            }
            return c;
        });
        stubSaveAll();
    }

    private void stubSaveAll() {
        when(repo.saveAll(any())).thenAnswer(i -> {
            java.util.List<FranchiseOrMerchantNotificationCallback> rows = new java.util.ArrayList<>();
            long next = 100;
            for (FranchiseOrMerchantNotificationCallback c : (Iterable<FranchiseOrMerchantNotificationCallback>) i.getArgument(0)) {
                c.setId(next++);
                rows.add(c);
            }
            return rows;
        });
    }

    private Franchise franchise(long id, String name) {
        Franchise f = new Franchise();
        ReflectionTestUtils.setField(f, "id", id);
        ReflectionTestUtils.setField(f, "franchiseName", name);
        when(franchiseRepo.findById(id)).thenReturn(Optional.of(f));
        return f;
    }

    private Merchant merchant(long id, String name, Franchise owner) {
        Merchant m = new Merchant();
        ReflectionTestUtils.setField(m, "id", id);
        ReflectionTestUtils.setField(m, "businessName", name);
        ReflectionTestUtils.setField(m, "franchise", owner);
        when(merchantRepo.findById(id)).thenReturn(Optional.of(m));
        return m;
    }

    private FranchiseOrMerchantNotificationCallback saved() {
        ArgumentCaptor<FranchiseOrMerchantNotificationCallback> c = ArgumentCaptor.forClass(FranchiseOrMerchantNotificationCallback.class);
        verify(repo).save(c.capture());
        return c.getValue();
    }

    // ------------------------------------------------------------------ create: how rows are stored

    @Test
    void franchiseUrl_isStoredWithTheFranchiseOnly() {
        franchise(3, "Sarthak");

        View v = service.create(new CreateRequest("FRANCHISE", 3L, null, URL), "admin@x.com");

        FranchiseOrMerchantNotificationCallback row = saved();
        assertEquals(3L, row.getFranchise().getId());
        assertNull(row.getMerchant());
        assertEquals(URL, row.getCallback());
        assertEquals("FRANCHISE", v.type());
        assertEquals("Sarthak", v.franchiseName());
    }

    @Test
    void franchiseMerchantUrl_isStoredWithTheMerchantOnly_soTheFranchiseLookupNeverFindsIt() {
        Franchise f = franchise(3, "Sarthak");
        merchant(7, "Vaibhav", f);

        View v = service.create(new CreateRequest("FRANCHISE_MERCHANT", 3L, 7L, URL), "admin@x.com");

        FranchiseOrMerchantNotificationCallback row = saved();
        assertNull(row.getFranchise());
        assertEquals(7L, row.getMerchant().getId());
        assertEquals("FRANCHISE_MERCHANT", v.type());
        assertEquals("Vaibhav", v.merchantName());
        assertEquals("Sarthak", v.franchiseName());
    }

    @Test
    void directMerchantUrl_isStoredWithTheMerchantOnly() {
        merchant(8, "Direct Co", null);

        View v = service.create(new CreateRequest("direct_merchant", null, 8L, URL), "admin@x.com");

        FranchiseOrMerchantNotificationCallback row = saved();
        assertNull(row.getFranchise());
        assertEquals(8L, row.getMerchant().getId());
        assertEquals("DIRECT_MERCHANT", v.type());
        assertNull(v.franchiseName());
    }

    @Test
    void franchiseMerchant_franchiseIdIsOptional() {
        merchant(7, "Vaibhav", franchise(3, "Sarthak"));

        assertDoesNotThrow(() -> service.create(new CreateRequest("FRANCHISE_MERCHANT", null, 7L, URL), "a"));
    }

    // ------------------------------------------------------------------ create: refused

    @Test
    void oneUrlPerFranchise() {
        franchise(3, "Sarthak");
        when(repo.existsByFranchise_Id(3L)).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.create(new CreateRequest("FRANCHISE", 3L, null, URL), "a"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(repo, never()).save(any());
    }

    @Test
    void oneUrlPerMerchant() {
        merchant(7, "Vaibhav", franchise(3, "Sarthak"));
        when(repo.existsByMerchant_Id(7L)).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.create(new CreateRequest("FRANCHISE_MERCHANT", 3L, 7L, URL), "a"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        verify(repo, never()).save(any());
    }

    @Test
    void directMerchantType_isRefusedForAFranchiseMerchant_andTheOtherWayRound() {
        merchant(7, "Vaibhav", franchise(3, "Sarthak"));
        merchant(8, "Direct Co", null);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateRequest("DIRECT_MERCHANT", null, 7L, URL), "a"));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateRequest("FRANCHISE_MERCHANT", null, 8L, URL), "a"));
        verify(repo, never()).save(any());
    }

    @Test
    void merchantThatDoesNotBelongToTheSelectedFranchise_isRefused() {
        merchant(7, "Vaibhav", franchise(3, "Sarthak"));
        franchise(4, "Other");

        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateRequest("FRANCHISE_MERCHANT", 4L, 7L, URL), "a"));
    }

    @Test
    void missingOrUnknownTargets_areRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateRequest("FRANCHISE", null, null, URL), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateRequest("FRANCHISE", 99L, null, URL), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateRequest("DIRECT_MERCHANT", null, null, URL), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateRequest("DIRECT_MERCHANT", null, 99L, URL), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateRequest("BOSS", 1L, 1L, URL), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateRequest(null, 1L, null, URL), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.create(null, "a"));
        verify(repo, never()).save(any());
    }

    @Test
    void franchiseUrl_cannotAlsoNameAMerchant() {
        franchise(3, "Sarthak");

        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateRequest("FRANCHISE", 3L, 7L, URL), "a"));
    }

    @Test
    void badUrl_isRefusedBeforeAnythingIsLookedUp() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateRequest("FRANCHISE", 3L, null, "ftp://x.com"), "a"));

        verifyNoInteractions(franchiseRepo, merchantRepo);
        verify(repo, never()).save(any());
    }

    // ------------------------------------------------------------------ create for several merchants at once

    @SuppressWarnings("unchecked")
    private List<FranchiseOrMerchantNotificationCallback> savedMany() {
        ArgumentCaptor<Iterable<FranchiseOrMerchantNotificationCallback>> c = ArgumentCaptor.forClass(Iterable.class);
        verify(repo).saveAll(c.capture());
        List<FranchiseOrMerchantNotificationCallback> out = new java.util.ArrayList<>();
        c.getValue().forEach(out::add);
        return out;
    }

    private com.project2.ism.DTO.NotificationCallbackDtos.BulkCreateRequest bulk(String type, Long franchiseId, Long... merchantIds) {
        return new com.project2.ism.DTO.NotificationCallbackDtos.BulkCreateRequest(type, franchiseId, java.util.Arrays.asList(merchantIds), URL);
    }

    @Test
    void severalFranchiseMerchants_getTheSameUrl_eachInItsOwnRow() {
        Franchise f = franchise(3, "Sarthak");
        merchant(1, "M1", f);
        merchant(2, "M2", f);
        merchant(3, "M3", f);

        List<View> views = service.createMany(bulk("FRANCHISE_MERCHANT", 3L, 1L, 2L, 3L), "admin@x.com");

        List<FranchiseOrMerchantNotificationCallback> rows = savedMany();
        assertEquals(3, rows.size());
        assertEquals(List.of(1L, 2L, 3L), rows.stream().map(r -> r.getMerchant().getId()).toList());
        assertTrue(rows.stream().allMatch(r -> URL.equals(r.getCallback())));
        assertTrue(rows.stream().allMatch(r -> r.getFranchise() == null));    // only the merchant, like a single add
        assertEquals(3, views.size());
        assertEquals("FRANCHISE_MERCHANT", views.get(0).type());
        assertEquals("Sarthak", views.get(0).franchiseName());
    }

    @Test
    void severalDirectMerchants_getTheSameUrl() {
        merchant(8, "D1", null);
        merchant(9, "D2", null);

        List<View> views = service.createMany(bulk("DIRECT_MERCHANT", null, 8L, 9L), "a");

        assertEquals(2, savedMany().size());
        assertEquals("DIRECT_MERCHANT", views.get(0).type());
    }

    @Test
    void duplicateIdsInTheRequest_areCountedOnce() {
        Franchise f = franchise(3, "Sarthak");
        merchant(1, "M1", f);

        List<View> views = service.createMany(bulk("FRANCHISE_MERCHANT", 3L, 1L, 1L, null), "a");

        assertEquals(1, views.size());
    }

    @Test
    void oneMerchantAlreadyHavingAUrl_savesNothing_andNamesIt() {
        Franchise f = franchise(3, "Sarthak");
        merchant(1, "M1", f);
        merchant(2, "Vaibhav", f);
        merchant(3, "M3", f);
        when(repo.existsByMerchant_Id(2L)).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.createMany(bulk("FRANCHISE_MERCHANT", 3L, 1L, 2L, 3L), "a"));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(e.getReason().startsWith("Nothing was saved."));
        assertTrue(e.getReason().contains("Vaibhav"));
        assertFalse(e.getReason().contains("M1"));
        verify(repo, never()).saveAll(any());
        verify(repo, never()).save(any());
    }

    @Test
    void anyOtherProblem_savesNothing_andListsEveryProblem() {
        Franchise f3 = franchise(3, "Sarthak");
        Franchise f4 = franchise(4, "Other");
        merchant(1, "M1", f3);
        merchant(2, "Stranger", f4);        // belongs to another franchise
        merchant(5, "Direct Co", null);     // not a franchise merchant
        when(repo.existsByMerchant_Id(1L)).thenReturn(true);   // and a plain duplicate too

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.createMany(bulk("FRANCHISE_MERCHANT", 3L, 1L, 2L, 5L, 77L), "a"));

        String m = e.getMessage();
        assertTrue(m.startsWith("Nothing was saved."));
        assertTrue(m.contains("M1"));
        assertTrue(m.contains("Stranger"));
        assertTrue(m.contains("Direct Co"));
        assertTrue(m.contains("#77"));
        verify(repo, never()).saveAll(any());
    }

    @Test
    void directType_refusesAFranchiseMerchant_inABatch() {
        merchant(1, "M1", franchise(3, "Sarthak"));
        merchant(8, "D1", null);

        assertThrows(IllegalArgumentException.class,
                () -> service.createMany(bulk("DIRECT_MERCHANT", null, 8L, 1L), "a"));
        verify(repo, never()).saveAll(any());
    }

    @Test
    void emptyTooBigOrWrongTypeRequests_areRefused_beforeAnyLookup() {
        assertThrows(IllegalArgumentException.class, () -> service.createMany(null, "a"));
        assertThrows(IllegalArgumentException.class, () -> service.createMany(bulk("FRANCHISE_MERCHANT", 3L), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.createMany(
                new com.project2.ism.DTO.NotificationCallbackDtos.BulkCreateRequest("FRANCHISE_MERCHANT", 3L, null, URL), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.createMany(bulk("FRANCHISE", 3L, 1L), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.createMany(bulk(null, 3L, 1L), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.createMany(
                new com.project2.ism.DTO.NotificationCallbackDtos.BulkCreateRequest("DIRECT_MERCHANT", null,
                        java.util.stream.LongStream.rangeClosed(1, NotificationCallbackService.MAX_BULK + 1L).boxed().toList(), URL), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.createMany(
                new com.project2.ism.DTO.NotificationCallbackDtos.BulkCreateRequest("DIRECT_MERCHANT", null, List.of(1L), "ftp://bad"), "a"));
        verifyNoInteractions(merchantRepo);
        verify(repo, never()).saveAll(any());
    }

    // ------------------------------------------------------------------ update / delete

    @Test
    void update_changesOnlyTheUrl_andTheTime() {
        FranchiseOrMerchantNotificationCallback row = new FranchiseOrMerchantNotificationCallback();
        row.setId(5L);
        row.setCallback("https://old.example/notify");
        row.setMerchant(merchant(7, "Vaibhav", null));
        LocalDateTime before = LocalDateTime.now().minusDays(3);
        row.setUpdatedAt(before);
        when(repo.findById(5L)).thenReturn(Optional.of(row));

        View v = service.update(5L, new UpdateRequest("  https://new.example/notify  "), "admin@x.com");

        assertEquals("https://new.example/notify", v.callback());
        assertEquals(7L, v.merchantId());
        assertTrue(row.getUpdatedAt().isAfter(before));
    }

    @Test
    void update_andDelete_ofAnUnknownId_are404() {
        when(repo.findById(5L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.update(5L, new UpdateRequest(URL), "a")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.delete(5L, "a")).getStatusCode());
    }

    @Test
    void update_withABadUrl_isRefused_andNothingIsSaved() {
        FranchiseOrMerchantNotificationCallback row = new FranchiseOrMerchantNotificationCallback();
        row.setId(5L);
        when(repo.findById(5L)).thenReturn(Optional.of(row));

        assertThrows(IllegalArgumentException.class, () -> service.update(5L, new UpdateRequest("not a url"), "a"));
        assertThrows(IllegalArgumentException.class, () -> service.update(5L, null, "a"));
        verify(repo, never()).save(any());
    }

    @Test
    void delete_removesTheRow() {
        FranchiseOrMerchantNotificationCallback row = new FranchiseOrMerchantNotificationCallback();
        row.setId(5L);
        when(repo.findById(5L)).thenReturn(Optional.of(row));

        service.delete(5L, "admin@x.com");

        verify(repo).delete(row);
    }

    // ------------------------------------------------------------------ list

    private FranchiseOrMerchantNotificationCallback row(long id, Franchise f, Merchant m, String url) {
        FranchiseOrMerchantNotificationCallback c = new FranchiseOrMerchantNotificationCallback();
        c.setId(id);
        c.setFranchise(f);
        c.setMerchant(m);
        c.setCallback(url);
        return c;
    }

    @Test
    void list_derivesTheTypeOfEachRow_andFlagsOldOnesThatDoNotFit() {
        Franchise f3 = franchise(3, "Sarthak");
        Franchise f4 = franchise(4, "Other");
        Merchant fm = merchant(7, "Vaibhav", f3);
        Merchant dm = merchant(8, "Direct Co", null);
        when(repo.findAllWithOwners()).thenReturn(List.of(
                row(1, f3, null, "https://a.example/n"),        // franchise
                row(2, null, fm, "https://b.example/n"),        // franchise merchant
                row(3, null, dm, "https://c.example/n"),        // direct merchant
                row(4, f4, fm, "https://d.example/n"),          // old: both set
                row(5, null, null, "https://e.example/n")));    // old: nobody

        List<View> all = service.list(null, null, null);

        assertEquals(List.of("FRANCHISE", "FRANCHISE_MERCHANT", "DIRECT_MERCHANT", "FRANCHISE_MERCHANT", "UNKNOWN"),
                all.stream().map(View::type).toList());
        assertNull(all.get(0).warning());
        assertNull(all.get(1).warning());
        assertNotNull(all.get(3).warning());
        assertNotNull(all.get(4).warning());
    }

    @Test
    void list_filtersByTypeFranchiseAndText() {
        Franchise f3 = franchise(3, "Sarthak");
        Franchise f4 = franchise(4, "Other");
        Merchant fm = merchant(7, "Vaibhav", f3);
        Merchant om = merchant(9, "Else", f4);
        Merchant dm = merchant(8, "Direct Co", null);
        when(repo.findAllWithOwners()).thenReturn(List.of(
                row(1, f3, null, "https://a.example/n"),
                row(2, null, fm, "https://b.example/n"),
                row(3, null, dm, "https://c.example/n"),
                row(4, null, om, "https://d.example/n")));

        assertEquals(List.of(3L), service.list("DIRECT_MERCHANT", null, null).stream().map(View::id).toList());
        assertEquals(List.of(1L, 2L), service.list(null, 3L, null).stream().map(View::id).toList());   // the franchise and its merchants
        assertEquals(List.of(2L), service.list("FRANCHISE_MERCHANT", 3L, null).stream().map(View::id).toList());
        assertEquals(List.of(2L), service.list("all", null, "VAIBHAV").stream().map(View::id).toList());
        assertEquals(List.of(3L), service.list(null, null, "c.example").stream().map(View::id).toList());
        assertEquals(4, service.list("ALL", null, "  ").size());
    }

    // ------------------------------------------------------------------ URL rules

    @Test
    void validUrls_areAccepted_andTrimmed() {
        assertEquals("https://example.com/notify", NotificationCallbackService.validateCallbackUrl("  https://example.com/notify "));
        assertEquals("http://example.com:8080/a/b?x=1&y=2", NotificationCallbackService.validateCallbackUrl("http://example.com:8080/a/b?x=1&y=2"));
        assertEquals("HTTPS://Example.com", NotificationCallbackService.validateCallbackUrl("HTTPS://Example.com"));
        assertEquals("https://203.0.113.10/notify", NotificationCallbackService.validateCallbackUrl("https://203.0.113.10/notify"));
    }

    @Test
    void badUrls_areRefused() {
        for (String bad : new String[]{null, "", "   ", "example.com/notify", "ftp://example.com", "javascript:alert(1)",
                "https://", "https:///path", "https://exa mple.com", "https://user:pass@example.com/n",
                "https://" + "a".repeat(260) + ".com", "http://[::1]/n"}) {
            assertThrows(IllegalArgumentException.class, () -> NotificationCallbackService.validateCallbackUrl(bad), String.valueOf(bad));
        }
    }

    @Test
    void addressesInsideOurOwnNetwork_areRefused() {
        for (String internal : new String[]{
                "http://localhost/n", "http://LOCALHOST:8080/n", "http://app.localhost/n", "http://db.internal/n", "http://printer.local/n",
                "http://127.0.0.1/n", "http://10.0.0.5/n", "http://172.16.0.1/n", "http://192.168.1.10/n",
                "http://169.254.169.254/latest/meta-data", "http://0.0.0.0/n", "http://999.1.1.1/n"}) {
            assertThrows(IllegalArgumentException.class, () -> NotificationCallbackService.validateCallbackUrl(internal), internal);
        }
    }

    @Test
    void theUrlLimitIsTheColumnSize() {
        String atLimit = "https://example.com/" + "a".repeat(NotificationCallbackService.MAX_URL_LENGTH - "https://example.com/".length());

        assertEquals(255, atLimit.length());
        assertEquals(atLimit, NotificationCallbackService.validateCallbackUrl(atLimit));
        assertThrows(IllegalArgumentException.class, () -> NotificationCallbackService.validateCallbackUrl(atLimit + "a"));
    }
}
