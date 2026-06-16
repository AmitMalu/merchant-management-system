package com.project2.ism.Controller;

import com.project2.ism.Model.*;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationLog;
import com.project2.ism.Model.Logs.RazorpayNotificationLog;
import com.project2.ism.Model.Logs.DigilockerLog;
import com.project2.ism.Model.Logs.MosambeeNotificationLog;
import com.project2.ism.Model.Payment.PaymentVendorLog;
import com.project2.ism.Model.Payment.PaymentVendorMonthlyLog;
import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import com.project2.ism.Service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin-logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    // ==================== REQUEST LOGS ====================

    @GetMapping("/requests")
    public ResponseEntity<Page<RequestLog>> getRequestLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);

        if (start != null && end != null) {
            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(23, 59, 59);
            return ResponseEntity.ok(logService.getRequestLogsByDateRange(startDateTime, endDateTime, pageable));
        }

        return ResponseEntity.ok(logService.getRequestLogs(pageable));
    }

    @GetMapping("/requests/errors")
    public ResponseEntity<Page<RequestLog>> getErrorRequestLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);

        if (start != null && end != null) {
            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(23, 59, 59);
            Page<RequestLog> allLogs = logService.getRequestLogsByDateRange(startDateTime, endDateTime, pageable);
            // Filter for errors - this could be optimized with a custom query if needed
            return ResponseEntity.ok(logService.getErrorRequestLogs(pageable));
        }

        return ResponseEntity.ok(logService.getErrorRequestLogs(pageable));
    }

    @GetMapping("/requests/slow")
    public ResponseEntity<Page<RequestLog>> getSlowRequestLogs(
            @RequestParam(defaultValue = "2000") Long thresholdMs,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(logService.getSlowRequestLogs(thresholdMs, pageable));
    }

    @GetMapping("/requests/status/{status}")
    public ResponseEntity<Page<RequestLog>> getRequestLogsByStatus(
            @PathVariable Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);

        if (start != null && end != null) {
            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(23, 59, 59);
            return ResponseEntity.ok(logService.searchRequestLogs(null, null, status, startDateTime, endDateTime, pageable));
        }

        return ResponseEntity.ok(logService.getRequestLogsByStatus(status, pageable));
    }

    // ==================== RAZORPAY NOTIFICATION LOGS ====================

    @GetMapping("/razorpay")
    public ResponseEntity<Page<RazorpayNotificationLog>> getRazorpayLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);

        if (start != null && end != null) {
            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(23, 59, 59);
            return ResponseEntity.ok(logService.getRazorpayLogsByDateRange(startDateTime, endDateTime, pageable));
        }

        return ResponseEntity.ok(logService.getRazorpayLogs(pageable));
    }

    @GetMapping("/razorpay/failed")
    public ResponseEntity<Page<RazorpayNotificationLog>> getRazorpayFailedLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);

        if (start != null && end != null) {
            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(23, 59, 59);
            Page<RazorpayNotificationLog> logs = logService.getRazorpayLogsByDateRange(startDateTime, endDateTime, pageable);
            // Could be optimized with a custom query combining date range and status filter
            return ResponseEntity.ok(logService.getRazorpayFailedLogs(pageable));
        }

        return ResponseEntity.ok(logService.getRazorpayFailedLogs(pageable));
    }

    @GetMapping("/razorpay/status/{status}")
    public ResponseEntity<Page<RazorpayNotificationLog>> getRazorpayLogsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(logService.getRazorpayLogsByStatus(status, pageable));
    }

    @GetMapping("/franchise-or-merchant-notification")
    public ResponseEntity<Page<FranchiseOrMerchantNotificationLog>>
    getFranchiseOrMerchantNotificationLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "all") String status) {

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                logService.getFranchiseOrMerchantNotificationLogs(pageable, start, end, status)
        );
    }

    // ==================== PAYMENT VENDOR RESPONSE LOGS ====================

    @GetMapping("/vendor-responses")
    public ResponseEntity<Page<PaymentVendorResponseLog>> getVendorResponseLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);

        if (start != null && end != null) {
            LocalDateTime startDateTime = start.atStartOfDay();
            LocalDateTime endDateTime = end.atTime(23, 59, 59);
            return ResponseEntity.ok(logService.getVendorResponseLogsByDateRange(startDateTime, endDateTime, pageable));
        }

        return ResponseEntity.ok(logService.getVendorResponseLogs(pageable));
    }

    @GetMapping("/vendor-responses/status/{statusCode}")
    public ResponseEntity<Page<PaymentVendorResponseLog>> getVendorResponseLogsByStatus(
            @PathVariable Integer statusCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(logService.getVendorResponseLogsByStatus(statusCode, pageable));
    }

    // ==================== PAYMENT VENDOR LOGS (DAILY) ====================

    @GetMapping("/vendor-daily")
    public ResponseEntity<Page<PaymentVendorLog>> getVendorDailyLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);

        if (start != null && end != null) {
            // For daily logs, we need to handle this differently since it uses LocalDate
            // We'll need to add a new service method or handle it here
            return ResponseEntity.ok(logService.getVendorDailyLogs(pageable));
        }

        return ResponseEntity.ok(logService.getVendorDailyLogs(pageable));
    }

    // ==================== PAYMENT VENDOR MONTHLY LOGS ====================

    @GetMapping("/vendor-monthly")
    public ResponseEntity<Page<PaymentVendorMonthlyLog>> getVendorMonthlyLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(logService.getVendorMonthlyLogs(pageable));
    }

    // ==================== DIGILOCKER LOGS ====================

    @GetMapping("/digilocker")
    public ResponseEntity<Page<DigilockerLog>> getDigilockerLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long franchiseId,
            @RequestParam(required = false) String apiName) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(logService.getDigilockerLogs(
                pageable, start, end, status, clientId, merchantId, franchiseId, apiName
        ));
    }

    // ==================== MOSAMBEE LOGS ====================

    @GetMapping("/mosambee")
    public ResponseEntity<Page<MosambeeNotificationLog>> getMosambeeLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) String txnId) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(logService.getMosambeeLogs(
                pageable, start, end, status, txnId
        ));
    }

}