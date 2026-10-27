package com.project2.ism.Controller;

import com.project2.ism.Service.CallerIdentityService;
import com.project2.ism.Service.CustomerRef;
import com.project2.ism.Service.FranchisePayoutReportService;
import com.project2.ism.Service.FranchisePayoutReportService.Filters;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payout report for franchises (see SecurityConfig). The franchise is the logged-in user:
 * no parameter says "whose report", so one franchise can never read another's.
 */
@RestController
@RequestMapping("/reports/payout/franchise")
public class FranchisePayoutReportController {

    private static final Logger log = LoggerFactory.getLogger(FranchisePayoutReportController.class);

    private final FranchisePayoutReportService reportService;
    private final CallerIdentityService callerIdentity;

    public FranchisePayoutReportController(FranchisePayoutReportService reportService,
                                           CallerIdentityService callerIdentity) {
        this.reportService = reportService;
        this.callerIdentity = callerIdentity;
    }

    @GetMapping
    public ResponseEntity<?> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        CustomerRef me = callerIdentity.currentCustomer();
        if (!me.isFranchise()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only franchises can use this report");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", reportService.getReport(me.id(),
                new Filters(startDate, endDate, status, customerType, customerId, paymentMode, search), page, size));
        return ResponseEntity.ok(body);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("Franchise payout report rejected | {} | reason={}", request.getRequestURI(), e.getMessage());
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e, HttpServletRequest request) {
        log.warn("Franchise payout report blocked | {} | status={} | reason={}",
                request.getRequestURI(), e.getStatusCode().value(), e.getReason());
        return error(HttpStatus.valueOf(e.getStatusCode().value()),
                e.getReason() != null ? e.getReason() : "Request not allowed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e, HttpServletRequest request) {
        String reference = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Franchise payout report UNEXPECTED ERROR | reference={} | {}", reference, request.getRequestURI(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not load the report. Please try again, and quote reference " + reference + " if it continues.");
    }
}
