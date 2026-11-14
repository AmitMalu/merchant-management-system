package com.project2.ism.Service;

import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.ReportResponse;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.ReportRow;
import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.ReportSummary;
import com.project2.ism.Model.Bbps.BbpsTransaction;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction;
import com.project2.ism.Model.CreditCard.CreditCardBillTransaction.Status;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Credit card bill payment report (CCBP) for admins, franchises and merchants.
 *
 * Who sees what is decided here, from the logged-in user only, never from the request:
 *   - admin      : every payment (can narrow down by account type / id)
 *   - franchise  : its own payments plus the payments of the merchants under it
 *   - merchant   : its own payments only
 * Any filter the caller sends is added ON TOP of that scope, so a filter can only ever
 * show fewer rows, never someone else's.
 *
 * The report covers both vendors: SuivrePay payments (credit_card_bill_transactions) and Bill Avenue
 * payments of the credit card category (bbps_transactions). The vendor filter narrows it to one of them.
 *
 * Only payment attempts are listed (SUCCESS, PENDING, FAILED). Bills that were only
 * looked up (FETCHED) are not payments and are left out. The report reads a handful of
 * columns instead of whole rows, so the large raw vendor response is never loaded.
 */
@Service
public class CreditCardReportService {

    private static final Logger log = LoggerFactory.getLogger(CreditCardReportService.class);

    private static final List<Status> REPORT_STATUSES = List.of(Status.SUCCESS, Status.PENDING, Status.FAILED);
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50000; // the Excel / PDF export asks for everything

    /** Who is asking: an admin, or a merchant / franchise (the customer). */
    public record Viewer(boolean admin, CustomerRef customer) {
    }

    public record Filters(LocalDate startDate, LocalDate endDate, String status,
                          String customerType, Long customerId, String search, String vendor) {
    }

    static final String VENDOR_SUIVREPAY = "SUIVREPAY";
    static final String VENDOR_BILLAVENUE = "BILLAVENUE";

