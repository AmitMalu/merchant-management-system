package com.project2.ism.Service;

import com.project2.ism.DTO.PayoutDTO.FranchisePayoutReportDtos.Response;
import com.project2.ism.DTO.PayoutDTO.FranchisePayoutReportDtos.Row;
import com.project2.ism.DTO.PayoutDTO.FranchisePayoutReportDtos.Summary;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Model.Payout.PayoutTransaction.PayoutStatus;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Payout report for a FRANCHISE: its own payouts plus the payouts of the merchants under it.
 *
 * The franchise is always the logged-in user (passed in by the controller from the login
 * token), never something the request names. Filters from the request are added on top of
 * that scope, so they can only narrow the result, never reach another franchise's data.
 *
 * One row per payout (table payout_transactions), so a failed payout appears once as FAILED
 * (its amount was refunded) instead of as a payout row plus a refund row. Only a handful of
 * columns are read; the beneficiary account number is masked to its last 4 digits.
 */
@Service
public class FranchisePayoutReportService {

    private static final Logger log = LoggerFactory.getLogger(FranchisePayoutReportService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50000; // the Excel / PDF export asks for everything

    public record Filters(LocalDate startDate, LocalDate endDate, String status,
                          String customerType, Long customerId, String paymentMode, String search) {
    }

    @PersistenceContext
    private EntityManager em;

    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;

    public FranchisePayoutReportService(MerchantRepository merchantRepository,
                                        FranchiseRepository franchiseRepository) {
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
    }

    @Transactional(readOnly = true)
    public Response getReport(Long franchiseId, Filters filters, int page, int size) {
        if (franchiseId == null) {
            throw new IllegalArgumentException("Report is not available for this account.");
        }
        Filters f = filters == null ? new Filters(null, null, null, null, null, null, null) : filters;
        if (f.startDate() != null && f.endDate() != null && f.startDate().isAfter(f.endDate())) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }
        int pageNo = Math.max(page, 0);
        int pageSize = size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        log.info("Franchise payout report | FRANCHISE#{} | start={} | end={} | status={} | customerType={} | customerId={} | page={} | size={}",
                franchiseId, f.startDate(), f.endDate(), f.status(), f.customerType(), f.customerId(), pageNo, pageSize);

        Summary summary = summarize(franchiseId, f);
        List<Row> rows = summary.totalCount() == 0 ? List.of() : fetchRows(franchiseId, f, pageNo, pageSize);

        return new Response(rows, summary.totalCount(), pageNo, pageSize, summary);
    }

    // =====================================================================
    // Queries
    // =====================================================================

    private Summary summarize(Long franchiseId, Filters f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<PayoutTransaction> root = cq.from(PayoutTransaction.class);

        cq.multiselect(
                root.get("status"),
                cb.count(root),
                cb.sum(root.<BigDecimal>get("amount")),
                cb.sum(root.<BigDecimal>get("charges")),
                cb.sum(root.<BigDecimal>get("totalDeducted")));
        cq.where(predicates(root, cq, cb, franchiseId, f).toArray(new Predicate[0]));
        cq.groupBy(root.get("status"));

        long success = 0, pending = 0, failed = 0;
        BigDecimal amount = BigDecimal.ZERO, charges = BigDecimal.ZERO, total = BigDecimal.ZERO;

        for (Tuple t : em.createQuery(cq).getResultList()) {
            PayoutStatus status = t.get(0, PayoutStatus.class);
            long count = t.get(1, Long.class);
            if (status == PayoutStatus.SUCCESS) {
                success = count;
                amount = nvl(t.get(2, BigDecimal.class));
                charges = nvl(t.get(3, BigDecimal.class));
                total = nvl(t.get(4, BigDecimal.class));
            } else if (status == PayoutStatus.PENDING) {
                pending = count;
            } else if (status == PayoutStatus.FAILED) {
                failed = count;
            }
        }
        return new Summary(success + pending + failed, success, pending, failed, amount, charges, total);
    }

    private List<Row> fetchRows(Long franchiseId, Filters f, int page, int size) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<PayoutTransaction> root = cq.from(PayoutTransaction.class);

