package com.project2.ism.Controller;

import com.project2.ism.DTO.CreditCardDTO.CreditCardDtos.*;
import com.project2.ism.Exception.CreditCardBillException;
import com.project2.ism.Service.CallerIdentityService;
import com.project2.ism.Service.CreditCardBillService;
import com.project2.ism.Service.SuivrePayClient;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Credit card bill payment for merchants and franchises (see SecurityConfig).
 * The account is always the logged-in user: no endpoint accepts a merchant / franchise id.
 * Billers, fetch bill and pay are refused when the account's "credit card bill payment"
 * switch is off (checked in CreditCardBillService).
 */
@RestController
@RequestMapping("/credit-card")
public class CreditCardBillController {

    private static final Logger log = LoggerFactory.getLogger(CreditCardBillController.class);

    private final CreditCardBillService service;
    private final CallerIdentityService callerIdentity;

    public CreditCardBillController(CreditCardBillService service, CallerIdentityService callerIdentity) {
        this.service = service;
        this.callerIdentity = callerIdentity;
    }

    @GetMapping("/billers")
    public ResponseEntity<?> billers() {
        return ok(service.getBillers(callerIdentity.currentCustomer()));
    }

    @PostMapping("/fetch-bill")
    public ResponseEntity<?> fetchBill(@RequestBody FetchBillRequest request) {
        return ok(service.fetchBill(callerIdentity.currentCustomer(), request));
    }

    @PostMapping("/pay")
    public ResponseEntity<?> pay(@RequestBody PayBillRequest request) {
        PaymentResult result = service.payBill(callerIdentity.currentCustomer(), request);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", "SUCCESS".equals(result.status()));
        body.put("status", result.status());
        body.put("message", result.message());
        body.put("data", result);

        // SUCCESS and PENDING (outcome being confirmed) are 200; a definite failure is 400.
        return "FAILED".equals(result.status())
                ? ResponseEntity.badRequest().body(body)
                : ResponseEntity.ok(body);
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> transactions() {
        return ok(service.getHistory(callerIdentity.currentCustomer()));
    }

    @PostMapping("/transactions/{id}/complaint")
    public ResponseEntity<?> raiseComplaint(@PathVariable Long id, @RequestBody ComplaintRequest request) {
        return ok(service.raiseComplaint(callerIdentity.currentCustomer(), id, request));
    }

    @GetMapping("/transactions/{id}/complaint-status")
    public ResponseEntity<?> complaintStatus(@PathVariable Long id) {
        return ok(service.getComplaintStatus(callerIdentity.currentCustomer(), id));
    }

    // ------------------------------------------------------------------

    private ResponseEntity<?> ok(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(CreditCardBillException.class)
    public ResponseEntity<Map<String, Object>> handleCreditCard(CreditCardBillException e, HttpServletRequest request) {
        log.warn("Credit card request refused | {} {} | status={} | message={}",
                request.getMethod(), request.getRequestURI(), e.getStatus().value(), e.getMessage());
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException e, HttpServletRequest request) {
        log.warn("Credit card request rejected | {} {} | reason={}",
                request.getMethod(), request.getRequestURI(), e.getMessage());
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Wrong role / not the owner (403) and similar: keep the status, and leave a trace in the log. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e, HttpServletRequest request) {
        log.warn("Credit card request blocked | {} {} | status={} | reason={}",
                request.getMethod(), request.getRequestURI(), e.getStatusCode().value(), e.getReason());
        return error(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason() != null ? e.getReason() : "Request not allowed");
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(Exception e, HttpServletRequest request) {
        log.warn("Credit card request body unreadable | {} {} | {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Invalid request.");
    }

    /**
     * Anything unexpected (database problem, bug): the merchant gets a short reference,
     * and the same reference is logged with the full stack trace, so
     * grep "<reference>" in catalina.out finds the exact failure.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e, HttpServletRequest request) {
        String reference = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Credit card UNEXPECTED ERROR | reference={} | {} {}", reference,
                request.getMethod(), request.getRequestURI(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again, and quote reference " + reference + " if it continues.");
    }

    @ExceptionHandler(SuivrePayClient.VendorUnreachableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreachable(SuivrePayClient.VendorUnreachableException e) {
        log.error("SuivrePay unreachable: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY,
                "The bill payment service did not respond. Please try again in a moment.");
    }
}
