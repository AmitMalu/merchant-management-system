package com.project2.ism.Service.Monitoring;

import com.project2.ism.Enum.AlertSeverity;
import com.project2.ism.Enum.MonitoringRuleType;
import com.project2.ism.Model.Monitoring.MonitoringRule;
import com.project2.ism.Repository.MonitoringRuleRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MonitoringRuleService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringRuleService.class);

    private final MonitoringRuleRepository monitoringRuleRepository;

    public MonitoringRuleService(MonitoringRuleRepository monitoringRuleRepository) {
        this.monitoringRuleRepository = monitoringRuleRepository;
    }

    /**
     * Seed a handful of sensible starter rules the first time this feature
     * runs (table is brand new, so this only ever inserts once — same
     * idempotent "insert if table empty" pattern is safe to leave running on
     * every boot). Ops can edit/deactivate/replace these via the rules API
     * once live; nothing here is hardcoded into the evaluation logic itself.
     */
    @PostConstruct
    public void seedDefaultRulesIfEmpty() {
        if (monitoringRuleRepository.count() > 0) {
            return;
        }

        log.info("No monitoring rules found — seeding default starter rules");

        MonitoringRule largePayout = new MonitoringRule();
        largePayout.setName("Large Payout");
        largePayout.setDescription("Flags any single payout of ₹1,00,000 or more for review.");
        largePayout.setRuleType(MonitoringRuleType.AMOUNT_THRESHOLD);
        largePayout.setSourceType(com.project2.ism.Enum.TransactionSourceType.PAYOUT);
        largePayout.setParameters("{\"minAmount\": 100000}");
        largePayout.setSeverity(AlertSeverity.HIGH);
        monitoringRuleRepository.save(largePayout);

        MonitoringRule payoutVelocity = new MonitoringRule();
        payoutVelocity.setName("Payout Velocity");
        payoutVelocity.setDescription("Flags a merchant/franchise initiating more than 5 payouts within 60 minutes.");
        payoutVelocity.setRuleType(MonitoringRuleType.VELOCITY);
        payoutVelocity.setSourceType(com.project2.ism.Enum.TransactionSourceType.PAYOUT);
        payoutVelocity.setParameters("{\"windowMinutes\": 60, \"maxCount\": 5}");
        payoutVelocity.setSeverity(AlertSeverity.MEDIUM);
        monitoringRuleRepository.save(payoutVelocity);

        MonitoringRule failureRate = new MonitoringRule();
        failureRate.setName("Repeated Payout Failures");
        failureRate.setDescription("Flags a merchant/franchise with more than 3 failed payouts within 60 minutes.");
        failureRate.setRuleType(MonitoringRuleType.FAILURE_RATE);
        failureRate.setSourceType(com.project2.ism.Enum.TransactionSourceType.PAYOUT);
        failureRate.setParameters("{\"windowMinutes\": 60, \"maxFailures\": 3}");
        failureRate.setSeverity(AlertSeverity.MEDIUM);
        monitoringRuleRepository.save(failureRate);

        MonitoringRule stuckPayout = new MonitoringRule();
        stuckPayout.setName("Stuck Payout");
        stuckPayout.setDescription("Flags a payout still PENDING 30 minutes after initiation (vendor callback likely lost).");
        stuckPayout.setRuleType(MonitoringRuleType.STUCK_PENDING);
        stuckPayout.setSourceType(com.project2.ism.Enum.TransactionSourceType.PAYOUT);
        stuckPayout.setParameters("{\"stuckMinutes\": 30}");
        stuckPayout.setSeverity(AlertSeverity.HIGH);
        monitoringRuleRepository.save(stuckPayout);

        MonitoringRule stuckBbps = new MonitoringRule();
        stuckBbps.setName("Stuck BBPS Transaction");
        stuckBbps.setDescription("Flags a BBPS bill payment still PENDING 30 minutes after initiation.");
        stuckBbps.setRuleType(MonitoringRuleType.STUCK_PENDING);
        stuckBbps.setSourceType(com.project2.ism.Enum.TransactionSourceType.BBPS);
        stuckBbps.setParameters("{\"stuckMinutes\": 30}");
        stuckBbps.setSeverity(AlertSeverity.HIGH);
        monitoringRuleRepository.save(stuckBbps);

        MonitoringRule largeBbps = new MonitoringRule();
        largeBbps.setName("Large BBPS Payment");
        largeBbps.setDescription("Flags any single BBPS bill payment of ₹50,000 or more for review.");
        largeBbps.setRuleType(MonitoringRuleType.AMOUNT_THRESHOLD);
        largeBbps.setSourceType(com.project2.ism.Enum.TransactionSourceType.BBPS);
        largeBbps.setParameters("{\"minAmount\": 50000}");
        largeBbps.setSeverity(AlertSeverity.MEDIUM);
        monitoringRuleRepository.save(largeBbps);

        log.info("Seeded 6 default monitoring rules");
    }

    public List<MonitoringRule> listAll() {
        return monitoringRuleRepository.findAll();
    }

    public MonitoringRule getById(Long id) {
        return monitoringRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Monitoring rule not found: " + id));
    }

    public MonitoringRule create(MonitoringRule rule) {
        rule.setId(null);
        return monitoringRuleRepository.save(rule);
    }

    public MonitoringRule update(Long id, MonitoringRule updated) {
        MonitoringRule existing = getById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setRuleType(updated.getRuleType());
        existing.setSourceType(updated.getSourceType());
        existing.setParameters(updated.getParameters());
        existing.setSeverity(updated.getSeverity());
        existing.setActive(updated.isActive());
        return monitoringRuleRepository.save(existing);
    }

    public void setActive(Long id, boolean active) {
        MonitoringRule rule = getById(id);
        rule.setActive(active);
        monitoringRuleRepository.save(rule);
    }

    public void delete(Long id) {
        monitoringRuleRepository.deleteById(id);
    }
}
