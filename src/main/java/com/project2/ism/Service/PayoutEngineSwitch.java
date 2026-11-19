package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.AssignmentDto;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.AttemptRow;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.PayoutEngineStatus;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.TargetRef;
import com.project2.ism.Model.Payment.PaymentVendor;
import com.project2.ism.Model.Payout.PaymentAttempt;
import com.project2.ism.Model.Payout.PayoutTransaction;
import com.project2.ism.Model.Routing.PaymentRoutingAudit;
import com.project2.ism.Model.Routing.PaymentRoutingSetting;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PaymentAttemptRepository;
import com.project2.ism.Repository.PaymentRoutingAuditRepository;
import com.project2.ism.Repository.PaymentRoutingSettingRepository;
import com.project2.ism.Repository.PaymentVendorRepository;
import com.project2.ism.Repository.PayoutTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which payout flow is used: OLD (the flow as it has always been), PILOT (the new flow only for the accounts on a
 * list) or ON (the new flow for everyone). Starts as OLD and is changed from the routing page, without a restart.
 *
 * A payout only reads the in-memory copy here, never the database, so this can never slow or break a payout.
 */
@Service
public class PayoutEngineSwitch {

    private static final Logger log = LoggerFactory.getLogger(PayoutEngineSwitch.class);

    public enum Mode {OLD, PILOT, ON}

    static final String KEY_MODE = "payout.engine-mode";
    static final String KEY_PILOT = "payout.engine-pilot";
    /** When on, the new flow follows each account's routing (vendor, limits, retries). Off by default. */
    static final String KEY_ROUTING = "payout.routing-enabled";

    private final PaymentRoutingSettingRepository settingRepository;
    private final PaymentRoutingAuditRepository auditRepository;
    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PayoutTransactionRepository payoutRepository;
    private final PaymentVendorRepository vendorRepository;

    private volatile Mode mode = Mode.OLD;
    private volatile Set<String> pilot = Set.of();
    private volatile boolean routing = false;

    public PayoutEngineSwitch(PaymentRoutingSettingRepository settingRepository,
                              PaymentRoutingAuditRepository auditRepository,
                              MerchantRepository merchantRepository,
                              FranchiseRepository franchiseRepository,
                              PaymentAttemptRepository attemptRepository,
                              PayoutTransactionRepository payoutRepository,
                              PaymentVendorRepository vendorRepository) {
        this.settingRepository = settingRepository;
        this.auditRepository = auditRepository;
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
        this.attemptRepository = attemptRepository;
        this.payoutRepository = payoutRepository;
        this.vendorRepository = vendorRepository;
    }

    // =====================================================================
    // Read by the payout (memory only)
    // =====================================================================

    public Mode mode() {
        return mode;
    }

    /** Should the new flow follow the routing settings? (memory only) */
    public boolean routingEnabled() {
        return routing;
    }

    /** Should this account's payout use the new flow? */
    public boolean useV2(String accountType, Long accountId) {
        Mode m = mode;
        if (m == Mode.ON) {
            return true;
        }
        if (m == Mode.PILOT && accountType != null && accountId != null) {
            return pilot.contains(accountType.toUpperCase() + ":" + accountId);
        }
        return false;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 5_000)
    void refresh() {
        try {
            Mode m = settingRepository.findById(KEY_MODE).map(s -> parseMode(s.getValue())).orElse(Mode.OLD);
            Set<String> p = settingRepository.findById(KEY_PILOT).map(s -> parsePilot(s.getValue())).orElse(Set.of());
            boolean r = settingRepository.findById(KEY_ROUTING).map(s -> parseFlag(s.getValue())).orElse(false);
            mode = m;
            pilot = p;
            routing = r;
        } catch (Throwable t) {
            log.warn("Payout engine: could not read the switch, keeping {}: {}", mode, t.toString());
        }
    }

    // =====================================================================
    // Admin
    // =====================================================================