        cq.multiselect(
                root.get("id"),                           // 0
                root.get("createdAt"),                    // 1
                root.get("completedAt"),                  // 2
                root.get("merchantRefId"),                // 3
                root.get("initiatorType"),                // 4
                root.get("initiatorId"),                  // 5
                root.get("beneficiaryName"),              // 6
                root.get("beneficiaryAccountNumber"),     // 7
                root.get("beneficiaryIFSC"),              // 8
                root.get("beneficiaryBank"),              // 9
                root.get("paymentMode"),                  // 10
                root.get("paymentPurpose"),               // 11
                root.get("amount"),                       // 12
                root.get("charges"),                      // 13
                root.get("totalDeducted"),                // 14
                root.get("status"),                       // 15
                root.get("rrn"),                          // 16
                root.get("responseMessage"));             // 17
        cq.where(predicates(root, cq, cb, franchiseId, f).toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));

        List<Tuple> tuples = em.createQuery(cq)
                .setFirstResult((int) Math.min((long) page * size, Integer.MAX_VALUE))
                .setMaxResults(size)
                .getResultList();

        // names for the accounts on this page: two small lookups, not one per row
        Set<Long> merchantIds = new HashSet<>();
        boolean ownRows = false;
        for (Tuple t : tuples) {
            if (CustomerRef.FRANCHISE.equalsIgnoreCase(t.get(4, String.class))) {
                ownRows = true;
            } else {
                merchantIds.add(t.get(5, Long.class));
            }
        }
        Map<Long, Merchant> merchants = new HashMap<>();
        if (!merchantIds.isEmpty()) {
            merchantRepository.findAllById(merchantIds).forEach(m -> merchants.put(m.getId(), m));
        }
        String ownName = null;
        if (ownRows) {
            ownName = franchiseRepository.findById(franchiseId).map(Franchise::getFranchiseName).orElse(null);
        }

        List<Row> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            String type = t.get(4, String.class);
            Long accountId = t.get(5, Long.class);
            String accountName;
            if (CustomerRef.FRANCHISE.equalsIgnoreCase(type)) {
                accountName = ownName;
            } else {
                Merchant m = merchants.get(accountId);
                accountName = m != null ? m.getBusinessName() : null;
            }

            PayoutStatus status = t.get(15, PayoutStatus.class);
            rows.add(new Row(
                    t.get(0, Long.class),
                    t.get(1, LocalDateTime.class),
                    t.get(2, LocalDateTime.class),
                    t.get(3, String.class),
                    type,
                    accountId,
                    accountName,
                    t.get(6, String.class),
                    maskAccount(t.get(7, String.class)),
                    t.get(8, String.class),
                    t.get(9, String.class),
                    t.get(10, String.class),
                    t.get(11, String.class),
                    t.get(12, BigDecimal.class),
                    t.get(13, BigDecimal.class),
                    t.get(14, BigDecimal.class),
                    status != null ? status.name() : null,
                    t.get(16, String.class),
                    t.get(17, String.class)));
        }
        return rows;
    }

    /** Scope first (whose payouts the franchise may see), then the filters the user chose. */
    private List<Predicate> predicates(Root<PayoutTransaction> root, CriteriaQuery<?> query,
                                       CriteriaBuilder cb, Long franchiseId, Filters f) {
        List<Predicate> p = new ArrayList<>();

        // ---- scope: its own payouts, and those of the merchants under it ----
        Subquery<Long> myMerchants = query.subquery(Long.class);
        Root<Merchant> m = myMerchants.from(Merchant.class);
        myMerchants.select(m.<Long>get("id")).where(cb.equal(m.get("franchise").get("id"), franchiseId));

        Predicate own = cb.and(
                cb.equal(root.get("initiatorType"), CustomerRef.FRANCHISE),
                cb.equal(root.get("initiatorId"), franchiseId));
        Predicate underMe = cb.and(
                cb.equal(root.get("initiatorType"), CustomerRef.MERCHANT),
                root.get("initiatorId").in(myMerchants));
        p.add(cb.or(own, underMe));

        // ---- filters ----
        if (f.status() != null && !f.status().isBlank() && !"ALL".equalsIgnoreCase(f.status().trim())) {
            PayoutStatus wanted;
            try {
                wanted = PayoutStatus.valueOf(f.status().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status. Use SUCCESS, PENDING or FAILED.");
            }
            p.add(cb.equal(root.get("status"), wanted));
        }
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
            p.add(cb.equal(root.get("initiatorType"), type));
        }
        if (f.customerId() != null) {
            p.add(cb.equal(root.get("initiatorId"), f.customerId()));
        }
        if (f.paymentMode() != null && !f.paymentMode().isBlank()) {
            p.add(cb.equal(cb.upper(root.<String>get("paymentMode")), f.paymentMode().trim().toUpperCase()));
        }
        if (f.search() != null && !f.search().isBlank()) {
            String s = f.search().trim().toLowerCase();
            p.add(cb.or(
                    cb.like(cb.lower(root.<String>get("merchantRefId")), "%" + s + "%"),
                    cb.like(cb.lower(root.<String>get("beneficiaryName")), "%" + s + "%"),
                    cb.like(cb.lower(root.<String>get("rrn")), "%" + s + "%"),
                    // the report only shows the last 4 digits of the account, so search by those
                    cb.like(root.<String>get("beneficiaryAccountNumber"), "%" + s)));
        }
        return p;
    }

    // =====================================================================

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String maskAccount(String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String digits = account.trim();
        return digits.length() <= 4 ? "****" : "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }
}
