package com.project2.ism.Service.Monitoring;

import com.project2.ism.Enum.AlertSeverity;
import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Model.Monitoring.TransactionEvent;
import com.project2.ism.Repository.TransactionEventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MonitoringDashboardService {

    private final TransactionEventRepository transactionEventRepository;
    private final AlertService alertService;

    public MonitoringDashboardService(TransactionEventRepository transactionEventRepository,
                                       AlertService alertService) {
        this.transactionEventRepository = transactionEventRepository;
        this.alertService = alertService;
    }

    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        Map<String, Long> openBySeverity = new LinkedHashMap<>();
        for (AlertSeverity sev : AlertSeverity.values()) {
            openBySeverity.put(sev.name(), alertService.countOpenBySeverity(sev));
        }
        summary.put("openAlertsBySeverity", openBySeverity);
        summary.put("openAlertsTotal", alertService.countOpen());

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Object[]> rows = transactionEventRepository.countBySourceTypeAndStatusSince(since);

        // sourceType -> status -> count
        Map<String, Map<String, Long>> statusCounts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            TransactionSourceType sourceType = (TransactionSourceType) row[0];
            TransactionEventStatus status = (TransactionEventStatus) row[1];
            Long count = (Long) row[2];

            statusCounts
                    .computeIfAbsent(sourceType.name(), k -> new LinkedHashMap<>())
                    .put(status.name(), count);
        }
        summary.put("last24hBySourceAndStatus", statusCounts);

        List<Object[]> cardRows = transactionEventRepository.countByCardBrandSince(since);
        List<Map<String, Object>> cardBrandBreakdown = new ArrayList<>();
        for (Object[] row : cardRows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("cardBrand", row[0]);
            entry.put("count", row[1]);
            entry.put("amount", row[2]);
            cardBrandBreakdown.add(entry);
        }
        summary.put("last24hByCardBrand", cardBrandBreakdown);

        List<TransactionEvent> recent = transactionEventRepository.findTop50ByOrderByOccurredAtDesc();
        summary.put("recentEvents", recent);

        return summary;
    }
}
