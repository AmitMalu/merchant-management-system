package com.project2.ism.Repository;

import com.project2.ism.Model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByCustomerTypeAndCustomerIdAndStatus(
            String customerType, Long customerId, String status
    );

    List<SupportTicket> findByStatus(String status);

    List<SupportTicket> findByCustomerTypeAndCustomerId(
            String customerType, Long customerId
    );
}