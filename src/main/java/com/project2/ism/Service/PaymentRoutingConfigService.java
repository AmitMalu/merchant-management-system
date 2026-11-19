package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.*;
import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Routing.PaymentProductVendor;
import com.project2.ism.Model.Routing.PaymentRoutingAssignment;
import com.project2.ism.Model.Routing.PaymentRoutingAudit;
import com.project2.ism.Model.Routing.PaymentRoutingProfile;
import com.project2.ism.Model.Routing.PaymentRoutingProfileVendor;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PaymentProductRepository;
import com.project2.ism.Repository.PaymentProductVendorRepository;
import com.project2.ism.Repository.PaymentRoutingAssignmentRepository;
import com.project2.ism.Repository.PaymentRoutingAuditRepository;
import com.project2.ism.Repository.PaymentRoutingProfileRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vendor routing configuration (admin / super admin only; see SecurityConfig).
 *
 * Phase 1: this only stores and checks the settings. No payment, charge or retry reads it yet, so saving a
 * routing changes nothing for any merchant. The simulator explains what the settings would decide.
 */
@Service
public class PaymentRoutingConfigService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRoutingConfigService.class);

    static final int MAX_VENDORS = 10;
    static final int DEFAULT_RETRY_COUNT = 3;
    static final int DEFAULT_RETRY_WAIT_SECONDS = 3;
    static final int MAX_RETRY_COUNT = 10;
    static final int MAX_RETRY_WAIT_SECONDS = 60;

    private final PaymentProductRepository productRepository;
    private final PaymentVendorRepository vendorRepository;
    private final PaymentProductVendorRepository linkRepository;
    private final PaymentRoutingProfileRepository profileRepository;
    private final PaymentRoutingAssignmentRepository assignmentRepository;
    private final PaymentRoutingAuditRepository auditRepository;
    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;

    // When true the sidebar will show Payout / Bill Payment only to accounts that have a routing. Off by
    // default: switched on only after the first-time setup has assigned everyone who uses them today.
    @Value("${routing.menu-gating-enabled:false}")
    private boolean gatingEnabled = false;

    @Value("${routing.payout-product-code:001}")
    private String payoutProductCode = "001";

    @Value("${routing.bill-payment-product-code:002}")
    private String billPaymentProductCode = "002";

    public PaymentRoutingConfigService(PaymentProductRepository productRepository,
                                       PaymentVendorRepository vendorRepository,
                                       PaymentProductVendorRepository linkRepository,
                                       PaymentRoutingProfileRepository profileRepository,
                                       PaymentRoutingAssignmentRepository assignmentRepository,
                                       PaymentRoutingAuditRepository auditRepository,
                                       MerchantRepository merchantRepository,
                                       FranchiseRepository franchiseRepository) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.linkRepository = linkRepository;
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditRepository = auditRepository;
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
    }

    // =====================================================================
    // Which vendors serve which product
    // =====================================================================

    @Transactional(readOnly = true)
    public List<ProductVendorDto> listProductVendors(Long productId) {
        return linkRepository.findAllWithDetails(productId).stream().map(this::toDto).toList();
    }

    @Transactional
    public ProductVendorDto saveProductVendor(ProductVendorRequest req) {
        if (req == null || req.productId() == null || req.vendorId() == null) {
            throw new IllegalArgumentException("Product and vendor are required");
        }
        PaymentProduct product = requireProduct(req.productId());
        PaymentVendor vendor = requireVendor(req.vendorId());
        boolean enabled = req.enabled() == null || req.enabled();

        PaymentProductVendor link = linkRepository.findByProductAndVendor(product.getId(), vendor.getId()).orElse(null);
        boolean isNew = link == null;
        if (isNew) {
            link = new PaymentProductVendor();
            link.setProduct(product);
            link.setVendor(vendor);
        } else if (!enabled && profileRepository.vendorUsedInProduct(product.getId(), vendor.getId())) {
            throw new IllegalArgumentException(vendor.getVendorName() + " is used by a routing of "
                    + product.getProductName() + ". Remove it from that routing first.");
        }
        link.setCategories(normalizeCategories(req.categories()));
        link.setEnabled(enabled);
        link = linkRepository.save(link);

        audit(null, product.getId(), isNew ? "LINK_ADDED" : "LINK_UPDATED",
                vendor.getVendorName() + " / " + product.getProductName()
                        + (link.getCategories() != null ? " / categories: " + link.getCategories() : "")
                        + (enabled ? "" : " / switched off"));
        return toDto(link);
    }

    @Transactional
    public void removeProductVendor(Long id) {
        PaymentProductVendor link = linkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        Long productId = link.getProduct().getId();
        Long vendorId = link.getVendor().getId();
        if (profileRepository.vendorUsedInProduct(productId, vendorId)) {
            throw new IllegalArgumentException(link.getVendor().getVendorName() + " is used by a routing of "
                    + link.getProduct().getProductName() + ". Remove it from that routing first.");
        }
        audit(null, productId, "LINK_REMOVED",
                link.getVendor().getVendorName() + " / " + link.getProduct().getProductName());
        linkRepository.delete(link);
    }

    // =====================================================================
    // Routings
    // =====================================================================

    @Transactional(readOnly = true)
    public List<RoutingDto> listProfiles(Long productId) {
        return profileRepository.findAllWithVendors(productId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RoutingDto getProfile(Long id) {
        return toDto(requireProfile(id));
    }

    @Transactional
    public RoutingDto createProfile(RoutingRequest req) {
        if (req == null || req.productId() == null) {
            throw new IllegalArgumentException("Product is required");
        }
        PaymentProduct product = requireProduct(req.productId());
        String name = cleanName(req.name());
        if (profileRepository.existsByProduct_IdAndNameIgnoreCase(product.getId(), name)) {
            throw new IllegalArgumentException("A routing named \"" + name + "\" already exists for "
                    + product.getProductName());
        }

        PaymentRoutingProfile profile = new PaymentRoutingProfile();
        profile.setProduct(product);
        profile.setName(name);
        profile.setStatus(req.status() == null || req.status());
        applySettings(profile, product, req);
        profile.setCreatedBy(actor());
        profile.setUpdatedBy(actor());

        profile = profileRepository.save(profile);
        audit(profile.getId(), product.getId(), "ROUTING_CREATED", describe(profile));
        return toDto(profile);
    }

    @Transactional
    public RoutingDto updateProfile(Long id, RoutingRequest req) {
        PaymentRoutingProfile profile = requireProfile(id);
        if (req == null) {
            throw new IllegalArgumentException("Nothing to save");
        }
        if (req.productId() != null && !req.productId().equals(profile.getProduct().getId())) {
            throw new IllegalArgumentException("The product of a routing cannot be changed. Create a new routing instead.");
        }
        String name = cleanName(req.name());
        if (profileRepository.existsByProduct_IdAndNameIgnoreCaseAndIdNot(profile.getProduct().getId(), name, id)) {
            throw new IllegalArgumentException("A routing named \"" + name + "\" already exists for "
                    + profile.getProduct().getProductName());
        }

        String before = describe(profile);
        profile.setName(name);
        if (req.status() != null) {
            profile.setStatus(req.status());
        }
        applySettings(profile, profile.getProduct(), req);
        profile.setUpdatedBy(actor());

        profile = profileRepository.save(profile);
        audit(profile.getId(), profile.getProduct().getId(), "ROUTING_UPDATED",
                "Before: " + before + " | After: " + describe(profile));
        return toDto(profile);
    }

    @Transactional
    public RoutingDto setProfileStatus(Long id, boolean status) {
        PaymentRoutingProfile profile = requireProfile(id);
        profile.setStatus(status);
        profile.setUpdatedBy(actor());
        profile = profileRepository.save(profile);
        audit(profile.getId(), profile.getProduct().getId(), status ? "ROUTING_ON" : "ROUTING_OFF", profile.getName());
        return toDto(profile);
    }

    @Transactional
    public void deleteProfile(Long id) {
        PaymentRoutingProfile profile = requireProfile(id);
        long assigned = assignmentRepository.countByProfile_Id(id);
        if (assigned > 0) {
            throw new IllegalArgumentException(assigned + " account(s) are assigned to this routing. "
                    + "Remove them from it first, so nobody loses the product by mistake.");
        }
        audit(profile.getId(), profile.getProduct().getId(), "ROUTING_DELETED", describe(profile));
        profileRepository.delete(profile);
    }

    // =====================================================================
    // Who is assigned
    // =====================================================================

    @Transactional(readOnly = true)
    public List<AssignmentDto> listAssignments(Long profileId) {
        requireProfile(profileId);
        List<PaymentRoutingAssignment> rows = assignmentRepository.findByProfile_IdOrderByIdDesc(profileId);
        Map<Long, String> merchants = merchantNames(rows.stream()
                .filter(a -> CustomerRef.MERCHANT.equals(a.getTargetType())).map(PaymentRoutingAssignment::getTargetId).toList());
        Map<Long, String> franchises = franchiseNames(rows.stream()
                .filter(a -> CustomerRef.FRANCHISE.equals(a.getTargetType())).map(PaymentRoutingAssignment::getTargetId).toList());
        return rows.stream().map(a -> new AssignmentDto(a.getTargetType(), a.getTargetId(),
                (CustomerRef.MERCHANT.equals(a.getTargetType()) ? merchants : franchises)
                        .getOrDefault(a.getTargetId(), "#" + a.getTargetId()),
                a.getCreatedAt())).toList();
    }

    /** Assigns accounts to a routing. All or nothing: an account that already has another routing for this product stops it. */
    @Transactional
    public int addAssignments(Long profileId, List<TargetRef> targets) {
        PaymentRoutingProfile profile = requireProfile(profileId);
        List<TargetRef> wanted = distinctTargets(targets);
        if (wanted.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one merchant or franchise");
        }
        Long productId = profile.getProduct().getId();

        Map<String, String> names = targetNames(wanted);
        List<String> conflicts = new ArrayList<>();
        List<PaymentRoutingAssignment> toSave = new ArrayList<>();
        for (TargetRef t : wanted) {
            String key = key(t);
            if (!names.containsKey(key)) {
                throw new IllegalArgumentException("No " + t.type().toLowerCase() + " with id " + t.id());
            }
            PaymentRoutingAssignment existing = assignmentRepository.findForAccount(productId, t.type(), t.id()).orElse(null);
            if (existing == null) {
                PaymentRoutingAssignment a = new PaymentRoutingAssignment();
                a.setProfile(profile);
                a.setProduct(profile.getProduct());
                a.setTargetType(t.type());
                a.setTargetId(t.id());
                a.setCreatedBy(actor());
                toSave.add(a);
            } else if (!existing.getProfile().getId().equals(profileId)) {
                conflicts.add(names.get(key) + " is already assigned to \"" + existing.getProfile().getName() + "\"");
            }
        }
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", conflicts.stream().limit(5).toList())
                    + (conflicts.size() > 5 ? " (and " + (conflicts.size() - 5) + " more)" : "")
                    + ". Remove them from that routing first.");
        }
        assignmentRepository.saveAll(toSave);
        if (!toSave.isEmpty()) {
            audit(profileId, productId, "ASSIGNED", toSave.size() + " account(s) to \"" + profile.getName() + "\": "
                    + sample(toSave.stream().map(a -> names.get(key(new TargetRef(a.getTargetType(), a.getTargetId())))).toList()));
        }
        return toSave.size();
    }

    @Transactional
    public int removeAssignments(Long profileId, List<TargetRef> targets) {
        PaymentRoutingProfile profile = requireProfile(profileId);
        List<TargetRef> wanted = distinctTargets(targets);
        Long productId = profile.getProduct().getId();
        Map<String, String> names = targetNames(wanted);
        int removed = 0;
        List<String> removedNames = new ArrayList<>();
        for (TargetRef t : wanted) {
            PaymentRoutingAssignment a = assignmentRepository.findForAccount(productId, t.type(), t.id()).orElse(null);
            if (a != null && a.getProfile().getId().equals(profileId)) {
                assignmentRepository.delete(a);
                removed++;
                removedNames.add(names.getOrDefault(key(t), "#" + t.id()));
            }
        }
        if (removed > 0) {
            audit(profileId, productId, "UNASSIGNED", removed + " account(s) from \"" + profile.getName() + "\": " + sample(removedNames));
        }
        return removed;
    }

    /** Merchants (or franchises) that have no routing for this product yet. */
    @Transactional(readOnly = true)
    public List<TargetOption> unassigned(Long productId, String type) {
        requireProduct(productId);
        String t = normalizeType(type);
        Set<Long> assigned = assignmentRepository.findByProductAndType(productId, t).stream()
                .map(PaymentRoutingAssignment::getTargetId).collect(Collectors.toSet());
        List<TargetOption> out = new ArrayList<>();
        if (CustomerRef.MERCHANT.equals(t)) {
            for (Merchant m : merchantRepository.findAll()) {
                if (!assigned.contains(m.getId())) {
                    out.add(new TargetOption(t, m.getId(), merchantName(m)));
                }
            }
        } else {
            for (Franchise f : franchiseRepository.findAll()) {
                if (!assigned.contains(f.getId())) {
                    out.add(new TargetOption(t, f.getId(), franchiseName(f)));
                }
            }
        }
        out.sort(Comparator.comparing(o -> o.name().toLowerCase()));
        return out;
    }

    // =====================================================================
    // "Which vendor would be chosen?" (explains the settings; nothing is reserved or changed)
    // =====================================================================

    @Transactional(readOnly = true)
    public SimulationResult simulate(Long productId, String type, Long accountId, BigDecimal amount, String category) {
        PaymentProduct product = requireProduct(productId);
        String t = normalizeType(type);
        if (accountId == null) {
            throw new IllegalArgumentException("Choose the merchant or franchise");
        }
        String accountName = targetNames(List.of(new TargetRef(t, accountId))).get(key(new TargetRef(t, accountId)));
        if (accountName == null) {
            throw new IllegalArgumentException("No " + t.toLowerCase() + " with id " + accountId);
        }

        PaymentRoutingAssignment assignment = assignmentRepository.findForAccount(productId, t, accountId).orElse(null);
        if (assignment == null) {
            return new SimulationResult(accountName, null, null, List.of(), null,
                    accountName + " has no routing for " + product.getProductName()
                            + ". When the menu rule is on, this account will not see the product.");
        }
        PaymentRoutingProfile profile = profileRepository.findByIdWithVendors(assignment.getProfile().getId()).orElseThrow();
        if (!Boolean.TRUE.equals(profile.getStatus())) {
            return new SimulationResult(accountName, profile.getId(), profile.getName(), List.of(), null,
                    "Routing \"" + profile.getName() + "\" is switched off, so no vendor would be used.");
        }

        String cat = category == null ? "" : category.trim();
        List<SimulationVendor> rows = new ArrayList<>();
        String chosen = null;
        for (PaymentRoutingProfileVendor pv : profile.getVendors()) {
            PaymentVendor vendor = pv.getVendor();
            String reason = ineligibleReason(productId, pv, vendor, amount, cat);
            boolean eligible = reason == null;
            if (eligible && chosen == null) {
                chosen = vendor.getVendorName();
            }
            rows.add(new SimulationVendor(vendor.getId(), vendor.getVendorName(), pv.getPriority(), eligible,
                    eligible ? "Can serve this" : reason));
        }
        String message = chosen != null
                ? "Would use " + chosen + ". (This check does not look at what each vendor has taken today, this month or this year, at vendor health or at vendor-wise charges. Real payouts check the daily, monthly and yearly limits when \"Use routing for payouts\" is on.)"
                : "No vendor can serve this right now: the payment would be refused.";
        return new SimulationResult(accountName, profile.getId(), profile.getName(), rows, chosen, message);
    }

    /**
     * The vendor routing would choose for one payment (the same rules as the simulator), for the trial run.
     * Read-only. Never throws for a missing routing: it answers "no routing" instead.
     */
    @Transactional(readOnly = true)
    public Decision decide(PaymentProduct product, String type, Long accountId, BigDecimal amount, String category) {
        String t = normalizeType(type);
        PaymentRoutingAssignment assignment = assignmentRepository.findForAccount(product.getId(), t, accountId).orElse(null);
        if (assignment == null) {
            return new Decision(null, null, null, null, true, "No routing assigned to this account");
        }
        PaymentRoutingProfile profile = profileRepository.findByIdWithVendors(assignment.getProfile().getId()).orElse(null);
        if (profile == null) {
            return new Decision(null, null, null, null, true, "Routing not found");
        }
        if (!Boolean.TRUE.equals(profile.getStatus())) {
            return new Decision(profile.getId(), profile.getName(), null, null, true, "Routing is switched off");
        }
        String cat = category == null ? "" : category.trim();
        PaymentVendor chosen = null;
        List<String> lines = new ArrayList<>();
        for (PaymentRoutingProfileVendor pv : profile.getVendors()) {
            String reason = ineligibleReason(product.getId(), pv, pv.getVendor(), amount, cat);
            if (reason == null && chosen == null) {
                chosen = pv.getVendor();
            }
            lines.add(pv.getPriority() + ". " + pv.getVendor().getVendorName() + ": " + (reason == null ? "can serve" : reason));
        }
        String summary = String.join("; ", lines);
        return new Decision(profile.getId(), profile.getName(), chosen != null ? chosen.getId() : null,
                chosen != null ? chosen.getVendorName() : null, false, summary);
    }

    /**
     * The vendors, in order, that may serve this one payment according to the account's routing (the same rules as
     * the simulator: linked, active, switched on, within the vendor's min and max). Daily limits are applied by the
     * caller, which knows what each vendor has taken today. Read-only. A missing or switched-off routing answers
     * hasRouting=false, so the caller keeps using its fixed vendor.
     */
    @Transactional(readOnly = true)
    public RoutePlan routePlan(PaymentProduct product, String type, Long accountId, BigDecimal amount, String category) {
        String t = normalizeType(type);
        PaymentRoutingAssignment assignment = assignmentRepository.findForAccount(product.getId(), t, accountId).orElse(null);
        if (assignment == null) {
            return new RoutePlan(false, null, null, 1, 0, List.of(), List.of("No routing assigned to this account"));
        }
        PaymentRoutingProfile profile = profileRepository.findByIdWithVendors(assignment.getProfile().getId()).orElse(null);
        if (profile == null || !Boolean.TRUE.equals(profile.getStatus())) {
            return new RoutePlan(false, profile == null ? null : profile.getId(), profile == null ? null : profile.getName(),
                    1, 0, List.of(), List.of(profile == null ? "Routing not found" : "Routing is switched off"));
        }
        String cat = category == null ? "" : category.trim();
        List<RoutePlanVendor> vendors = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<RoutePlanRefusal> refusals = new ArrayList<>();
        for (PaymentRoutingProfileVendor pv : profile.getVendors()) {
            String reason = ineligibleReason(product.getId(), pv, pv.getVendor(), amount, cat);
            if (reason == null) {
                vendors.add(new RoutePlanVendor(pv.getVendor().getId(), pv.getVendor().getVendorName(), pv.getPriority(),
                        pv.getDailyTransactionLimit(), pv.getDailyAmountLimit(),
                        pv.getMonthlyTransactionLimit(), pv.getMonthlyAmountLimit(),
                        pv.getYearlyTransactionLimit(), pv.getYearlyAmountLimit()));
            } else {
                notes.add(pv.getPriority() + ". " + pv.getVendor().getVendorName() + ": " + reason);
                refusals.add(new RoutePlanRefusal(pv.getPriority(), publicReason(reason, pv)));
            }
        }
        int retries = profile.getRetryCount() == null ? 1 : Math.max(1, profile.getRetryCount());
        int wait = profile.getRetryWaitSeconds() == null ? 0 : Math.max(0, profile.getRetryWaitSeconds());
        return new RoutePlan(true, profile.getId(), profile.getName(), retries, wait, vendors, notes, refusals);
    }

    /**
     * The reason in words that are safe to show to a merchant: the allowed amounts are explained, anything else
     * (a vendor that is not linked, inactive or switched off) is only "not available right now". null = generic.
     */
    private static String publicReason(String reason, PaymentRoutingProfileVendor pv) {
        if (reason.startsWith("Amount is below") && pv.getMinAmount() != null) {
            return "Amount is below the minimum allowed ("
                    + pv.getMinAmount().stripTrailingZeros().toPlainString() + ").";
        }
        if (reason.startsWith("Amount is above") && pv.getMaxAmount() != null) {
            return "Amount is above the maximum allowed ("
                    + pv.getMaxAmount().stripTrailingZeros().toPlainString() + ").";
        }
        return null;
    }

    /** Does the account have a routing for this product that is switched on? */
    @Transactional(readOnly = true)
    public boolean hasActiveRouting(PaymentProduct product, String type, Long accountId) {
        PaymentRoutingAssignment a = assignmentRepository
                .findForAccount(product.getId(), normalizeType(type), accountId).orElse(null);
        return a != null && Boolean.TRUE.equals(a.getProfile().getStatus());
    }

    // =====================================================================
    // Categories of a product (for the dropdowns: nobody types a category)
    // =====================================================================

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.project2.ism.Repository.BillAvenueConfigRepository billerRepository;

    /**
     * The categories an admin can pick for a product, sorted. For Bill Payment: the service names of the Bill
     * Avenue billers, plus any category already saved on a vendor link so that nothing saved earlier disappears.
     * A product without categories (Payout) answers an empty list.
     */
    @Transactional(readOnly = true)
    public List<String> categoriesFor(Long productId) {
        PaymentProduct product = requireProduct(productId);
        java.util.TreeSet<String> out = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (billPaymentProductCode.equals(product.getProductCode()) && billerRepository != null) {
            for (String s : billerRepository.findDistinctServicesByVendorName("Bill Avenue")) {
                if (s != null && !s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        for (ProductVendorDto l : listProductVendors(productId)) {
            if (l.categories() != null) {
                for (String c : l.categories().split(",")) {
                    if (!c.isBlank()) {
                        out.add(c.trim());
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    private String ineligibleReason(Long productId, PaymentRoutingProfileVendor pv, PaymentVendor vendor,
                                    BigDecimal amount, String category) {
        PaymentProductVendor link = linkRepository.findByProductAndVendor(productId, vendor.getId()).orElse(null);
        if (link == null || !Boolean.TRUE.equals(link.getEnabled())) {
            return "Not linked to this product (or switched off in Product Vendors)";
        }
        if (!Boolean.TRUE.equals(vendor.getStatus())) {
            return "The vendor is inactive";
        }
        if (!Boolean.TRUE.equals(pv.getEnabled())) {
            return "Switched off in this routing";
        }
        if (!category.isEmpty() && !servesCategory(link.getCategories(), category)) {
            return "Does not serve the category " + category;
        }
        if (amount != null) {
            if (pv.getMinAmount() != null && amount.compareTo(pv.getMinAmount()) < 0) {
                return "Amount is below this vendor's minimum (" + pv.getMinAmount().stripTrailingZeros().toPlainString() + ")";
            }
            if (pv.getMaxAmount() != null && amount.compareTo(pv.getMaxAmount()) > 0) {
                return "Amount is above this vendor's maximum (" + pv.getMaxAmount().stripTrailingZeros().toPlainString() + ")";
            }
        }
        return null;
    }

    static boolean servesCategory(String categories, String category) {
        if (categories == null || categories.isBlank()) {
            return true;      // empty = every category
        }
        for (String c : categories.split(",")) {
            if (c.trim().equalsIgnoreCase(category.trim())) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // What an account has (for the sidebar, once the menu rule is on)
    // =====================================================================

    @Transactional(readOnly = true)
    public MyProducts myProducts(CustomerRef who) {
        List<MyProduct> products = new ArrayList<>();
        if (who != null && who.id() != null) {
            for (PaymentRoutingAssignment a : assignmentRepository.findAllForAccount(who.type(), who.id())) {
                if (Boolean.TRUE.equals(a.getProfile().getStatus()) && Boolean.TRUE.equals(a.getProduct().getStatus())) {
                    products.add(new MyProduct(a.getProduct().getId(), a.getProduct().getProductCode(),
                            a.getProduct().getProductName()));
                }
            }
        }
        return new MyProducts(gatingEnabled, products);
    }

    // =====================================================================
    // First-time setup: link the vendors in use today, create a default routing, assign who uses them now
    // =====================================================================

    /**
     * Creates, for each product the admin chose vendors for, the vendor links, a "<product> - Default" routing
     * with those vendors in that order, and assigns the accounts that use the product today. The vendors are
     * never picked here: Vendor 1 and the order are the admin's decision.
     */
    @Transactional
    public SeedResult seedDefaults(boolean dryRun, SeedRequest request) {
        if (request == null || request.products() == null || request.products().isEmpty()) {
            throw new IllegalArgumentException("Choose Vendor 1 for at least one product");
        }
        List<String> lines = new ArrayList<>();
        int[] counts = new int[3];       // links, routings, assignments
        boolean any = false;

        for (SeedProductChoice choice : request.products()) {
            if (choice == null || choice.productId() == null) {
                continue;
            }
            PaymentProduct product = requireProduct(choice.productId());
            List<SeedVendorChoice> chosen = choice.vendors() == null ? List.<SeedVendorChoice>of()
                    : choice.vendors().stream().filter(v -> v != null && v.vendorId() != null).toList();
            if (chosen.isEmpty()) {
                lines.add(product.getProductName() + ": no vendor chosen, skipped.");
                continue;
            }

            Set<Long> seen = new HashSet<>();
            List<SeedVendor> vendors = new ArrayList<>();
            for (SeedVendorChoice c : chosen) {
                if (!seen.add(c.vendorId())) {
                    throw new IllegalArgumentException(product.getProductName() + ": the same vendor is chosen more than once");
                }
                PaymentVendor vendor = requireVendor(c.vendorId());
                if (!Boolean.TRUE.equals(vendor.getStatus())) {
                    throw new IllegalArgumentException(product.getProductName() + ": " + vendor.getVendorName() + " is inactive");
                }
                vendors.add(new SeedVendor(vendor, normalizeCategories(c.categories())));
            }
            any = true;
            seedProduct(product, product.getProductName() + " - Default", vendors, seedTargets(product), dryRun, lines, counts);
        }
        if (!any) {
            throw new IllegalArgumentException("Choose Vendor 1 for at least one product");
        }
        if (dryRun) {
            lines.add("This was only a preview: nothing was saved.");
        }
        return new SeedResult(dryRun, lines, counts[0], counts[1], counts[2]);
    }

    /** The accounts that use a product today. Only Payout and Bill Payment are known; for any other product the admin assigns. */
    private List<TargetRef> seedTargets(PaymentProduct product) {
        List<TargetRef> targets = new ArrayList<>();
        String code = product.getProductCode();
        if (payoutProductCode.equals(code)) {
            for (Merchant m : merchantRepository.findAll()) {
                if (Boolean.TRUE.equals(m.getPayout())) {
                    targets.add(new TargetRef(CustomerRef.MERCHANT, m.getId()));
                }
            }
            for (Franchise f : franchiseRepository.findAll()) {
                // empty = franchise from before the switch existed: payout works for it
                if (f.getPayout() == null || Boolean.TRUE.equals(f.getPayout())) {
                    targets.add(new TargetRef(CustomerRef.FRANCHISE, f.getId()));
                }
            }
        } else if (billPaymentProductCode.equals(code)) {
            for (Merchant m : merchantRepository.findAll()) {
                targets.add(new TargetRef(CustomerRef.MERCHANT, m.getId()));
            }
            for (Franchise f : franchiseRepository.findAll()) {
                if (Boolean.TRUE.equals(f.getCreditCardBillPayment())) {
                    targets.add(new TargetRef(CustomerRef.FRANCHISE, f.getId()));
                }
            }
        }
        return targets;
    }

    private record SeedVendor(PaymentVendor vendor, String categories) {
    }

    private void seedProduct(PaymentProduct product, String profileName, List<SeedVendor> vendors,
                             List<TargetRef> targets, boolean dryRun, List<String> lines, int[] counts) {
        String label = product.getProductName();

        // links
        for (SeedVendor sv : vendors) {
            if (linkRepository.findByProductAndVendor(product.getId(), sv.vendor().getId()).isEmpty()) {
                counts[0]++;
                lines.add(label + ": link vendor " + sv.vendor().getVendorName()
                        + (sv.categories() != null ? " (category " + sv.categories() + " only)" : " (all categories)"));
                if (!dryRun) {
                    PaymentProductVendor link = new PaymentProductVendor();
                    link.setProduct(product);
                    link.setVendor(sv.vendor());
                    link.setCategories(sv.categories());
                    link.setEnabled(true);
                    linkRepository.save(link);
                }
            }
        }

        // the default routing
        List<PaymentRoutingProfile> existing = profileRepository.findAllWithVendors(product.getId());
        PaymentRoutingProfile target = existing.stream()
                .filter(p -> p.getName().equalsIgnoreCase(profileName)).findFirst().orElse(null);
        if (target != null) {
            lines.add(label + ": \"" + profileName + "\" already exists, so its vendors and order are not changed "
                    + "(edit them on the Routings tab).");
        }
        if (target == null && !existing.isEmpty()) {
            lines.add(label + ": already has its own routing but not \"" + profileName
                    + "\", so no accounts were assigned. Assign them on the Routings tab.");
            return;
        }
        if (target == null) {
            counts[1]++;
            lines.add(label + ": create routing \"" + profileName + "\" with vendors "
                    + vendors.stream().map(v -> v.vendor().getVendorName()).collect(Collectors.joining(" > ")));
            if (!dryRun) {
                target = new PaymentRoutingProfile();
                target.setProduct(product);
                target.setName(profileName);
                target.setStatus(true);
                target.setCreatedBy(actor());
                target.setUpdatedBy(actor());
                int priority = 1;
                for (SeedVendor sv : vendors) {
                    PaymentRoutingProfileVendor pv = new PaymentRoutingProfileVendor();
                    pv.setVendor(sv.vendor());
                    pv.setPriority(priority++);
                    pv.setEnabled(true);
                    target.addVendor(pv);
                }
                target = profileRepository.save(target);
                audit(target.getId(), product.getId(), "SEED_ROUTING", describe(target));
            }
        }

        // the accounts that have the product today
        Set<String> already = new HashSet<>();
        for (String type : List.of(CustomerRef.MERCHANT, CustomerRef.FRANCHISE)) {
            assignmentRepository.findByProductAndType(product.getId(), type)
                    .forEach(a -> already.add(a.getTargetType() + ":" + a.getTargetId()));
        }
        List<TargetRef> fresh = distinctTargets(targets).stream().filter(t -> !already.contains(key(t))).toList();
        if (!fresh.isEmpty()) {
            long merchants = fresh.stream().filter(t -> CustomerRef.MERCHANT.equals(t.type())).count();
            counts[2] += fresh.size();
            lines.add(label + ": assign " + fresh.size() + " account(s) (" + merchants + " merchants, "
                    + (fresh.size() - merchants) + " franchises) to \"" + profileName + "\"");
            if (!dryRun && target != null) {
                List<PaymentRoutingAssignment> rows = new ArrayList<>();
                for (TargetRef t : fresh) {
                    PaymentRoutingAssignment a = new PaymentRoutingAssignment();
                    a.setProfile(target);
                    a.setProduct(product);
                    a.setTargetType(t.type());
                    a.setTargetId(t.id());
                    a.setCreatedBy(actor());
                    rows.add(a);
                }
                assignmentRepository.saveAll(rows);
                audit(target.getId(), product.getId(), "SEED_ASSIGNED", fresh.size() + " account(s) assigned");
            }
        } else if (targets.isEmpty()) {
            lines.add(label + ": no accounts are assigned automatically for this product. Assign them on the Routings tab.");
        } else {
            lines.add(label + ": every account that uses it is already assigned.");
        }
    }

    // =====================================================================
    // History
    // =====================================================================

    @Transactional(readOnly = true)
    public Page<AuditDto> audit(Long profileId, Pageable pageable) {
        return auditRepository.search(profileId, pageable).map(a ->
                new AuditDto(a.getId(), a.getProfileId(), a.getProductId(), a.getAction(), a.getActor(),
                        a.getDetails(), a.getCreatedAt()));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private void applySettings(PaymentRoutingProfile profile, PaymentProduct product, RoutingRequest req) {
        int retry = req.retryCount() == null ? DEFAULT_RETRY_COUNT : req.retryCount();
        int wait = req.retryWaitSeconds() == null ? DEFAULT_RETRY_WAIT_SECONDS : req.retryWaitSeconds();
        if (retry < 0 || retry > MAX_RETRY_COUNT) {
            throw new IllegalArgumentException("Retries must be between 0 and " + MAX_RETRY_COUNT);
        }
        if (wait < 0 || wait > MAX_RETRY_WAIT_SECONDS) {
            throw new IllegalArgumentException("Wait between attempts must be between 0 and " + MAX_RETRY_WAIT_SECONDS + " seconds");
        }

        List<RoutingVendorRequest> vendors = req.vendors();
        if (vendors == null || vendors.isEmpty() || vendors.get(0) == null || vendors.get(0).vendorId() == null) {
            throw new IllegalArgumentException("Vendor 1 is required");
        }
        if (vendors.size() > MAX_VENDORS) {
            throw new IllegalArgumentException("A routing can have at most " + MAX_VENDORS + " vendors");
        }

        Set<Long> seen = new HashSet<>();
        List<PaymentRoutingProfileVendor> built = new ArrayList<>();
        for (int i = 0; i < vendors.size(); i++) {
            RoutingVendorRequest rv = vendors.get(i);
            String slot = "Vendor " + (i + 1);
            if (rv == null || rv.vendorId() == null) {
                throw new IllegalArgumentException(slot + " is not selected. Remove the empty row.");
            }
            if (!seen.add(rv.vendorId())) {
                throw new IllegalArgumentException(slot + ": the same vendor is chosen more than once");
            }
            PaymentVendor vendor = requireVendor(rv.vendorId());
            if (!Boolean.TRUE.equals(vendor.getStatus())) {
                throw new IllegalArgumentException(slot + " (" + vendor.getVendorName() + ") is inactive");
            }
            PaymentProductVendor link = linkRepository.findByProductAndVendor(product.getId(), vendor.getId()).orElse(null);
            if (link == null) {
                throw new IllegalArgumentException(slot + " (" + vendor.getVendorName() + ") is not linked to "
                        + product.getProductName() + ". Link it first in Product Vendors.");
            }
            if (!Boolean.TRUE.equals(link.getEnabled())) {
                throw new IllegalArgumentException(slot + " (" + vendor.getVendorName() + ") is switched off for "
                        + product.getProductName() + " in Product Vendors");
            }
            boolean enabled = rv.enabled() == null || rv.enabled();
            if (i == 0 && !enabled) {
                throw new IllegalArgumentException("Vendor 1 must be switched on");
            }
            BigDecimal min = rv.minAmount();
            BigDecimal max = rv.maxAmount();
            if (min != null && min.signum() < 0) {
                throw new IllegalArgumentException(slot + ": minimum amount cannot be negative");
            }
            if (max != null && max.signum() <= 0) {
                throw new IllegalArgumentException(slot + ": maximum amount must be more than zero");
            }
            if (min != null && max != null && min.compareTo(max) >= 0) {
                throw new IllegalArgumentException(slot + ": minimum amount must be less than the maximum");
            }
            requireCountLimit(slot, "daily", rv.dailyTransactionLimit());
            requireAmountLimit(slot, "daily", rv.dailyAmountLimit());
            requireCountLimit(slot, "monthly", rv.monthlyTransactionLimit());
            requireAmountLimit(slot, "monthly", rv.monthlyAmountLimit());
            requireCountLimit(slot, "yearly", rv.yearlyTransactionLimit());
            requireAmountLimit(slot, "yearly", rv.yearlyAmountLimit());
            // A longer period cannot allow less than a shorter one: it would never be reached.
            requireNotSmaller(slot, "monthly", "daily", rv.monthlyTransactionLimit(), rv.dailyTransactionLimit());
            requireNotSmaller(slot, "yearly", "monthly", rv.yearlyTransactionLimit(), rv.monthlyTransactionLimit());
            requireNotSmaller(slot, "yearly", "daily", rv.yearlyTransactionLimit(), rv.dailyTransactionLimit());
            requireNotSmaller(slot, "monthly", "daily", rv.monthlyAmountLimit(), rv.dailyAmountLimit());
            requireNotSmaller(slot, "yearly", "monthly", rv.yearlyAmountLimit(), rv.monthlyAmountLimit());
            requireNotSmaller(slot, "yearly", "daily", rv.yearlyAmountLimit(), rv.dailyAmountLimit());

            PaymentRoutingProfileVendor pv = new PaymentRoutingProfileVendor();
            pv.setVendor(vendor);
            pv.setPriority(i + 1);
            pv.setEnabled(enabled);
            pv.setMinAmount(min);
            pv.setMaxAmount(max);
            pv.setDailyTransactionLimit(rv.dailyTransactionLimit());
            pv.setDailyAmountLimit(rv.dailyAmountLimit());
            pv.setMonthlyTransactionLimit(rv.monthlyTransactionLimit());
            pv.setMonthlyAmountLimit(rv.monthlyAmountLimit());
            pv.setYearlyTransactionLimit(rv.yearlyTransactionLimit());
            pv.setYearlyAmountLimit(rv.yearlyAmountLimit());
            built.add(pv);
        }

        profile.setRetryCount(retry);
        profile.setRetryWaitSeconds(wait);
        profile.getVendors().clear();
        built.forEach(profile::addVendor);
    }

    private static void requireCountLimit(String slot, String period, Integer limit) {
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException(slot + ": " + period
                    + " transaction limit must be at least 1 (or empty for no limit)");
        }
    }

    private static void requireAmountLimit(String slot, String period, BigDecimal limit) {
        if (limit != null && limit.signum() <= 0) {
            throw new IllegalArgumentException(slot + ": " + period
                    + " amount limit must be more than zero (or empty for no limit)");
        }
    }

    /** When both are set, the longer period must allow at least as much as the shorter one. */
    private static <T extends Comparable<T>> void requireNotSmaller(String slot, String longer, String shorter,
                                                                     T longerLimit, T shorterLimit) {
        if (longerLimit != null && shorterLimit != null && longerLimit.compareTo(shorterLimit) < 0) {
            throw new IllegalArgumentException(slot + ": the " + longer + " limit cannot be smaller than the "
                    + shorter + " limit");
        }
    }

    private String describe(PaymentRoutingProfile p) {
        String vendors = p.getVendors().stream()
                .sorted(Comparator.comparing(PaymentRoutingProfileVendor::getPriority))
                .map(v -> v.getPriority() + ". " + v.getVendor().getVendorName()
                        + (Boolean.TRUE.equals(v.getEnabled()) ? "" : " (off)")
                        + limits(v))
                .collect(Collectors.joining(", "));
        return "\"" + p.getName() + "\" [" + (Boolean.TRUE.equals(p.getStatus()) ? "on" : "off") + "] vendors: " + vendors
                + "; retries " + p.getRetryCount() + " x " + p.getRetryWaitSeconds() + "s";
    }

    private String limits(PaymentRoutingProfileVendor v) {
        List<String> parts = new ArrayList<>();
        if (v.getMinAmount() != null) parts.add("min " + v.getMinAmount().stripTrailingZeros().toPlainString());
        if (v.getMaxAmount() != null) parts.add("max " + v.getMaxAmount().stripTrailingZeros().toPlainString());
        if (v.getDailyTransactionLimit() != null) parts.add("daily count " + v.getDailyTransactionLimit());
        if (v.getDailyAmountLimit() != null) parts.add("daily amount " + v.getDailyAmountLimit().stripTrailingZeros().toPlainString());
        if (v.getMonthlyTransactionLimit() != null) parts.add("monthly count " + v.getMonthlyTransactionLimit());
        if (v.getMonthlyAmountLimit() != null) parts.add("monthly amount " + v.getMonthlyAmountLimit().stripTrailingZeros().toPlainString());
        if (v.getYearlyTransactionLimit() != null) parts.add("yearly count " + v.getYearlyTransactionLimit());
        if (v.getYearlyAmountLimit() != null) parts.add("yearly amount " + v.getYearlyAmountLimit().stripTrailingZeros().toPlainString());
        return parts.isEmpty() ? "" : " {" + String.join(", ", parts) + "}";
    }

    private RoutingDto toDto(PaymentRoutingProfile p) {
        List<RoutingVendorDto> vendors = p.getVendors().stream()
                .sorted(Comparator.comparing(PaymentRoutingProfileVendor::getPriority))
                .map(v -> new RoutingVendorDto(v.getVendor().getId(), v.getVendor().getVendorName(), v.getPriority(),
                        Boolean.TRUE.equals(v.getEnabled()), v.getMinAmount(), v.getMaxAmount(),
                        v.getDailyTransactionLimit(), v.getDailyAmountLimit(),
                        v.getMonthlyTransactionLimit(), v.getMonthlyAmountLimit(),
                        v.getYearlyTransactionLimit(), v.getYearlyAmountLimit()))
                .toList();
        return new RoutingDto(p.getId(), p.getProduct().getId(), p.getProduct().getProductName(), p.getName(),
                Boolean.TRUE.equals(p.getStatus()), p.getRetryCount(), p.getRetryWaitSeconds(), vendors,
                p.getId() == null ? 0 : assignmentRepository.countByProfile_Id(p.getId()),
                p.getCreatedAt(), p.getUpdatedAt(), p.getUpdatedBy());
    }

    private ProductVendorDto toDto(PaymentProductVendor l) {
        return new ProductVendorDto(l.getId(), l.getProduct().getId(), l.getProduct().getProductName(),
                l.getVendor().getId(), l.getVendor().getVendorName(), Boolean.TRUE.equals(l.getVendor().getStatus()),
                l.getCategories(), Boolean.TRUE.equals(l.getEnabled()));
    }

    private PaymentProduct requireProduct(Long id) {
        PaymentProduct p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (!Boolean.TRUE.equals(p.getStatus())) {
            throw new IllegalArgumentException("Product " + p.getProductName() + " is inactive");
        }
        return p;
    }

    private PaymentVendor requireVendor(Long id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found with id " + id));
    }

    private PaymentRoutingProfile requireProfile(Long id) {
        return profileRepository.findByIdWithVendors(id)
                .orElseThrow(() -> new IllegalArgumentException("Routing not found"));
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.length() < 2 || n.length() > 100) {
            throw new IllegalArgumentException("Routing name must be 2 to 100 characters");
        }
        return n;
    }

    /** "Credit Card,  credit card , Electricity" becomes "Credit Card, Electricity"; empty becomes null (= all). */
    static String normalizeCategories(String categories) {
        if (categories == null || categories.isBlank()) {
            return null;
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String c : categories.split(",")) {
            String t = c.trim().replaceAll("\\s+", " ");
            if (!t.isEmpty()) {
                unique.putIfAbsent(t.toLowerCase(), t);
            }
        }
        if (unique.isEmpty()) {
            return null;
        }
        String joined = String.join(", ", unique.values());
        if (joined.length() > 500) {
            throw new IllegalArgumentException("Categories are too long");
        }
        return joined;
    }

    private static String normalizeType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!CustomerRef.MERCHANT.equals(t) && !CustomerRef.FRANCHISE.equals(t)) {
            throw new IllegalArgumentException("Account type must be MERCHANT or FRANCHISE");
        }
        return t;
    }

    private static List<TargetRef> distinctTargets(List<TargetRef> targets) {
        Map<String, TargetRef> unique = new LinkedHashMap<>();
        if (targets != null) {
            for (TargetRef t : targets) {
                if (t == null || t.id() == null) {
                    throw new IllegalArgumentException("An account in the list has no id");
                }
                TargetRef normal = new TargetRef(normalizeType(t.type()), t.id());
                unique.putIfAbsent(key(normal), normal);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static String key(TargetRef t) {
        return t.type() + ":" + t.id();
    }

    private Map<String, String> targetNames(List<TargetRef> targets) {
        Map<Long, String> merchants = merchantNames(targets.stream()
                .filter(t -> CustomerRef.MERCHANT.equals(t.type())).map(TargetRef::id).toList());
        Map<Long, String> franchises = franchiseNames(targets.stream()
                .filter(t -> CustomerRef.FRANCHISE.equals(t.type())).map(TargetRef::id).toList());
        Map<String, String> names = new HashMap<>();
        merchants.forEach((id, n) -> names.put(CustomerRef.MERCHANT + ":" + id, n));
        franchises.forEach((id, n) -> names.put(CustomerRef.FRANCHISE + ":" + id, n));
        return names;
    }

    private Map<Long, String> merchantNames(List<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (!ids.isEmpty()) {
            merchantRepository.findAllById(ids).forEach(m -> out.put(m.getId(), merchantName(m)));
        }
        return out;
    }

    private Map<Long, String> franchiseNames(List<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (!ids.isEmpty()) {
            franchiseRepository.findAllById(ids).forEach(f -> out.put(f.getId(), franchiseName(f)));
        }
        return out;
    }

    private static String merchantName(Merchant m) {
        return m.getBusinessName() != null && !m.getBusinessName().isBlank() ? m.getBusinessName() : "Merchant #" + m.getId();
    }

    private static String franchiseName(Franchise f) {
        return f.getFranchiseName() != null && !f.getFranchiseName().isBlank() ? f.getFranchiseName() : "Franchise #" + f.getId();
    }

    private static String sample(List<String> names) {
        Set<String> first = names.stream().limit(5).collect(Collectors.toCollection(LinkedHashSet::new));
        return String.join(", ", first) + (names.size() > 5 ? " and " + (names.size() - 5) + " more" : "");
    }

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getName() != null ? a.getName() : "system";
    }

    /** Every change is recorded in the same transaction: if the history cannot be written, the change is not made. */
    private void audit(Long profileId, Long productId, String action, String details) {
        PaymentRoutingAudit row = new PaymentRoutingAudit();
        row.setProfileId(profileId);
        row.setProductId(productId);
        row.setAction(action);
        row.setActor(actor());
        row.setDetails(details != null && details.length() > 4000 ? details.substring(0, 4000) : details);
        auditRepository.save(row);
        log.info("Routing change | {} | by {} | {}", action, row.getActor(), details);
    }
}
