package com.project2.ism.Controller;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.*;
import com.project2.ism.DTO.ReportDTO.ApiResponse;
import com.project2.ism.Service.CallerIdentityService;
import com.project2.ism.Service.MenuRulesService;
import com.project2.ism.Service.MenuRulesSwitch;
import com.project2.ism.Service.PaymentRoutingConfigService;
import com.project2.ism.Service.PaymentRoutingTrialService;
import com.project2.ism.Service.PayoutEngineSwitch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Vendor routing configuration. Admin / super admin only (see SecurityConfig), except /my-products, which
 * any logged-in merchant or franchise may read for itself.
 */
@RestController
@RequestMapping("/payment-routing")
public class PaymentRoutingController {

    private final PaymentRoutingConfigService service;
    private final CallerIdentityService callerIdentity;
    private final PaymentRoutingTrialService trial;
    private final PayoutEngineSwitch payoutEngine;
    private final MenuRulesSwitch menuRules;
    private final MenuRulesService menuService;

    public PaymentRoutingController(PaymentRoutingConfigService service, CallerIdentityService callerIdentity,
                                    PaymentRoutingTrialService trial, PayoutEngineSwitch payoutEngine,
                                    MenuRulesSwitch menuRules, MenuRulesService menuService) {
        this.service = service;
        this.callerIdentity = callerIdentity;
        this.trial = trial;
        this.payoutEngine = payoutEngine;
        this.menuRules = menuRules;
        this.menuService = menuService;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(new ApiResponse<>(true, message, data, null, LocalDateTime.now()));
    }

    // ---- which vendors serve which product ----

    @GetMapping("/product-vendors")
    public ResponseEntity<ApiResponse<List<ProductVendorDto>>> productVendors(
            @RequestParam(required = false) Long productId) {
        return ok("Product vendors fetched", service.listProductVendors(productId));
    }

    @PostMapping("/product-vendors")
    public ResponseEntity<ApiResponse<ProductVendorDto>> saveProductVendor(@RequestBody ProductVendorRequest request) {
        return ok("Saved", service.saveProductVendor(request));
    }

    @DeleteMapping("/product-vendors/{id}")
    public ResponseEntity<ApiResponse<Void>> removeProductVendor(@PathVariable Long id) {
        service.removeProductVendor(id);
        return ok("Removed", null);
    }

