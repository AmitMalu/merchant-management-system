package com.project2.ism.Service;

import com.project2.ism.Model.CreditCard.CreditCardApiLog;
import com.project2.ism.Repository.CreditCardApiLogRepository;
import com.project2.ism.Repository.FranchiseRepository;
import com.project2.ism.Repository.MerchantRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes and reads the credit card API log (table credit_card_api_logs).
 * Saving a log row must never break a payment or a lookup, so any problem
 * here is only written to the application log.
 */
@Service
public class CreditCardApiLogService {

    private static final Logger log = LoggerFactory.getLogger(CreditCardApiLogService.class);

    private final CreditCardApiLogRepository repository;
    private final MerchantRepository merchantRepository;
    private final FranchiseRepository franchiseRepository;

    public CreditCardApiLogService(CreditCardApiLogRepository repository, MerchantRepository merchantRepository,
                                   FranchiseRepository franchiseRepository) {
        this.repository = repository;
        this.merchantRepository = merchantRepository;
        this.franchiseRepository = franchiseRepository;
    }

    /** Saves one log row. Never throws. */
    public void record(CreditCardApiLog entry) {
        try {
            if (entry.getCreatedAt() == null) {
                entry.setCreatedAt(LocalDateTime.now());
            }
            if (entry.getMerchantName() == null && entry.getMerchantId() != null) {
                if (CustomerRef.FRANCHISE.equalsIgnoreCase(entry.getCustomerType())) {
                    franchiseRepository.findById(entry.getMerchantId())
                            .ifPresent(f -> entry.setMerchantName(f.getFranchiseName()));
                } else {
                    merchantRepository.findById(entry.getMerchantId())
                            .ifPresent(m -> entry.setMerchantName(m.getBusinessName()));
                }
            }
            repository.save(entry);
        } catch (Exception e) {
            log.error("Could not save credit card API log | callId={} | action={} | error={}",
                    entry.getCallId(), entry.getAction(), e.getMessage(), e);
        }
    }

    /** Admin search; every filter is optional. Newest first. */
    public Page<CreditCardApiLog> search(String action, String outcome, Long merchantId, String requestId,
                                         LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return search(action, outcome, null, merchantId, requestId, start, end, pageable);
    }

    public Page<CreditCardApiLog> search(String action, String outcome, String customerType, Long merchantId,
                                         String requestId, LocalDateTime start, LocalDateTime end,
                                         Pageable pageable) {

        Specification<CreditCardApiLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                p.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            }
            if (outcome != null && !outcome.isBlank()) {
                p.add(cb.equal(root.get("outcome"), outcome.trim().toUpperCase()));
            }
            if (customerType != null && !customerType.isBlank()) {
                p.add(cb.equal(root.get("customerType"), customerType.trim().toUpperCase()));
            }
            if (merchantId != null) {
                p.add(cb.equal(root.get("merchantId"), merchantId));
            }
            if (requestId != null && !requestId.isBlank()) {
                p.add(cb.equal(root.get("requestId"), requestId.trim()));
            }
            if (start != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (end != null) {
                p.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        Pageable sorted = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        return repository.findAll(spec, sorted);
    }
}
