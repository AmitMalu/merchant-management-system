package com.project2.ism.Controller;

import com.project2.ism.DTO.SmsDTO.SmsSettingsDTO;
import com.project2.ism.Service.SmsSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin and Super Admin only (SecurityConfig): see every SMS and switch each one on or off. */
@RestController
@RequestMapping("/sms-settings")
public class SmsSettingsController {

    private final SmsSettingsService service;

    public SmsSettingsController(SmsSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SmsSettingsDTO list() {
        return service.list();
    }

    @PutMapping("/{key}")
    public ResponseEntity<?> setEnabled(@PathVariable String key, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body == null ? null : body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "enabled (true or false) is required"));
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String who = auth != null ? auth.getName() : "unknown";
        try {
            return ResponseEntity.ok(service.setEnabled(key, enabled, who));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
        }
    }
}
