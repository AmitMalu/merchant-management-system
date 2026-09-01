package com.project2.ism.Controller;

import com.project2.ism.Enum.AlertSeverity;
import com.project2.ism.Enum.AlertStatus;
import com.project2.ism.Model.Monitoring.Alert;
import com.project2.ism.Model.Monitoring.MonitoringRule;
import com.project2.ism.Service.Monitoring.AlertService;
import com.project2.ism.Service.Monitoring.MonitoringDashboardService;
import com.project2.ism.Service.Monitoring.MonitoringRuleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitoring")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class TransactionMonitoringController {

    private final MonitoringDashboardService dashboardService;
    private final AlertService alertService;
    private final MonitoringRuleService ruleService;

    public TransactionMonitoringController(MonitoringDashboardService dashboardService,
                                            AlertService alertService,
                                            MonitoringRuleService ruleService) {
        this.dashboardService = dashboardService;
        this.alertService = alertService;
        this.ruleService = ruleService;
    }

    // ==================== DASHBOARD ====================

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    // ==================== ALERTS ====================

    @GetMapping("/alerts")
    public ResponseEntity<Page<Alert>> listAlerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String merchant,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Alert> alerts = alertService.listAlerts(status, severity, startDate, endDate, merchant, PageRequest.of(page, size));
        return ResponseEntity.ok(alerts);
    }

    @PutMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<Alert> acknowledge(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.acknowledge(id));
    }

    @PutMapping("/alerts/{id}/resolve")
    public ResponseEntity<Alert> resolve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(alertService.resolve(id, notes));
    }

    @PutMapping("/alerts/{id}/false-positive")
    public ResponseEntity<Alert> markFalsePositive(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(alertService.markFalsePositive(id, notes));
    }

    @GetMapping("/alerts/export")
    public ResponseEntity<byte[]> exportAlerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String merchant) {

        String csv = alertService.exportAlertsCsv(status, severity, ruleId, startDate, endDate, merchant);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transaction-monitoring-alerts.csv")
                .body(body);
    }

    // ==================== RULES ====================

    @GetMapping("/rules")
    public ResponseEntity<List<MonitoringRule>> listRules() {
        return ResponseEntity.ok(ruleService.listAll());
    }

    @PostMapping("/rules")
    public ResponseEntity<MonitoringRule> createRule(@RequestBody MonitoringRule rule) {
        return ResponseEntity.ok(ruleService.create(rule));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<MonitoringRule> updateRule(@PathVariable Long id, @RequestBody MonitoringRule rule) {
        return ResponseEntity.ok(ruleService.update(id, rule));
    }

    @PutMapping("/rules/{id}/active")
    public ResponseEntity<Void> setActive(@PathVariable Long id, @RequestParam boolean active) {
        ruleService.setActive(id, active);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        ruleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
