package com.project2.ism.Service;

import com.project2.ism.DTO.PaymentDTO.RoutingDtos.AssignmentDto;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.MenuRulesStatus;
import com.project2.ism.DTO.PaymentDTO.RoutingDtos.TargetRef;
import com.project2.ism.Model.Routing.PaymentRoutingAudit;
import com.project2.ism.Model.Routing.PaymentRoutingSetting;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.PaymentRoutingAuditRepository;
import com.project2.ism.Repository.PaymentRoutingSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Menu Rules: for which accounts the menus follow routing. OFF (the default): every account sees every menu, as
 * always. PILOT: only the accounts on a list. ON: every account. Changed from the routing page, without a restart.
 *
 * Payments and the sidebar only read the in-memory copy here (never the database), refreshed every 15 seconds.
 */
@Service
public class MenuRulesSwitch {

    private static final Logger log = LoggerFactory.getLogger(MenuRulesSwitch.class);

    public enum Mode {OFF, PILOT, ON}

    static final String KEY_MODE = "menu.rules-mode";
    static final String KEY_ACCOUNTS = "menu.rules-accounts";

    private final PaymentRoutingSettingRepository settingRepository;
    private final PaymentRoutingAuditRepository auditRepository;
    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;

    private volatile Mode mode = Mode.OFF;
    private volatile Set<String> accounts = Set.of();

    public MenuRulesSwitch(PaymentRoutingSettingRepository settingRepository,
                           PaymentRoutingAuditRepository auditRepository,
                           MerchantRepository merchantRepository,
                           FranchiseRepository franchiseRepository) {
        this.settingRepository = settingRepository;
        this.auditRepository = auditRepository;
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
    }

    public Mode mode() {
        return mode;
    }

    /** Do the menu rules apply to this account? (memory only) */
    public boolean applies(CustomerRef who) {
        if (who == null || who.id() == null || who.type() == null) {
            return false;
        }
        Mode m = mode;
        if (m == Mode.ON) {
            return true;
        }
        return m == Mode.PILOT && accounts.contains(who.type().toUpperCase() + ":" + who.id());
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 6_000)
    void refresh() {
        try {
            Mode m = settingRepository.findById(KEY_MODE).map(s -> parseMode(s.getValue())).orElse(Mode.OFF);
            Set<String> a = settingRepository.findById(KEY_ACCOUNTS).map(s -> PayoutEngineSwitch.parsePilot(s.getValue()))
                    .orElse(Set.of());
            mode = m;
            accounts = a;
        } catch (Throwable t) {
            log.warn("Menu rules: could not read the switch, keeping {}: {}", mode, t.toString());
        }
    }

    // =====================================================================
    // Admin
    // =====================================================================

    public MenuRulesStatus status() {
        Set<String> current = accounts;
        List<TargetRef> targets = current.stream().map(MenuRulesSwitch::toTarget).toList();
        Map<String, String> names = namesOf(targets);
        List<AssignmentDto> out = new ArrayList<>();
        for (TargetRef t : targets) {
            String key = t.type() + ":" + t.id();
            out.add(new AssignmentDto(t.type(), t.id(), names.getOrDefault(key, "#" + t.id()), null));
        }
        return new MenuRulesStatus(mode.name(), out);
    }

    public MenuRulesStatus save(String modeText, List<TargetRef> accountList) {
        Mode newMode;
        try {
            newMode = Mode.valueOf(modeText == null ? "" : modeText.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Mode must be OFF, PILOT or ON");
        }

        Set<String> keys = new LinkedHashSet<>();
        if (accountList != null) {
            for (TargetRef t : accountList) {
                if (t == null || t.id() == null || t.type() == null) {
                    throw new IllegalArgumentException("An account has no type or id");
                }
                String type = t.type().trim().toUpperCase();
                if (!CustomerRef.MERCHANT.equals(type) && !CustomerRef.FRANCHISE.equals(type)) {
                    throw new IllegalArgumentException("Account type must be MERCHANT or FRANCHISE");
                }
                keys.add(type + ":" + t.id());
            }
        }
        Map<String, String> names = namesOf(keys.stream().map(MenuRulesSwitch::toTarget).toList());
        for (String key : keys) {
            if (!names.containsKey(key)) {
                throw new IllegalArgumentException("No account found for " + key.toLowerCase().replace(":", " #"));
            }
        }
        if (newMode == Mode.PILOT && keys.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one account for the Only these accounts mode");
        }

        String actor = actor();
        store(KEY_MODE, newMode.name(), actor);
        store(KEY_ACCOUNTS, String.join(",", keys), actor);
        mode = newMode;
        accounts = keys;

        PaymentRoutingAudit audit = new PaymentRoutingAudit();
        audit.setAction("MENU_RULES");
        audit.setActor(actor);
        audit.setDetails("Menu rules: " + newMode + (keys.isEmpty() ? "" : " | accounts: "
                + keys.stream().map(k -> names.getOrDefault(k, k)).collect(Collectors.joining(", "))));
        auditRepository.save(audit);
        log.info("Menu rules set to {} by {} | accounts={}", newMode, actor, keys);
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
            return Mode.valueOf(value == null ? "OFF" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mode.OFF;       // anything unreadable means the rule is off: every menu is shown
        }
    }

    private static String actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getName() != null ? a.getName() : "system";
    }
}
