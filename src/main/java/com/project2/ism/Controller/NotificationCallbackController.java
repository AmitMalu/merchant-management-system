package com.project2.ism.Controller;

import com.project2.ism.DTO.NotificationCallbackDtos.CreateRequest;
import com.project2.ism.DTO.NotificationCallbackDtos.UpdateRequest;
import com.project2.ism.Service.NotificationCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin > Other > Notification URL. Admin / super admin only (see SecurityConfig).
 * The URLs set here are where Razorpay / Mosambee payment notifications are forwarded.
 */
@RestController
@RequestMapping("/notification-callbacks")
public class NotificationCallbackController {

    private static final Logger log = LoggerFactory.getLogger(NotificationCallbackController.class);

    private final NotificationCallbackService service;

    public NotificationCallbackController(NotificationCallbackService service) {
        this.service = service;
    }

    /** Filters (all optional): type = FRANCHISE / FRANCHISE_MERCHANT / DIRECT_MERCHANT, franchiseId, search. */
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String type,
                                  @RequestParam(required = false) Long franchiseId,
                                  @RequestParam(required = false) String search) {
        return ok(service.list(type, franchiseId, search));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(body(true, "Notification URL added", service.create(request, adminEmail())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(body(true, "Notification URL updated", service.update(id, request, adminEmail())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id, adminEmail());
        return ResponseEntity.ok(body(true, "Notification URL deleted", null));
    }

    // ------------------------------------------------------------------

    private String adminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }

    private ResponseEntity<?> ok(Object data) {
        return ResponseEntity.ok(body(true, null, data));
    }

    private Map<String, Object> body(boolean success, String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        if (message != null) {
            body.put("message", message);
        }
        if (data != null) {
            body.put("data", data);
        }
        return body;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("Notification URL request rejected | {} {} | reason={}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.badRequest().body(body(false, e.getMessage(), null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e, HttpServletRequest request) {
        log.warn("Notification URL request refused | {} {} | status={} | reason={}",
                request.getMethod(), request.getRequestURI(), e.getStatusCode().value(), e.getReason());
        return ResponseEntity.status(e.getStatusCode())
                .body(body(false, e.getReason() != null ? e.getReason() : "Request not allowed", null));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(Exception e, HttpServletRequest request) {
        log.warn("Notification URL request body unreadable | {} {} | {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.badRequest().body(body(false, "Invalid request.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e, HttpServletRequest request) {
        String reference = java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Notification URL UNEXPECTED ERROR | reference={} | {} {}", reference, request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(false, "Something went wrong. Please try again, and quote reference " + reference + " if it continues.", null));
    }
}
