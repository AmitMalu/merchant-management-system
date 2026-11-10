package com.project2.ism.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project2.ism.Enum.BillAvenueApiType;
import com.project2.ism.Model.Bbps.BillAvenueConfig;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Repository.BillAvenueConfigRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.util.BillAvenueApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Re-loads the biller list (categories and providers) from Bill Avenue into bill_avenue_config,
 * using whichever environment (UAT / PROD) is active. Meant for an admin after switching environment.
 *
 * Safe by design: the default is a preview that changes nothing. Billers that Bill Avenue no longer
 * lists are only removed when explicitly asked for.
 */
@Service
public class BillerSyncService {

    private static final Logger log = LoggerFactory.getLogger(BillerSyncService.class);

    private final PaymentVendorRepository paymentVendorRepository;
    private final BillAvenueApiClient billAvenueApiClient;
    private final BillAvenueConfigRepository configRepository;
    private final PaymentVendorCredentialsService credentialsService;
    private final ObjectMapper objectMapper;

    public BillerSyncService(PaymentVendorRepository paymentVendorRepository,
                             BillAvenueApiClient billAvenueApiClient,
                             BillAvenueConfigRepository configRepository,
                             PaymentVendorCredentialsService credentialsService,
                             ObjectMapper objectMapper) {
        this.paymentVendorRepository = paymentVendorRepository;
        this.billAvenueApiClient = billAvenueApiClient;
        this.configRepository = configRepository;
        this.credentialsService = credentialsService;
        this.objectMapper = objectMapper;
    }

    static final int MAX_IDS_PER_REQUEST = 2000;
    static final int MAX_REQUESTS_PER_SYNC = 10;   // Bill Avenue allows 15 Biller Info requests a day
    private static final java.util.regex.Pattern BILLER_ID = java.util.regex.Pattern.compile("^[A-Za-z0-9]{14}$");

    private record Fetched(String id, String category, String name, String raw) {}