    public PayoutEngineStatus status() {
        List<AssignmentDto> accounts = new ArrayList<>();
        Set<String> current = pilot;
        Map<String, String> names = namesOf(current.stream().map(PayoutEngineSwitch::toTarget).toList());
        for (String key : current) {
            TargetRef t = toTarget(key);
            accounts.add(new AssignmentDto(t.type(), t.id(), names.getOrDefault(key, "#" + t.id()), null));
        }
        return new PayoutEngineStatus(mode.name(), accounts, routing);
    }

    /** Saves the flow and the pilot list; the "use routing" switch stays as it is. */
    public PayoutEngineStatus save(String modeText, List<TargetRef> pilotAccounts) {
        return save(modeText, pilotAccounts, null);
    }

    /** useRouting null = leave that switch as it is. */
    public PayoutEngineStatus save(String modeText, List<TargetRef> pilotAccounts, Boolean useRouting) {
        Mode newMode;
        try {
            newMode = Mode.valueOf(modeText == null ? "" : modeText.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Mode must be OLD, PILOT or ON");
        }

        Set<String> keys = new LinkedHashSet<>();
        if (pilotAccounts != null) {
            for (TargetRef t : pilotAccounts) {
                if (t == null || t.id() == null || t.type() == null) {
                    throw new IllegalArgumentException("A pilot account has no type or id");
                }
                String type = t.type().trim().toUpperCase();
                if (!CustomerRef.MERCHANT.equals(type) && !CustomerRef.FRANCHISE.equals(type)) {
                    throw new IllegalArgumentException("Account type must be MERCHANT or FRANCHISE");
                }
                keys.add(type + ":" + t.id());
            }
        }
        Map<String, String> names = namesOf(keys.stream().map(PayoutEngineSwitch::toTarget).toList());
        for (String key : keys) {
            if (!names.containsKey(key)) {
                throw new IllegalArgumentException("No account found for " + key.toLowerCase().replace(":", " #"));
            }
        }
        if (newMode == Mode.PILOT && keys.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one pilot account for the Pilot mode");
        }

        String actor = actor();
        store(KEY_MODE, newMode.name(), actor);
        store(KEY_PILOT, String.join(",", keys), actor);
        if (useRouting != null) {
            store(KEY_ROUTING, useRouting ? "true" : "false", actor);
            routing = useRouting;
        }
        mode = newMode;
        pilot = keys;

        PaymentRoutingAudit audit = new PaymentRoutingAudit();
        audit.setAction("PAYOUT_ENGINE");
        audit.setActor(actor);
        audit.setDetails("Payout engine: " + newMode + " | routing for payouts: " + (routing ? "ON" : "OFF")
                + (keys.isEmpty() ? "" : " | pilot: "
                + keys.stream().map(k -> names.getOrDefault(k, k)).collect(Collectors.joining(", "))));
        auditRepository.save(audit);
        log.info("Payout engine set to {} by {} | pilot={}", newMode, actor, keys);
        return status();
    }

    private void store(String key, String value, String actor) {
        PaymentRoutingSetting setting = settingRepository.findById(key)
                .orElseGet(() -> new PaymentRoutingSetting(key, value, actor));
        setting.setValue(value);
        setting.setUpdatedBy(actor);
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
    }

    /** The newest attempts, with the payout and account each belongs to (admin only). */
    public Page<AttemptRow> attempts(int page, int size) {
        Page<PaymentAttempt> result = attemptRepository.recent(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<Long> payoutIds = result.getContent().stream().map(PaymentAttempt::getSourceId).toList();
        Map<Long, PayoutTransaction> payouts = payoutRepository.findAllById(payoutIds).stream()
                .collect(Collectors.toMap(PayoutTransaction::getId, p -> p, (a, b) -> a));
        List<TargetRef> accounts = payouts.values().stream()
                .map(p -> new TargetRef(p.getInitiatorType(), p.getInitiatorId())).toList();
        Map<String, String> names = namesOf(accounts);
        Map<Long, String> vendors = new HashMap<>();
        vendorRepository.findAllById(result.getContent().stream().map(PaymentAttempt::getVendorId).distinct().toList())
                .forEach((PaymentVendor v) -> vendors.put(v.getId(), v.getVendorName()));

        return result.map(a -> {
            PayoutTransaction p = payouts.get(a.getSourceId());
            String key = p == null ? null : p.getInitiatorType() + ":" + p.getInitiatorId();
            return new AttemptRow(a.getId(), a.getCreatedAt(), p != null ? p.getMerchantRefId() : null,
                    p != null ? p.getInitiatorType() : null, p != null ? p.getInitiatorId() : null,
                    key != null ? names.getOrDefault(key, "#" + p.getInitiatorId()) : null,
                    p != null ? p.getAmount() : null, a.getAttemptNo(), vendors.get(a.getVendorId()),
                    shownStatus(a, p), a.getResponseCode(), a.getMessage(), a.getVendorTxnId(),
                    p != null && p.getStatus() != null ? p.getStatus().name() : null);
        });
    }

    /**
     * A try that is still open (PENDING / CREATED / UNKNOWN) on a payout that has since been settled, by a callback
     * or by an admin's manual update, is shown with the payout's final result. Display only; nothing is saved.
     */
    static String shownStatus(PaymentAttempt a, PayoutTransaction p) {
        String status = a.getStatus();
        boolean open = PaymentAttempt.PENDING.equals(status) || PaymentAttempt.CREATED.equals(status)
                || PaymentAttempt.UNKNOWN.equals(status);
        if (open && p != null && p.getStatus() != null) {
            if (p.getStatus() == PayoutTransaction.PayoutStatus.SUCCESS) {
                return PaymentAttempt.SUCCESS;
            }
            if (p.getStatus() == PayoutTransaction.PayoutStatus.FAILED) {
                return PaymentAttempt.FAILED;
            }
        }
        return status;
    }

    // =====================================================================

    private Map<String, String> namesOf(List<TargetRef> targets) {
        Map<String, String> names = new HashMap<>();
        List<Long> merchantIds = targets.stream().filter(t -> CustomerRef.MERCHANT.equalsIgnoreCase(t.type())).map(TargetRef::id).toList();
        List<Long> franchiseIds = targets.stream().filter(t -> CustomerRef.FRANCHISE.equalsIgnoreCase(t.type())).map(TargetRef::id).toList();
        if (!merchantIds.isEmpty()) {
            merchantRepository.findAllById(merchantIds).forEach(m -> names.put(CustomerRef.MERCHANT + ":" + m.getId(),
                    m.getBusinessName() != null ? m.getBusinessName() : "Merchant #" + m.getId()));
        }
        if (!franchiseIds.isEmpty()) {
            franchiseRepository.findAllById(franchiseIds).forEach(f -> names.put(CustomerRef.FRANCHISE + ":" + f.getId(),
                    f.getFranchiseName() != null ? f.getFranchiseName() : "Franchise #" + f.getId()));
        }
        return names;
    }

    private static TargetRef toTarget(String key) {
        int i = key.indexOf(':');
        return new TargetRef(key.substring(0, i), Long.valueOf(key.substring(i + 1)));
    }

    static Mode parseMode(String value) {
        try {
            return Mode.valueOf(value == null ? "OLD" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mode.OLD;       // anything unreadable means the old flow: the safe one
        }
    }

    static boolean parseFlag(String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());      // anything unreadable means off
    }

    static Set<String> parsePilot(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (value != null) {
            for (String part : value.split(",")) {
                String p = part.trim().toUpperCase();
                if (p.matches("(MERCHANT|FRANCHISE):\\d+")) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getName() != null ? a.getName() : "system";
    }
}