    private static final ReportSummary NO_PAYMENTS = new ReportSummary(0, 0, 0, 0,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    private static final Comparator<ReportRow> NEWEST_FIRST = Comparator
            .comparing(ReportRow::paidAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
            .thenComparing(ReportRow::id, Comparator.nullsLast(Comparator.<Long>reverseOrder()));

    // The bill category (Bill Avenue "service name") that counts as a credit card bill.
    @org.springframework.beans.factory.annotation.Value("${bbps.credit-card-category:Credit Card}")
    private String creditCardCategory = "Credit Card";

    @PersistenceContext
    private EntityManager em;

    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;

    public CreditCardReportService(MerchantRepository merchantRepository, FranchiseRepository franchiseRepository) {
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(Viewer viewer, Filters filters, int page, int size) {
        if (viewer == null || (!viewer.admin() && viewer.customer() == null)) {
            throw new IllegalArgumentException("Report is not available for this account.");
        }
        Filters f = filters == null ? new Filters(null, null, null, null, null, null, null) : filters;
        if (f.startDate() != null && f.endDate() != null && f.startDate().isAfter(f.endDate())) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }
        int pageNo = Math.max(page, 0);
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        String vendor = normalizeVendor(f.vendor());
        boolean suivrePay = vendor == null || VENDOR_SUIVREPAY.equals(vendor);
        boolean billAvenue = vendor == null || VENDOR_BILLAVENUE.equals(vendor);

        log.info("Credit card report | {} | start={} | end={} | status={} | customerType={} | customerId={} | vendor={} | page={} | size={}",
                viewer.admin() ? "ADMIN" : viewer.customer().label(), f.startDate(), f.endDate(), f.status(),
                f.customerType(), f.customerId(), vendor, pageNo, pageSize);

        ReportSummary fromSuivrePay = suivrePay ? summarize(viewer, f) : NO_PAYMENTS;
        ReportSummary fromBillAvenue = billAvenue ? summarizeBillAvenue(viewer, f) : NO_PAYMENTS;
        ReportSummary summary = plus(fromSuivrePay, fromBillAvenue);

        List<ReportRow> rows;
        if (summary.totalCount() == 0) {
            rows = List.of();
        } else if (suivrePay && billAvenue) {
            // Two tables, one list: take the newest rows of each up to the end of this page, put them in
            // order, and cut out the page.
            int upTo = (int) Math.min((long) (pageNo + 1) * pageSize, Integer.MAX_VALUE);
            List<ReportRow> merged = new ArrayList<>();
            if (fromSuivrePay.totalCount() > 0) {
                merged.addAll(fetchSuivrePayRows(viewer, f, 0, upTo));
            }
            if (fromBillAvenue.totalCount() > 0) {
                merged.addAll(fetchBillAvenueRows(viewer, f, 0, upTo));
            }
            merged.sort(NEWEST_FIRST);
            int from = (int) Math.min((long) pageNo * pageSize, merged.size());
            rows = new ArrayList<>(merged.subList(from, Math.min(merged.size(), from + pageSize)));
        } else {
            int first = (int) Math.min((long) pageNo * pageSize, Integer.MAX_VALUE);
            rows = suivrePay ? fetchSuivrePayRows(viewer, f, first, pageSize)
                    : fetchBillAvenueRows(viewer, f, first, pageSize);
        }

        return new ReportResponse(rows, summary.totalCount(), pageNo, pageSize, summary);
    }

    /** null = both vendors. */
    private static String normalizeVendor(String vendor) {
        if (vendor == null || vendor.isBlank() || "ALL".equalsIgnoreCase(vendor.trim())) {
            return null;
        }
        String v = vendor.trim().toUpperCase();
        if (!VENDOR_SUIVREPAY.equals(v) && !VENDOR_BILLAVENUE.equals(v)) {
            throw new IllegalArgumentException("Invalid vendor. Use BILLAVENUE or SUIVREPAY.");
        }
        return v;
    }

    private static ReportSummary plus(ReportSummary a, ReportSummary b) {
        return new ReportSummary(a.totalCount() + b.totalCount(), a.successCount() + b.successCount(),
                a.pendingCount() + b.pendingCount(), a.failedCount() + b.failedCount(),
                a.successBillAmount().add(b.successBillAmount()), a.successCharges().add(b.successCharges()),
                a.successTotalDeducted().add(b.successTotalDeducted()));
    }

    // =====================================================================
    // Queries
    // =====================================================================

    private ReportSummary summarize(Viewer viewer, Filters f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<CreditCardBillTransaction> root = cq.from(CreditCardBillTransaction.class);

        cq.multiselect(
                root.get("status"),
                cb.count(root),
                cb.sum(cb.coalesce(root.<BigDecimal>get("paidAmount"), root.<BigDecimal>get("billAmount"))),
                cb.sum(root.<BigDecimal>get("charges")),
                cb.sum(root.<BigDecimal>get("totalDeducted")));
        cq.where(predicates(root, cq, cb, viewer, f).toArray(new Predicate[0]));
        cq.groupBy(root.get("status"));

        long success = 0, pending = 0, failed = 0;
        BigDecimal bill = BigDecimal.ZERO, charges = BigDecimal.ZERO, total = BigDecimal.ZERO;

        for (Tuple t : em.createQuery(cq).getResultList()) {
            Status status = t.get(0, Status.class);
            long count = t.get(1, Long.class);
            if (status == Status.SUCCESS) {
                success = count;
                bill = nvl(t.get(2, BigDecimal.class));
                charges = nvl(t.get(3, BigDecimal.class));
                total = nvl(t.get(4, BigDecimal.class));
            } else if (status == Status.PENDING) {
                pending = count;
            } else if (status == Status.FAILED) {
                failed = count;
            }
        }
        return new ReportSummary(success + pending + failed, success, pending, failed, bill, charges, total);
    }

    private List<ReportRow> fetchSuivrePayRows(Viewer viewer, Filters f, int first, int max) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<CreditCardBillTransaction> root = cq.from(CreditCardBillTransaction.class);

        cq.multiselect(
                root.get("id"),                 // 0
                root.get("paidAt"),             // 1
                root.get("completedAt"),        // 2
                root.get("walletTxnId"),        // 3
                root.get("customerType"),       // 4
                root.get("merchantId"),         // 5  (the customer's id, see customerType)
                root.get("billerName"),         // 6
                root.get("cardLast4"),          // 7
                root.get("customerName"),       // 8  (card holder)
                root.get("customerMobile"),     // 9
                cb.coalesce(root.<BigDecimal>get("paidAmount"), root.<BigDecimal>get("billAmount")),  // 10 (paid)
                root.get("charges"),            // 11
                root.get("totalDeducted"),      // 12
                root.get("status"),             // 13
                root.get("responseMessage"),    // 14
                root.get("complaintTicketId"),  // 15
                root.get("requestId"),          // 16
                root.get("vendorTxnId"),        // 17
                root.get("billAmount"));        // 18 (the bill that was due)
        cq.where(predicates(root, cq, cb, viewer, f).toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get("paidAt")), cb.desc(root.get("id")));

        List<Tuple> tuples = em.createQuery(cq)
                .setFirstResult(first)
                .setMaxResults(max)
                .getResultList();

        // names for the accounts on this page: two small lookups, not one per row
        Set<Long> merchantIds = new HashSet<>();
        Set<Long> franchiseIds = new HashSet<>();
        for (Tuple t : tuples) {
            (CustomerRef.FRANCHISE.equalsIgnoreCase(t.get(4, String.class)) ? franchiseIds : merchantIds)
                    .add(t.get(5, Long.class));
        }
        Map<Long, Merchant> merchants = new HashMap<>();
        if (!merchantIds.isEmpty()) {
            merchantRepository.findAllById(merchantIds).forEach(m -> merchants.put(m.getId(), m));
        }
        Map<Long, Franchise> franchises = new HashMap<>();
        if (!franchiseIds.isEmpty()) {
            franchiseRepository.findAllById(franchiseIds).forEach(fr -> franchises.put(fr.getId(), fr));
        }

        List<ReportRow> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            String type = t.get(4, String.class);
            Long customerId = t.get(5, Long.class);

            String name = null;
            String franchiseName = null;
            if (CustomerRef.FRANCHISE.equalsIgnoreCase(type)) {
                Franchise fr = franchises.get(customerId);
                name = fr != null ? fr.getFranchiseName() : null;
            } else {
                Merchant m = merchants.get(customerId);
                if (m != null) {
                    name = m.getBusinessName();
                    franchiseName = m.getFranchise() != null ? m.getFranchise().getFranchiseName() : null;
                }
            }

            Status status = t.get(13, Status.class);
            rows.add(new ReportRow(
                    t.get(0, Long.class),
                    t.get(1, LocalDateTime.class),
                    t.get(2, LocalDateTime.class),
                    t.get(3, String.class),
                    type,
                    customerId,
                    name,
                    franchiseName,
                    t.get(6, String.class),
                    t.get(7, String.class),
                    t.get(8, String.class),
                    maskMobile(t.get(9, String.class)),
                    t.get(10, BigDecimal.class),
                    t.get(11, BigDecimal.class),
                    t.get(12, BigDecimal.class),
                    status != null ? status.name() : null,
                    t.get(14, String.class),
                    t.get(15, String.class),
                    viewer.admin() ? t.get(16, String.class) : null,
                    viewer.admin() ? t.get(17, String.class) : null,
                    t.get(18, BigDecimal.class),
                    viewer.admin() ? VENDOR_SUIVREPAY : null));
        }
        return rows;
    }

    // =====================================================================
    // Bill Avenue (bbps_transactions, credit card category)
    // =====================================================================

    private ReportSummary summarizeBillAvenue(Viewer viewer, Filters f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<BbpsTransaction> root = cq.from(BbpsTransaction.class);

        cq.multiselect(
                root.get("status"),
                cb.count(root),
                cb.sum(root.<BigDecimal>get("amount")),
                cb.sum(root.<BigDecimal>get("charges")),
                cb.sum(root.<BigDecimal>get("totalDeducted")));
        cq.where(billAvenuePredicates(root, cq, cb, viewer, f).toArray(new Predicate[0]));
        cq.groupBy(root.get("status"));

        long success = 0, pending = 0, failed = 0;
        BigDecimal bill = BigDecimal.ZERO, charges = BigDecimal.ZERO, total = BigDecimal.ZERO;

        for (Tuple t : em.createQuery(cq).getResultList()) {
            BbpsTransaction.BbpsStatus status = t.get(0, BbpsTransaction.BbpsStatus.class);
            long count = t.get(1, Long.class);
            if (status == BbpsTransaction.BbpsStatus.SUCCESS) {
                success = count;
                bill = nvl(t.get(2, BigDecimal.class));
                charges = nvl(t.get(3, BigDecimal.class));
                total = nvl(t.get(4, BigDecimal.class));
            } else if (status == BbpsTransaction.BbpsStatus.PENDING) {
                pending = count;
            } else if (status == BbpsTransaction.BbpsStatus.FAILED) {
                failed = count;
            }
        }
        return new ReportSummary(success + pending + failed, success, pending, failed, bill, charges, total);
    }

    private List<ReportRow> fetchBillAvenueRows(Viewer viewer, Filters f, int first, int max) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<BbpsTransaction> root = cq.from(BbpsTransaction.class);

        cq.multiselect(
                root.get("id"),                 // 0
                root.get("createdAt"),          // 1  (when the payment was made)
                root.get("completedAt"),        // 2
                root.get("txnRefId"),           // 3  (Bill Avenue's transaction id)
                root.get("merchantId"),         // 4
                root.get("billerName"),         // 5
                root.get("consumerNumber"),     // 6  (for a credit card: the last 4 digits)
                root.get("customerMobile"),     // 7
                root.get("amount"),             // 8
                root.get("charges"),            // 9
                root.get("totalDeducted"),      // 10
                root.get("status"),             // 11
                root.get("responseMessage"),    // 12
                root.get("requestId"),          // 13
                root.get("approvalRefNumber")); // 14
        cq.where(billAvenuePredicates(root, cq, cb, viewer, f).toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));

        List<Tuple> tuples = em.createQuery(cq).setFirstResult(first).setMaxResults(max).getResultList();

        Set<Long> merchantIds = new HashSet<>();
        for (Tuple t : tuples) {
            merchantIds.add(t.get(4, Long.class));
        }
        Map<Long, Merchant> merchants = new HashMap<>();
        if (!merchantIds.isEmpty()) {
            merchantRepository.findAllById(merchantIds).forEach(m -> merchants.put(m.getId(), m));
        }

        List<ReportRow> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            Long merchantId = t.get(4, Long.class);
            Merchant m = merchants.get(merchantId);
            String consumer = t.get(6, String.class);
            BbpsTransaction.BbpsStatus status = t.get(11, BbpsTransaction.BbpsStatus.class);
            rows.add(new ReportRow(
                    t.get(0, Long.class),
                    t.get(1, LocalDateTime.class),
                    t.get(2, LocalDateTime.class),
                    t.get(3, String.class),
                    CustomerRef.MERCHANT,
                    merchantId,
                    m != null ? m.getBusinessName() : null,
                    m != null && m.getFranchise() != null ? m.getFranchise().getFranchiseName() : null,
                    t.get(5, String.class),
                    consumer == null || consumer.length() < 4 ? consumer : consumer.substring(consumer.length() - 4),
                    null,                                   // Bill Avenue payments do not keep the card holder's name
                    maskMobile(t.get(7, String.class)),
                    t.get(8, BigDecimal.class),
                    t.get(9, BigDecimal.class),
                    t.get(10, BigDecimal.class),
                    status != null ? status.name() : null,
                    t.get(12, String.class),
                    null,
                    viewer.admin() ? t.get(13, String.class) : null,
                    viewer.admin() ? t.get(14, String.class) : null,
                    null,
                    viewer.admin() ? VENDOR_BILLAVENUE : null));
        }
        return rows;
    }

    /** Scope first (who may see the row), then the filters. Bill Avenue payments are made by merchants only. */
    private List<Predicate> billAvenuePredicates(Root<BbpsTransaction> root, CriteriaQuery<?> query,
                                                 CriteriaBuilder cb, Viewer viewer, Filters f) {
        List<Predicate> p = new ArrayList<>();

        // ---- scope ----
        if (!viewer.admin()) {
            CustomerRef me = viewer.customer();
            if (me.isFranchise()) {
                Subquery<Long> myMerchants = query.subquery(Long.class);
                Root<Merchant> m = myMerchants.from(Merchant.class);
                myMerchants.select(m.<Long>get("id")).where(cb.equal(m.get("franchise").get("id"), me.id()));
                p.add(root.get("merchantId").in(myMerchants));
            } else {
                p.add(cb.equal(root.get("merchantId"), me.id()));
            }
        }

        // ---- only credit card bills, only payment attempts ----
        p.add(cb.equal(cb.lower(root.<String>get("category")), creditCardCategory.trim().toLowerCase()));
        if (f.status() != null && !f.status().isBlank() && !"ALL".equalsIgnoreCase(f.status().trim())) {
            BbpsTransaction.BbpsStatus wanted;
            try {
                wanted = BbpsTransaction.BbpsStatus.valueOf(f.status().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status. Use SUCCESS, PENDING or FAILED.");
            }
            p.add(cb.equal(root.get("status"), wanted));
        }

        // ---- filters ----
        if (f.startDate() != null) {
            p.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), f.startDate().atStartOfDay()));
        }
        if (f.endDate() != null) {
            p.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), f.endDate().plusDays(1).atStartOfDay()));
        }
        if (f.customerType() != null && !f.customerType().isBlank()) {
            String type = f.customerType().trim().toUpperCase();
            if (!CustomerRef.MERCHANT.equals(type) && !CustomerRef.FRANCHISE.equals(type)) {
                throw new IllegalArgumentException("Invalid account type. Use MERCHANT or FRANCHISE.");
            }
            if (CustomerRef.FRANCHISE.equals(type)) {
                p.add(cb.disjunction());    // franchises do not pay through Bill Avenue
            }
        }
        if (f.customerId() != null) {
            p.add(cb.equal(root.get("merchantId"), f.customerId()));
        }
        if (f.search() != null && !f.search().isBlank()) {
            String s = f.search().trim().toLowerCase();
            String like = "%" + s + "%";
            p.add(cb.or(
                    cb.like(cb.lower(root.<String>get("txnRefId")), like),
                    cb.like(cb.lower(root.<String>get("billerName")), like),
                    cb.equal(root.get("consumerNumber"), s)));
        }
        return p;
    }

    /** Scope first (who may see the row), then the filters the user chose. */
    private List<Predicate> predicates(Root<CreditCardBillTransaction> root, CriteriaQuery<?> query,
                                       CriteriaBuilder cb, Viewer viewer, Filters f) {
        List<Predicate> p = new ArrayList<>();

        // ---- scope ----
        if (!viewer.admin()) {
            CustomerRef me = viewer.customer();
            if (me.isFranchise()) {
                Subquery<Long> myMerchants = query.subquery(Long.class);
                Root<Merchant> m = myMerchants.from(Merchant.class);
                myMerchants.select(m.<Long>get("id")).where(cb.equal(m.get("franchise").get("id"), me.id()));

                Predicate own = cb.and(
                        cb.equal(root.get("customerType"), CustomerRef.FRANCHISE),
                        cb.equal(root.get("merchantId"), me.id()));
                Predicate underMe = cb.and(
                        cb.equal(root.get("customerType"), CustomerRef.MERCHANT),
                        root.get("merchantId").in(myMerchants));
                p.add(cb.or(own, underMe));
            } else {
                p.add(cb.equal(root.get("customerType"), CustomerRef.MERCHANT));
                p.add(cb.equal(root.get("merchantId"), me.id()));
            }
        }

        // ---- only payment attempts ----
        if (f.status() == null || f.status().isBlank() || "ALL".equalsIgnoreCase(f.status().trim())) {
            p.add(root.get("status").in(REPORT_STATUSES));
        } else {
            Status wanted;
            try {
                wanted = Status.valueOf(f.status().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status. Use SUCCESS, PENDING or FAILED.");
            }
            if (!REPORT_STATUSES.contains(wanted)) {
                throw new IllegalArgumentException("Invalid status. Use SUCCESS, PENDING or FAILED.");
            }
            p.add(cb.equal(root.get("status"), wanted));
        }

        // ---- filters ----
        if (f.startDate() != null) {
            p.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("paidAt"), f.startDate().atStartOfDay()));
        }
        if (f.endDate() != null) {
            p.add(cb.lessThan(root.<LocalDateTime>get("paidAt"), f.endDate().plusDays(1).atStartOfDay()));
        }
        if (f.customerType() != null && !f.customerType().isBlank()) {
            String type = f.customerType().trim().toUpperCase();
            if (!CustomerRef.MERCHANT.equals(type) && !CustomerRef.FRANCHISE.equals(type)) {
                throw new IllegalArgumentException("Invalid account type. Use MERCHANT or FRANCHISE.");
            }
            p.add(cb.equal(root.get("customerType"), type));
        }
        if (f.customerId() != null) {
            p.add(cb.equal(root.get("merchantId"), f.customerId()));
        }
        if (f.search() != null && !f.search().isBlank()) {
            String s = f.search().trim().toLowerCase();
            String like = "%" + s + "%";
            p.add(cb.or(
                    cb.like(cb.lower(root.<String>get("walletTxnId")), like),
                    cb.like(cb.lower(root.<String>get("billerName")), like),
                    cb.like(cb.lower(root.<String>get("customerName")), like),
                    cb.equal(root.get("cardLast4"), s)));
        }
        return p;
    }

    // =====================================================================

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String maskMobile(String m) {
        return m == null || m.length() < 6 ? null : m.substring(0, 2) + "*".repeat(m.length() - 4) + m.substring(m.length() - 2);
    }
}
