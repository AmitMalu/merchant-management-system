package com.project2.ism.Service.Monitoring;

import com.project2.ism.Enum.AlertSeverity;
import com.project2.ism.Enum.AlertStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Model.Monitoring.Alert;
import com.project2.ism.Repository.AlertRepository;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final MailService mailService;
    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;

    // Comma-separated recipient list for CRITICAL/HIGH alert emails. Empty by
    // default so this feature works out of the box without SMTP being
    // reconfigured; set monitoring.alert.recipients in application.properties
    // to enable email notifications.
    @Value("${monitoring.alert.recipients:}")
    private String alertRecipientsRaw;

    public AlertService(AlertRepository alertRepository, MailService mailService,
                         MerchantRepository merchantRepository, FranchiseRepository franchiseRepository) {
        this.alertRepository = alertRepository;
        this.mailService = mailService;
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
    }

    public Alert raiseAlert(Long ruleId, String ruleName, Long transactionEventId, TransactionSourceType sourceType,
                             String initiatorType, Long initiatorId, BigDecimal amount,
                             AlertSeverity severity, String message) {

        Alert alert = new Alert();
        alert.setRuleId(ruleId);
        alert.setRuleName(ruleName);
        alert.setTransactionEventId(transactionEventId);
        alert.setSourceType(sourceType);
        alert.setInitiatorType(initiatorType);
        alert.setInitiatorId(initiatorId);
        alert.setInitiatorName(resolveInitiatorName(initiatorType, initiatorId));
        alert.setAmount(amount);
        alert.setSeverity(severity);
        alert.setStatus(AlertStatus.OPEN);
        alert.setMessage(message);

        alert = alertRepository.save(alert);

        log.info("Alert raised. id={} rule={} severity={} initiator={}:{} message={}",
                alert.getId(), ruleName, severity, initiatorType, initiatorId, message);

        if (severity == AlertSeverity.CRITICAL || severity == AlertSeverity.HIGH) {
            notifyByEmail(alert);
        }

        return alert;
    }

    // Resolves the merchant/franchise business name once, at alert-creation
    // time, so the dashboard shows "TATTHA MITRA KENDRA" instead of a bare
    // id. Never throws — a name-lookup failure must not stop an alert from
    // being raised; it just falls back to showing the id in the UI.
    private String resolveInitiatorName(String initiatorType, Long initiatorId) {
        if (initiatorType == null || initiatorId == null) {
            return null;
        }
        try {
            if ("MERCHANT".equalsIgnoreCase(initiatorType)) {
                return merchantRepository.findById(initiatorId)
                        .map(m -> m.getBusinessName())
                        .orElse(null);
            }
            if ("FRANCHISE".equalsIgnoreCase(initiatorType)) {
                return franchiseRepository.findById(initiatorId)
                        .map(f -> f.getFranchiseName())
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Failed to resolve initiator name for {}:{}: {}", initiatorType, initiatorId, e.getMessage());
        }
        return null;
    }

    private void notifyByEmail(Alert alert) {
        if (alertRecipientsRaw == null || alertRecipientsRaw.isBlank()) {
            return;
        }

        List<String> recipients = List.of(alertRecipientsRaw.split(","))
                .stream().map(String::trim).filter(s -> !s.isEmpty()).toList();

        if (recipients.isEmpty()) {
            return;
        }

        String subject = "[" + alert.getSeverity() + "] Transaction Monitoring Alert — " + alert.getRuleName();
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;">
                    <div style="background:#b71c1c;padding:20px;text-align:center;">
                        <h2 style="color:#ffffff;margin:0;font-size:20px;">Transaction Monitoring Alert</h2>
                    </div>
                    <div style="padding:24px 28px;background:#ffffff;">
                        <table style="width:100%%;border-collapse:collapse;font-size:14px;">
                            <tr><td style="padding:6px 0;color:#666;">Severity</td><td style="padding:6px 0;font-weight:bold;">%s</td></tr>
                            <tr><td style="padding:6px 0;color:#666;">Rule</td><td style="padding:6px 0;">%s</td></tr>
                            <tr><td style="padding:6px 0;color:#666;">Source</td><td style="padding:6px 0;">%s</td></tr>
                            <tr><td style="padding:6px 0;color:#666;">Initiator</td><td style="padding:6px 0;">%s%s #%s</td></tr>
                            <tr><td style="padding:6px 0;color:#666;">Amount</td><td style="padding:6px 0;">%s</td></tr>
                        </table>
                        <p style="color:#333;font-size:14px;margin-top:16px;">%s</p>
                    </div>
                </div>
                """.formatted(
                alert.getSeverity(),
                alert.getRuleName(),
                alert.getSourceType(),
                alert.getInitiatorType(),
                alert.getInitiatorName() != null ? " (" + alert.getInitiatorName() + ")" : "",
                alert.getInitiatorId(),
                alert.getAmount() != null ? alert.getAmount().toPlainString() : "N/A",
                alert.getMessage()
        );

        // Fire-and-forget — an email failure must never block alert creation.
        mailService.sendHtmlEmail(recipients, subject, html);
    }

    public Page<Alert> listAlerts(AlertStatus status, AlertSeverity severity,
                                   LocalDateTime startDate, LocalDateTime endDate,
                                   String initiatorNameSearch, Pageable pageable) {
        return alertRepository.findFiltered(status, severity, startDate, endDate,
                toLikePattern(initiatorNameSearch), pageable);
    }

    private String toLikePattern(String search) {
        return (search == null || search.isBlank()) ? null : "%" + search.trim() + "%";
    }

    public Alert acknowledge(Long alertId) {
        Alert alert = getOrThrow(alertId);
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAssignedTo(currentUser());
        return alertRepository.save(alert);
    }

    public Alert resolve(Long alertId, String notes) {
        Alert alert = getOrThrow(alertId);
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolutionNotes(notes);
        alert.setResolvedAt(LocalDateTime.now());
        if (alert.getAssignedTo() == null) {
            alert.setAssignedTo(currentUser());
        }
        return alertRepository.save(alert);
    }

    public Alert markFalsePositive(Long alertId, String notes) {
        Alert alert = getOrThrow(alertId);
        alert.setStatus(AlertStatus.FALSE_POSITIVE);
        alert.setResolutionNotes(notes);
        alert.setResolvedAt(LocalDateTime.now());
        if (alert.getAssignedTo() == null) {
            alert.setAssignedTo(currentUser());
        }
        return alertRepository.save(alert);
    }

    /**
     * Auto-closes any still-OPEN or ACKNOWLEDGED alert about this exact
     * transaction row (only ever meaningful for STUCK_PENDING alerts — a
     * threshold/velocity alert's premise doesn't stop being true just
     * because the transaction later succeeded or failed, so this is never
     * called for those). Called right after a transaction's final status is
     * recorded, including the late-vendor-callback path — that's the "5-10
     * minutes late" scenario this exists for.
     */
    public void autoResolveStuckPending(TransactionSourceType sourceType, Long sourceId, String reason) {
        try {
            List<Alert> openAlerts = new ArrayList<>();
            openAlerts.addAll(alertRepository.findBySourceTypeAndTransactionEventIdAndStatus(
                    sourceType, sourceId, AlertStatus.OPEN));
            openAlerts.addAll(alertRepository.findBySourceTypeAndTransactionEventIdAndStatus(
                    sourceType, sourceId, AlertStatus.ACKNOWLEDGED));

            for (Alert alert : openAlerts) {
                alert.setStatus(AlertStatus.AUTO_RESOLVED);
                alert.setResolutionNotes(reason);
                alert.setResolvedAt(LocalDateTime.now());
                alertRepository.save(alert);

                log.info("Auto-resolved alert id={} for {}:{} — {}", alert.getId(), sourceType, sourceId, reason);
            }
        } catch (Exception e) {
            // Never let auto-resolution failures affect the real transaction flow.
            log.warn("Failed to auto-resolve alerts for {}:{}: {}", sourceType, sourceId, e.getMessage());
        }
    }

    private Alert getOrThrow(Long alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    public long countOpen() {
        return alertRepository.countByStatus(AlertStatus.OPEN);
    }

    public long countOpenBySeverity(AlertSeverity severity) {
        return alertRepository.countByStatusAndSeverity(AlertStatus.OPEN, severity);
    }

    /** Dedup guard for window-based rules (velocity/failure-rate): don't raise a
     *  second alert for the same rule+initiator while an earlier one from the
     *  same window is still open. */
    public boolean hasRecentOpenAlert(Long ruleId, String initiatorType, Long initiatorId, LocalDateTime since) {
        return !alertRepository.findByRuleIdAndInitiatorTypeAndInitiatorIdAndStatusAndCreatedAtAfter(
                ruleId, initiatorType, initiatorId, AlertStatus.OPEN, since).isEmpty();
    }

    /** Dedup guard for stuck-pending rules: don't raise a second alert for the
     *  same source row while an earlier one is still open. */
    public boolean hasOpenAlertForEvent(Long ruleId, Long transactionEventId) {
        return !alertRepository.findByRuleIdAndTransactionEventIdAndStatus(
                ruleId, transactionEventId, AlertStatus.OPEN).isEmpty();
    }

    /**
     * Builds a CSV of alerts matching the given filters, for compliance/audit
     * export. Every filter is optional; omitting all of them exports the full
     * alert history.
     */
    public String exportAlertsCsv(AlertStatus status, AlertSeverity severity, Long ruleId,
                                   LocalDateTime startDate, LocalDateTime endDate, String initiatorNameSearch) {

        List<Alert> alerts = alertRepository.findForExport(status, severity, ruleId, startDate, endDate,
                toLikePattern(initiatorNameSearch));

        StringBuilder csv = new StringBuilder();
        csv.append("Alert ID,Severity,Status,Rule,Source Type,Initiator Type,Initiator ID,Initiator Name,Amount,")
           .append("Message,Assigned To,Resolution Notes,Created At,Resolved At\n");

        for (Alert a : alerts) {
            csv.append(a.getId()).append(',')
               .append(a.getSeverity()).append(',')
               .append(a.getStatus()).append(',')
               .append(csvEscape(a.getRuleName())).append(',')
               .append(a.getSourceType()).append(',')
               .append(nullSafe(a.getInitiatorType())).append(',')
               .append(nullSafe(a.getInitiatorId())).append(',')
               .append(csvEscape(a.getInitiatorName())).append(',')
               .append(a.getAmount() != null ? a.getAmount().toPlainString() : "").append(',')
               .append(csvEscape(a.getMessage())).append(',')
               .append(csvEscape(a.getAssignedTo())).append(',')
               .append(csvEscape(a.getResolutionNotes())).append(',')
               .append(a.getCreatedAt()).append(',')
               .append(a.getResolvedAt() != null ? a.getResolvedAt() : "")
               .append('\n');
        }

        return csv.toString();
    }

    private String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
