package com.project2.ism.Controller;

import com.project2.ism.DTO.AdminDTO.SupportTicketDTO;
import com.project2.ism.Model.SupportTicket;
import com.project2.ism.Service.SupportTicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/support-tickets")
public class SupportTicketController {

    @Autowired
    private SupportTicketService supportTicketService;

    @PostMapping("/create")
    public ResponseEntity<SupportTicket> createTicket(
            @RequestBody SupportTicket ticket,
            @RequestParam Long customerId) {

        String customerType = extractCustomerTypeFromToken();

        SupportTicket createdTicket = supportTicketService.createTicket(ticket, customerType, customerId);
        return new ResponseEntity<>(createdTicket, HttpStatus.CREATED);
    }

    @GetMapping("/unresolved")
    public ResponseEntity<List<SupportTicket>> getUnresolvedTickets(@RequestParam Long customerId) {
        String customerType = extractCustomerTypeFromToken();
        List<SupportTicket> tickets = supportTicketService.getUnresolvedTickets(customerType, customerId);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/resolved")
    public ResponseEntity<List<SupportTicket>> getResolvedTickets(@RequestParam Long customerId) {
        String customerType = extractCustomerTypeFromToken();
        List<SupportTicket> tickets = supportTicketService.getResolvedTickets(customerType, customerId);
        return ResponseEntity.ok(tickets);
    }

    // Admin endpoints
    @GetMapping("/admin/unresolved")
    public ResponseEntity<List<SupportTicketDTO>> getAllUnresolvedTickets() {
        List<SupportTicketDTO> tickets = supportTicketService.getAllUnresolvedTickets();
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/admin/resolved")
    public ResponseEntity<List<SupportTicketDTO>> getAllResolvedTickets() {
        List<SupportTicketDTO> tickets = supportTicketService.getAllResolvedTickets();
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketDTO> getTicketById(@PathVariable Long id) {
        Optional<SupportTicketDTO> ticket = supportTicketService.getTicketById(id);
        return ticket.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/admin/resolve/{id}")
    public ResponseEntity<SupportTicket> resolveTicket(
            @PathVariable Long id,
            @RequestParam String adminRemarks) {

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            SupportTicket resolvedTicket = supportTicketService.resolveTicket(id, auth.getName(), adminRemarks);
            return ResponseEntity.ok(resolvedTicket);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String extractCustomerTypeFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Extract role from authorities
        Optional<String> role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.equals("ROLE_FRANCHISE") || auth.equals("ROLE_MERCHANT"))
                .findFirst();

        if (role.isPresent()) {
            // Remove "ROLE_" prefix and return
            return role.get().replace("ROLE_", "");
        }

        throw new RuntimeException("Invalid customer type in token");
    }
}