package com.project2.ism.Controller;

import com.project2.ism.Service.CallerIdentityService;
import com.project2.ism.Service.CreditCardReportService;
import com.project2.ism.Service.CreditCardReportService.Filters;
import com.project2.ism.Service.CreditCardReportService.Viewer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * Credit card bill payment report for admins, franchises and merchants (see SecurityConfig).
 * What each role can see is decided in CreditCardReportService from the logged-in user;
 * nothing here accepts "whose report" from the request.
 */
@RestController
@RequestMapping("/reports/credit-card")
public class CreditCardReportController {

    private static final Logger log = LoggerFactory.getLogger(CreditCardReportController.class);

    private final CreditCardReportService reportService;
    private final CallerIdentityService callerIdentity;

    public CreditCardReportController(CreditCardReportService reportService, CallerIdentityService callerIdentity) {
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
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Viewer viewer = isAdmin() ? new Viewer(true, null) : new Viewer(false, callerIdentity.currentCustomer());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", reportService.getReport(viewer,
                new Filters(startDate, endDate, status, customerType, customerId, search), page, size));
        return ResponseEntity.ok(body);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
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
        log.warn("Credit card report rejected | {} | reason={}", request.getRequestURI(), e.getMessage());
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e, HttpServletRequest request) {
        log.warn("Credit card report blocked | {} | status={} | reason={}",
                request.getRequestURI(), e.getStatusCode().value(), e.getReason());
        return error(HttpStatus.valueOf(e.getStatusCode().value()),
                e.getReason() != null ? e.getReason() : "Request not allowed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e, HttpServletRequest request) {
        String reference = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Credit card report UNEXPECTED ERROR | reference={} | {}", reference, request.getRequestURI(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not load the report. Please try again, and quote reference " + reference + " if it continues.");
    }
}
