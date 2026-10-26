package com.project2.ism.Controller;

import com.project2.ism.Model.CreditCard.CreditCardApiLog;
import com.project2.ism.Service.CreditCardApiLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Admin > Logs > Credit Card Logs. Admin / super admin only (see SecurityConfig).
 */
@RestController
@RequestMapping("/admin-logs/credit-card")
public class CreditCardLogController {

    private static final int MAX_PAGE_SIZE = 200;

    private final CreditCardApiLogService logService;

    public CreditCardLogController(CreditCardApiLogService logService) {
        this.logService = logService;
    }

    /** All credit card API calls. Filters: status (outcome), merchantId, requestId, start / end date. */
    @GetMapping
    public ResponseEntity<Page<CreditCardApiLog>> all(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(search(null, page, size, status, merchantId, requestId, start, end));
    }

    /** Same, for one kind of call: FETCH_BILLERS, FETCH_BILL, PAY_BILL, RAISE_COMPLAINT, COMPLAINT_STATUS. */
    @GetMapping("/action/{action}")
    public ResponseEntity<Page<CreditCardApiLog>> byAction(
            @PathVariable String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(search(action, page, size, status, merchantId, requestId, start, end));
    }

    private Page<CreditCardApiLog> search(String action, int page, int size, String status, Long merchantId,
                                          String requestId, LocalDate start, LocalDate end) {
        // The dashboard sends status=all for "no filter".
        String outcome = (status == null || "all".equalsIgnoreCase(status)) ? null : status;

        return logService.search(action, outcome, merchantId, requestId,
                start != null ? start.atStartOfDay() : null,
                end != null ? end.atTime(23, 59, 59) : null,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
    }
}