    // ---- routings ----

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoutingDto>>> list(@RequestParam(required = false) Long productId) {
        return ok("Routings fetched", service.listProfiles(productId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoutingDto>> get(@PathVariable Long id) {
        return ok("Routing fetched", service.getProfile(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoutingDto>> create(@RequestBody RoutingRequest request) {
        return ok("Routing created", service.createProfile(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoutingDto>> update(@PathVariable Long id, @RequestBody RoutingRequest request) {
        return ok("Routing updated", service.updateProfile(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<RoutingDto>> status(@PathVariable Long id, @RequestParam boolean value) {
        return ok(value ? "Routing switched on" : "Routing switched off", service.setProfileStatus(id, value));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.deleteProfile(id);
        return ok("Routing deleted", null);
    }

    // ---- who is assigned ----

    @GetMapping("/{id}/assignments")
    public ResponseEntity<ApiResponse<List<AssignmentDto>>> assignments(@PathVariable Long id) {
        return ok("Assignments fetched", service.listAssignments(id));
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> assign(@PathVariable Long id,
                                                                    @RequestBody AssignmentRequest request) {
        int added = service.addAssignments(id, request == null ? null : request.targets());
        return ok(added + " account(s) assigned", Map.of("added", added));
    }

    @PostMapping("/{id}/assignments/remove")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> unassign(@PathVariable Long id,
                                                                      @RequestBody AssignmentRequest request) {
        int removed = service.removeAssignments(id, request == null ? null : request.targets());
        return ok(removed + " account(s) removed", Map.of("removed", removed));
    }

    @GetMapping("/unassigned")
    public ResponseEntity<ApiResponse<List<TargetOption>>> unassigned(@RequestParam Long productId,
                                                                      @RequestParam String type) {
        return ok("Accounts without a routing", service.unassigned(productId, type));
    }

    // ---- explain, set up, history ----

    @GetMapping("/simulate")
    public ResponseEntity<ApiResponse<SimulationResult>> simulate(@RequestParam Long productId,
                                                                  @RequestParam String type,
                                                                  @RequestParam Long accountId,
                                                                  @RequestParam(required = false) BigDecimal amount,
                                                                  @RequestParam(required = false) String category) {
        return ok("Simulated", service.simulate(productId, type, accountId, amount, category));
    }

    @PostMapping("/seed-defaults")
    public ResponseEntity<ApiResponse<SeedResult>> seed(@RequestParam(defaultValue = "true") boolean dryRun,
                                                        @RequestBody SeedRequest request) {
        SeedResult result = service.seedDefaults(dryRun, request);
        return ok(dryRun ? "Preview" : "Default routing created", result);
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<Page<AuditDto>>> audit(@RequestParam(required = false) Long routingId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ok("History fetched", service.audit(routingId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    // ---- the payout engine: old flow, pilot accounts only, or the new flow for everyone ----

    @GetMapping("/payout-engine")
    public ResponseEntity<ApiResponse<PayoutEngineStatus>> payoutEngineStatus() {
        return ok("Payout engine", payoutEngine.status());
    }

    @PostMapping("/payout-engine")
    public ResponseEntity<ApiResponse<PayoutEngineStatus>> payoutEngineSave(@RequestBody PayoutEngineRequest request) {
        return ok("Payout engine saved", payoutEngine.save(request.mode(), request.pilotAccounts(), request.useRouting()));
    }

    @GetMapping("/payout-engine/attempts")
    public ResponseEntity<ApiResponse<Page<AttemptRow>>> payoutAttempts(@RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return ok("Attempts", payoutEngine.attempts(page, size));
    }

    // ---- the trial run: what routing WOULD choose on real payments (changes nothing) ----

    @GetMapping("/trial/status")
    public ResponseEntity<ApiResponse<TrialStatus>> trialStatus() {
        return ok("Trial run status", trial.status());
    }

    @PostMapping("/trial/enabled")
    public ResponseEntity<ApiResponse<TrialStatus>> trialEnabled(@RequestParam boolean value) {
        return ok(value ? "Trial run switched on" : "Trial run switched off", trial.setEnabled(value));
    }

    @GetMapping("/trial/summary")
    public ResponseEntity<ApiResponse<List<TrialSummaryRow>>> trialSummary() {
        return ok("Trial run summary", trial.summary());
    }

    @GetMapping("/trial/rows")
    public ResponseEntity<ApiResponse<Page<TrialRow>>> trialRows(@RequestParam(defaultValue = "false") boolean onlyDifferent,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ok("Trial run rows", trial.rows(onlyDifferent, page, size));
    }

    @DeleteMapping("/trial")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> trialClear() {
        int removed = trial.clearAll();
        return ok(removed + " row(s) cleared", Map.of("removed", removed));
    }

    // ---- menu rules (admin switch) ----

    @GetMapping("/menu-rules")
    public ResponseEntity<ApiResponse<MenuRulesStatus>> menuRulesStatus() {
        return ok("Menu rules", menuRules.status());
    }

    @PostMapping("/menu-rules")
    public ResponseEntity<ApiResponse<MenuRulesStatus>> menuRulesSave(@RequestBody MenuRulesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Choose the mode");
        }
        return ok("Saved", menuRules.save(request.mode(), request.accounts()));
    }

    /** For the sidebar: which menus this merchant / franchise sees. Admins: everything. */
    @GetMapping("/my-menu")
    public ResponseEntity<ApiResponse<MyMenu>> myMenu() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        return ok("Menus", admin ? new MyMenu(false, true, true, true) : menuService.myMenu(callerIdentity.currentCustomer()));
    }

    // ---- categories of a product, for the dropdowns ----

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> categories(@RequestParam Long productId) {
        return ok("Categories", service.categoriesFor(productId));
    }

    /** For the sidebar: the products this merchant / franchise has a routing for. Admins have none. */
    @GetMapping("/my-products")
    public ResponseEntity<ApiResponse<MyProducts>> myProducts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        return ok("Products fetched", service.myProducts(admin ? null : callerIdentity.currentCustomer()));
    }
}
