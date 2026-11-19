package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.Decision;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.TrialRow;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.TrialStatus;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.TrialSummaryRow;
import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Model.Routing.PaymentRoutingAudit;
import com.project2.ism.Model.Routing.PaymentRoutingSetting;
import com.project2.ism.Model.Routing.PaymentRoutingTrial;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PaymentProductRepository;
import com.project2.ism.Repository.PaymentRoutingAuditRepository;
import com.project2.ism.Repository.PaymentRoutingSettingRepository;
import com.project2.ism.Repository.PaymentRoutingTrialRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The routing TRIAL RUN. For a real payment it works out which vendor routing would choose and records it next to
 * the vendor that was really used. It changes nothing about the payment.
 *
 * Safety:
 *  - The payment's own thread only reads an in-memory flag and hands the work to a separate pool. It never
 *    touches the database, so even a database problem here cannot affect the payment's transaction.
 *  - Everything is caught and logged; nothing is ever thrown to the payment.
 *  - The flag is switched from the routing page (stored in payment_routing_settings) and starts off.
 *  - Rows are deleted after routing.trial-retention-hours (default 3).
 */
@Service
public class PaymentRoutingTrialService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRoutingTrialService.class);

    static final String SETTING_ENABLED = "routing.trial-enabled";

    public static final String SOURCE_PAYOUT = "PAYOUT";
    public static final String SOURCE_BILL_AVENUE = "BILL_AVENUE";
    public static final String SOURCE_SUIVREPAY = "SUIVREPAY";

    private final Executor executor;
    private final PaymentRoutingConfigService configService;
    private final PaymentRoutingTrialRepository trialRepository;
    private final PaymentRoutingSettingRepository settingRepository;
    private final PaymentRoutingAuditRepository auditRepository;
    private final PaymentProductRepository productRepository;
    private final PaymentVendorRepository vendorRepository;
    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;

    @Value("${routing.trial-enabled:false}")
    private boolean enabledByDefault = false;

    @Value("${routing.trial-retention-hours:3}")
    private int retentionHours = 3;

    @Value("${routing.payout-product-code:001}")
    private String payoutProductCode = "001";

    @Value("${routing.bill-payment-product-code:002}")
    private String billPaymentProductCode = "002";

    @Value("${suivrepay.vendor-name:SuivrePay}")
    private String suivrePayVendorName = "SuivrePay";

    @Value("${bbps.credit-card-category:Credit Card}")
    private String creditCardCategory = "Credit Card";

    // The only thing the payment's thread reads. Refreshed from the database every few seconds and on toggle.
    private volatile boolean enabled = false;

    public PaymentRoutingTrialService(@Qualifier("routingTrialExecutor") Executor executor,
                                      PaymentRoutingConfigService configService,
                                      PaymentRoutingTrialRepository trialRepository,
                                      PaymentRoutingSettingRepository settingRepository,
                                      PaymentRoutingAuditRepository auditRepository,
                                      PaymentProductRepository productRepository,
                                      PaymentVendorRepository vendorRepository,
                                      MerchantRepository merchantRepository,
                                      FranchiseRepository franchiseRepository) {
        this.executor = executor;
        this.configService = configService;
        this.trialRepository = trialRepository;
        this.settingRepository = settingRepository;
        this.auditRepository = auditRepository;
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
    }

    // =====================================================================
    // Called from the payment code. Never throws, never waits, never reads the database.
    // =====================================================================

    public void recordPayout(String accountType, Long accountId, BigDecimal amount, Long usedVendorId, String reference) {
        submit(new Input(payoutProductCode, SOURCE_PAYOUT, accountType, accountId, amount, null,
                usedVendorId, null, reference));
    }

    public void recordBillAvenue(Long merchantId, BigDecimal amount, String category, Long usedVendorId, String reference) {
        submit(new Input(billPaymentProductCode, SOURCE_BILL_AVENUE, CustomerRef.MERCHANT, merchantId, amount, category,
                usedVendorId, null, reference));
    }

    public void recordSuivrePay(String accountType, Long accountId, BigDecimal amount, String reference) {
        submit(new Input(billPaymentProductCode, SOURCE_SUIVREPAY, accountType, accountId, amount, creditCardCategory,
                null, suivrePayVendorName, reference));
    }

    private record Input(String productCode, String source, String accountType, Long accountId, BigDecimal amount,
                         String category, Long usedVendorId, String usedVendorName, String reference) {
    }

    private void submit(Input in) {
        try {
            if (!enabled) {
                return;
            }
            executor.execute(() -> work(in));
        } catch (Throwable t) {
            log.warn("Routing trial: could not queue ({}): {}", in.source(), t.toString());
        }
    }

    /** Runs on the trial pool, in its own transactions, completely apart from the payment. */
    void work(Input in) {
        try {
            PaymentProduct product = productRepository.findByProductCode(in.productCode()).orElse(null);
            if (product == null || in.accountId() == null) {
                return;
            }
            Decision decision = configService.decide(product, in.accountType(), in.accountId(), in.amount(), in.category());

            Long usedId = in.usedVendorId();
            String usedName = null;
            if (usedId == null && in.usedVendorName() != null) {
                var vendor = vendorRepository.findByVendorNameAndStatus(in.usedVendorName(), true).orElse(null);
                if (vendor != null) {
                    usedId = vendor.getId();
                    usedName = vendor.getVendorName();
                } else {
                    usedName = in.usedVendorName();
                }
            } else if (usedId != null) {
                usedName = vendorRepository.findById(usedId).map(v -> v.getVendorName()).orElse(null);
            }

            PaymentRoutingTrial row = new PaymentRoutingTrial();
            row.setProductId(product.getId());
            row.setProductName(product.getProductName());
            row.setSource(in.source());
            row.setAccountType(in.accountType());
            row.setAccountId(in.accountId());
            row.setAmount(in.amount());
            row.setCategory(in.category());
            row.setRoutingId(decision.routingId());
            row.setRoutingName(decision.routingName());
            row.setChosenVendorId(decision.chosenVendorId());
            row.setChosenVendorName(decision.chosenVendorName());
            row.setUsedVendorId(usedId);
            row.setUsedVendorName(usedName);
            row.setNoRouting(decision.noRouting());
            row.setMatched(decision.chosenVendorId() != null && decision.chosenVendorId().equals(usedId));
            row.setDetails(decision.summary() != null && decision.summary().length() > 1000
                    ? decision.summary().substring(0, 1000) : decision.summary());
            row.setReference(in.reference());
            trialRepository.save(row);
        } catch (Throwable t) {
            log.warn("Routing trial: could not record ({}): {}", in.source(), t.toString());
        }
    }

    // =====================================================================
    // The switch
    // =====================================================================

    public boolean isEnabled() {
        return enabled;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 5_000)
    void refreshFlag() {
        try {
            enabled = settingRepository.findById(SETTING_ENABLED)
                    .map(s -> "true".equalsIgnoreCase(s.getValue()))
                    .orElse(enabledByDefault);
        } catch (Throwable t) {
            log.warn("Routing trial: could not read the switch, keeping {}: {}", enabled, t.toString());
        }
    }

    public TrialStatus setEnabled(boolean value) {
        String actor = actor();
        PaymentRoutingSetting setting = settingRepository.findById(SETTING_ENABLED)
                .orElseGet(() -> new PaymentRoutingSetting(SETTING_ENABLED, "false", actor));
        setting.setValue(String.valueOf(value));
        setting.setUpdatedBy(actor);
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
        enabled = value;

        PaymentRoutingAudit audit = new PaymentRoutingAudit();
        audit.setAction(value ? "TRIAL_ON" : "TRIAL_OFF");
        audit.setActor(actor);
        audit.setDetails("Routing trial run switched " + (value ? "on" : "off"));
        auditRepository.save(audit);
        log.info("Routing trial run switched {} by {}", value ? "on" : "off", actor);
        return status();
    }

    public TrialStatus status() {
        return new TrialStatus(enabled, retentionHours, trialRepository.count());
    }

    // =====================================================================
    // Clean-up: rows live only a few hours
    // =====================================================================

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    void purge() {
        try {
            int removed = trialRepository.purgeOlderThan(LocalDateTime.now().minusHours(retentionHours));
            if (removed > 0) {
                log.info("Routing trial: removed {} row(s) older than {} hour(s)", removed, retentionHours);
            }
        } catch (Throwable t) {
            log.warn("Routing trial: clean-up failed: {}", t.toString());
        }
    }

    public int clearAll() {
        long before = trialRepository.count();
        trialRepository.deleteAllInBatch();
        return (int) before;
    }

    // =====================================================================
    // For the admin page
    // =====================================================================

    public List<TrialSummaryRow> summary() {
        List<TrialSummaryRow> out = new ArrayList<>();
        for (Object[] r : trialRepository.summary()) {
            out.add(new TrialSummaryRow((String) r[0], (String) r[1], (String) r[2], (String) r[3],
                    Boolean.TRUE.equals(r[4]), Boolean.TRUE.equals(r[5]), ((Number) r[6]).longValue(),
                    r[7] instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(r[7]))));
        }
        return out;
    }

    public org.springframework.data.domain.Page<TrialRow> rows(boolean onlyDifferent, int page, int size) {
        var result = trialRepository.search(onlyDifferent, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        Map<Long, String> merchants = new HashMap<>();
        Map<Long, String> franchises = new HashMap<>();
        List<Long> merchantIds = result.getContent().stream()
                .filter(t -> CustomerRef.MERCHANT.equals(t.getAccountType())).map(PaymentRoutingTrial::getAccountId).toList();
        List<Long> franchiseIds = result.getContent().stream()
                .filter(t -> CustomerRef.FRANCHISE.equals(t.getAccountType())).map(PaymentRoutingTrial::getAccountId).toList();
        if (!merchantIds.isEmpty()) {
            merchantRepository.findAllById(merchantIds).forEach(m -> merchants.put(m.getId(), m.getBusinessName()));
        }
        if (!franchiseIds.isEmpty()) {
            franchiseRepository.findAllById(franchiseIds).forEach(f -> franchises.put(f.getId(), f.getFranchiseName()));
        }
        return result.map(t -> new TrialRow(t.getId(), t.getCreatedAt(), t.getProductName(), t.getSource(),
                t.getAccountType(), t.getAccountId(),
                (CustomerRef.MERCHANT.equals(t.getAccountType()) ? merchants : franchises)
                        .getOrDefault(t.getAccountId(), "#" + t.getAccountId()),
                t.getAmount(), t.getCategory(), t.getRoutingName(), t.getUsedVendorName(), t.getChosenVendorName(),
                Boolean.TRUE.equals(t.getMatched()), Boolean.TRUE.equals(t.getNoRouting()), t.getDetails(),
                t.getReference()));
    }

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getName() != null ? a.getName() : "system";
    }
}
