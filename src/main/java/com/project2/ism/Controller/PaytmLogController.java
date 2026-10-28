package com.project2.ism.Controller;

import com.project2.ism.Model.Logs.PaytmNotificationLog;
import com.project2.ism.Service.PaytmNotificationLogService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Admin > Logs > Paytm Notification Logs. Admin / super admin only (see SecurityConfig).
 */
@RestController
@RequestMapping("/admin-logs/paytm")
public class PaytmLogController {

    private final PaytmNotificationLogService logService;

    public PaytmLogController(PaytmNotificationLogService logService) {
        this.logService = logService;
    }

    /** Filters: status (RECEIVED / SUCCESS / FAILED, or "all"), reference (order id or txn id), start / end date. */
    @GetMapping
    public ResponseEntity<Page<PaytmNotificationLog>> all(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(logService.search(status, reference, start, end, page, size));
    }
}
