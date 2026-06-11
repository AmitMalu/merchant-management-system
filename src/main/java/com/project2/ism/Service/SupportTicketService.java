package com.project2.ism.Service;

import com.project2.ism.DTO.AdminDTO.SupportTicketDTO;
import com.project2.ism.Model.SupportTicket;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import com.project2.ism.Repository.SupportTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class SupportTicketService {


    private final SupportTicketRepository supportTicketRepository;

    private final FranchiseRepository franchiseRepository;

    private final MerchantRepository merchantRepository;

    public SupportTicketService(SupportTicketRepository supportTicketRepository, FranchiseRepository franchiseRepository, MerchantRepository merchantRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.franchiseRepository = franchiseRepository;
        this.merchantRepository = merchantRepository;
    }

    public SupportTicket createTicket(SupportTicket ticket, String customerType, Long customerId) {
        ticket.setCustomerType(customerType);
        ticket.setCustomerId(customerId);
        ticket.setStatus("PENDING");
        ticket.setAdminRemarks("");
        return supportTicketRepository.save(ticket);
    }

    public List<SupportTicket> getUnresolvedTickets(String customerType, Long customerId) {
        return supportTicketRepository.findByCustomerTypeAndCustomerIdAndStatus(
                customerType, customerId, "PENDING"
        );
    }

    public List<SupportTicket> getResolvedTickets(String customerType, Long customerId) {
        return supportTicketRepository.findByCustomerTypeAndCustomerIdAndStatus(
                customerType, customerId, "RESOLVED"
        );
    }

    public List<SupportTicketDTO> getAllUnresolvedTickets() {
        List<SupportTicket> tickets = supportTicketRepository.findByStatus("PENDING");
        return tickets.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SupportTicketDTO> getAllResolvedTickets() {
        List<SupportTicket> tickets = supportTicketRepository.findByStatus("RESOLVED");
        return tickets.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Optional<SupportTicketDTO> getTicketById(Long id) {
        Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(id);
        return ticketOpt.map(this::convertToDTO);
    }

    public SupportTicket resolveTicket(Long id, String resolvedBy, String adminRemarks) {
        Optional<SupportTicket> ticketOpt = supportTicketRepository.findById(id);
        if (ticketOpt.isPresent()) {
            SupportTicket ticket = ticketOpt.get();
            ticket.setStatus("RESOLVED");
            ticket.setResolvedBy(resolvedBy);
            ticket.setAdminRemarks(adminRemarks);
            ticket.setResolvedAt(LocalDateTime.now());
            return supportTicketRepository.save(ticket);
        }
        throw new RuntimeException("Ticket not found with id: " + id);
    }

    private SupportTicketDTO convertToDTO(SupportTicket ticket) {
        SupportTicketDTO dto = new SupportTicketDTO();
        dto.setId(ticket.getId());
        dto.setIssueType(ticket.getIssueType());
        dto.setDescription(ticket.getDescription());
        dto.setCustomerType(ticket.getCustomerType());
        dto.setStatus(ticket.getStatus());
        dto.setResolvedBy(ticket.getResolvedBy());
        dto.setAdminRemarks(ticket.getAdminRemarks());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setResolvedAt(ticket.getResolvedAt());

        // Fetch customer details based on customerType
        if ("FRANCHISE".equalsIgnoreCase(ticket.getCustomerType())) {
            Optional<Franchise> franchiseOpt = franchiseRepository.findById(ticket.getCustomerId());
            if (franchiseOpt.isPresent()) {
                Franchise franchise = franchiseOpt.get();
                dto.setCustomerName(franchise.getFranchiseName());
                dto.setPhoneNumber(franchise.getContactPerson().getPhoneNumber());
            } else {
                dto.setCustomerName("Unknown");
                dto.setPhoneNumber("N/A");
            }
        } else if ("MERCHANT".equalsIgnoreCase(ticket.getCustomerType())) {
            Optional<Merchant> merchantOpt = merchantRepository.findById(ticket.getCustomerId());
            if (merchantOpt.isPresent()) {
                Merchant merchant = merchantOpt.get();
                dto.setCustomerName(merchant.getBusinessName());
                dto.setPhoneNumber(merchant.getContactPerson().getPhoneNumber());
            } else {
                dto.setCustomerName("Unknown");
                dto.setPhoneNumber("N/A");
            }
        } else {
            dto.setCustomerName("Unknown Type");
            dto.setPhoneNumber("N/A");
        }

        return dto;
    }
}