    /**
     * @param billerIds   only these billers; empty or null = the whole list
     * @param dryRun      true = report only, change nothing
     * @param removeStale true = also delete saved billers Bill Avenue did not return (whole-list sync only)
     */
    @Transactional
    public Map<String, Object> sync(List<String> billerIds, boolean dryRun, boolean removeStale) throws Exception {

        PaymentVendor vendor = paymentVendorRepository
                .findByVendorNameAndStatus("Bill Avenue", true)
                .orElseThrow(() -> new IllegalStateException("Active Bill Avenue vendor not found"));

        List<String> only = billerIds == null ? List.of()
                : billerIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        boolean wholeList = only.isEmpty();

        // Bill Avenue: a biller ID is exactly 14 letters / digits, at most 2000 IDs per request, and the
        // Biller Info API allows only 15 requests per day. Check everything before spending a request.
        List<String> badIds = only.stream().filter(id -> !BILLER_ID.matcher(id).matches()).toList();
        if (!badIds.isEmpty()) {
            throw new IllegalArgumentException("Biller IDs must be exactly 14 letters or digits. Not valid: "
                    + String.join(", ", badIds.stream().limit(5).toList()) + (badIds.size() > 5 ? " ..." : ""));
        }
        int requestCount = wholeList ? 1 : (only.size() + MAX_IDS_PER_REQUEST - 1) / MAX_IDS_PER_REQUEST;
        if (requestCount > MAX_REQUESTS_PER_SYNC) {
            throw new IllegalArgumentException("Too many biller IDs: " + only.size() + " would need " + requestCount
                    + " requests, and Bill Avenue allows only 15 per day. Send at most "
                    + (MAX_REQUESTS_PER_SYNC * MAX_IDS_PER_REQUEST) + " IDs at a time.");
        }

        String environment = credentialsService.getActiveEnvironmentCredentials(vendor.getId()).getActiveEnvironment();

        log.info("Biller sync | environment={} wholeList={} ids={} requests={} dryRun={} removeStale={}",
                environment, wholeList, only.size(), requestCount, dryRun, removeStale);

        // Nothing is saved until every request has been answered.
        List<List<String>> batches = new ArrayList<>();
        if (wholeList) {
            batches.add(List.of());     // "complete list" request: an empty biller list
        } else {
            for (int i = 0; i < only.size(); i += MAX_IDS_PER_REQUEST) {
                batches.add(only.subList(i, Math.min(only.size(), i + MAX_IDS_PER_REQUEST)));
            }
        }

        Map<String, Fetched> byId = new LinkedHashMap<>();
        for (List<String> batch : batches) {
            ObjectNode body = objectMapper.createObjectNode();
            var array = body.putArray("billerId");
            batch.forEach(array::add);

            JsonNode response;
            try {
                response = objectMapper.readTree(billAvenueApiClient.call(
                        vendor.getId(), BillAvenueApiType.BILLER_INFO, null, objectMapper.writeValueAsString(body)));
            } catch (RuntimeException ex) {
                String hint = wholeList
                        ? " Bill Avenue may not accept a request without biller IDs: paste the biller IDs to load instead."
                        : "";
                throw new IllegalStateException("Bill Avenue refused the request (" + ex.getMessage() + ")." + hint
                        + " Nothing was changed.");
            }

            List<Fetched> got = parse(response);
            if (got.isEmpty()) {
                String code = response.path("responseCode").asText("");
                JsonNode errorInfo = response.path("errorInfo");
                String msg = errorInfo.path("error").path(0).path("errorMessage").asText(
                        errorInfo.path(0).path("error").path("errorMessage").asText(""));
                throw new IllegalStateException("Bill Avenue returned no billers"
                        + (code.isEmpty() ? "" : " (code " + code + ")") + (msg.isEmpty() ? "" : ": " + msg)
                        + ". Nothing was changed.");
            }
            got.forEach(f -> byId.putIfAbsent(f.id(), f));
        }
        List<Fetched> fetched = new ArrayList<>(byId.values());

        Map<String, List<BillAvenueConfig>> saved = new LinkedHashMap<>();
        for (BillAvenueConfig c : configRepository.findByPaymentVendor_Id(vendor.getId())) {
            saved.computeIfAbsent(c.getProviderId(), k -> new ArrayList<>()).add(c);
        }

        int added = 0, updated = 0, unchanged = 0;
        List<BillAvenueConfig> toSave = new ArrayList<>();
        Set<String> returnedIds = new HashSet<>();
        Map<String, Integer> categories = new TreeMap<>();

        for (Fetched f : fetched) {
            returnedIds.add(f.id());
            categories.merge(f.category(), 1, Integer::sum);
            List<BillAvenueConfig> existing = saved.get(f.id());
            if (existing == null) {
                BillAvenueConfig c = new BillAvenueConfig();
                c.setPaymentVendor(vendor);
                apply(c, f);
                toSave.add(c);
                added++;
            } else {
                BillAvenueConfig c = existing.get(0);
                if (Objects.equals(c.getServiceName(), f.category()) && Objects.equals(c.getProviderName(), f.name())
                        && sameJson(c.getRawConfig(), f.raw())) {
                    unchanged++;
                } else {
                    apply(c, f);
                    toSave.add(c);
                    updated++;
                }
            }
        }

        // Saved billers that Bill Avenue did not return (only meaningful for a whole-list sync).
        List<BillAvenueConfig> stale = new ArrayList<>();
        if (wholeList) {
            saved.forEach((id, rows) -> { if (!returnedIds.contains(id)) stale.addAll(rows); });
        }
        long stagingLooking = stale.stream().filter(c -> c.getProviderId().toUpperCase().startsWith("DUMMY")).count();

        int removed = 0;
        if (!dryRun) {
            configRepository.saveAll(toSave);
            if (removeStale && wholeList && !stale.isEmpty()) {
                configRepository.deleteAll(stale);
                removed = stale.size();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("environment", environment);
        result.put("dryRun", dryRun);
        result.put("wholeList", wholeList);
        result.put("fetched", fetched.size());
        result.put("added", added);
        result.put("updated", updated);
        result.put("unchanged", unchanged);
        result.put("notInBillAvenueList", stale.size());
        result.put("notInBillAvenueListLookingLikeStaging", stagingLooking);
        result.put("notInBillAvenueListSample", stale.stream().limit(15)
                .map(c -> c.getProviderId() + " - " + c.getProviderName()).toList());
        result.put("removed", removed);
        result.put("categories", categories);
        return result;
    }

    /** The database may store the JSON with different spacing or key order; compare the content. */
    private boolean sameJson(String a, String b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        try {
            return objectMapper.readTree(a).equals(objectMapper.readTree(b));
        } catch (Exception ex) {
            return a.equals(b);
        }
    }

    private void apply(BillAvenueConfig c, Fetched f) {
        c.setServiceName(cut(f.category(), 100));
        c.setProviderId(cut(f.id(), 100));
        c.setProviderName(cut(f.name(), 200));
        c.setRawConfig(f.raw());
    }

    private String cut(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    /** The biller entries of an MDM answer: a list, or a single object. */
    private List<Fetched> parse(JsonNode response) {
        JsonNode node = response.path("biller");
        List<JsonNode> items = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(items::add);
        } else if (node.isObject()) {
            items.add(node);
        }

        Map<String, Fetched> byId = new LinkedHashMap<>();
        for (JsonNode b : items) {
            String id = b.path("billerId").asText("").trim();
            if (id.isEmpty()) {
                continue;
            }
            byId.putIfAbsent(id, new Fetched(id, b.path("billerCategory").asText("").trim(),
                    b.path("billerName").asText("").trim(), b.toString()));
        }
        return new ArrayList<>(byId.values());
    }
}
