package com.project2.ism.Service;


import com.project2.ism.DTO.PaymentDTO.PaymentVendorRoutingDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentVendorRoutingRequest;
import com.project2.ism.DTO.PaymentDTO.PaymentVendorRuleDTO;
import com.project2.ism.DTO.PaymentDTO.PaymentVendorRuleRequest;
import com.project2.ism.DTO.Vendor.VendorIDNameDTO;
import com.project2.ism.Enum.TransactionStatus;
import com.project2.ism.Exception.NoAvailableVendorException;
import com.project2.ism.Model.Payment.*;
import com.project2.ism.Repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentVendorRoutingService {

    private final PaymentVendorRoutingRepository paymentVendorRoutingRepository;
    private final PaymentVendorRuleRepository paymentVendorRuleRepository;
    private final PaymentProductRepository paymentProductRepository;
    private final PaymentVendorRepository paymentVendorRepository;
    private final PaymentTransactionHistoryRepository paymentTransactionHistoryRepository;
    private final PaymentVendorLogRepository paymentVendorLogRepository;


    private static final int FAILURE_CHECK_LIMIT = 5;
    private static final int FAILURE_THRESHOLD = 3; // 3 out of 5 = multiple failures



    public PaymentVendorRoutingService(
            PaymentVendorRoutingRepository paymentVendorRoutingRepository,
            PaymentVendorRuleRepository paymentVendorRuleRepository,
            PaymentProductRepository paymentProductRepository,
            PaymentVendorRepository paymentVendorRepository, PaymentTransactionHistoryRepository paymentTransactionHistoryRepository, PaymentVendorLogRepository paymentVendorLogRepository
    ) {
        this.paymentVendorRoutingRepository = paymentVendorRoutingRepository;
        this.paymentVendorRuleRepository = paymentVendorRuleRepository;
        this.paymentProductRepository = paymentProductRepository;
        this.paymentVendorRepository = paymentVendorRepository;
        this.paymentTransactionHistoryRepository = paymentTransactionHistoryRepository;
        this.paymentVendorLogRepository = paymentVendorLogRepository;
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------
    @Transactional
    public PaymentVendorRoutingDTO createRouting(PaymentVendorRoutingRequest request) {

        PaymentProduct product = paymentProductRepository.findById(request.getPayoutProductId())
                .orElseThrow(() -> new IllegalArgumentException("Payout Product not found"));

        paymentVendorRoutingRepository.findByPaymentProductId(request.getPayoutProductId())
                .ifPresent(v -> { throw new IllegalArgumentException("Routing already exists for this product"); });

        validateUniqueVendors(request.getVendor1Id(), request.getVendor2Id(), request.getVendor3Id());
        validateVendorRulesMatchSelectedVendors(request);
        validateVendorRuleRanges(request);

        PaymentVendor v1 = loadVendor(request.getVendor1Id());
        PaymentVendor v2 = loadVendor(request.getVendor2Id());
        PaymentVendor v3 = loadVendor(request.getVendor3Id());

        PaymentVendorRouting routing = new PaymentVendorRouting();
        routing.setPayoutProduct(product);
        routing.setPaymentVendor1(v1);
        routing.setPaymentVendor2(v2);
        routing.setPaymentVendor3(v3);
        routing.setStatus(true);

        for (PaymentVendorRuleRequest r : request.getVendorRules()) {
            PaymentVendorRule rule = createRuleEntity(routing, r);
            routing.addVendorRule(rule);
        }

        return convertToDTO(paymentVendorRoutingRepository.save(routing));
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------
    @Transactional
    public PaymentVendorRoutingDTO updateRouting(Long id, PaymentVendorRoutingRequest request) {

        PaymentVendorRouting routing = paymentVendorRoutingRepository.findByIdWithRules(id)
                .orElseThrow(() -> new IllegalArgumentException("Routing not found"));

        validateUniqueVendors(request.getVendor1Id(), request.getVendor2Id(), request.getVendor3Id());
        validateVendorRulesMatchSelectedVendors(request);
        validateVendorRuleRanges(request);

        if (!routing.getPayoutProduct().getId().equals(request.getPayoutProductId())) {
            PaymentProduct product = paymentProductRepository.findById(request.getPayoutProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Payout Product not found"));
            routing.setPayoutProduct(product);
        }

        routing.setPaymentVendor1(loadVendor(request.getVendor1Id()));
        routing.setPaymentVendor2(loadVendor(request.getVendor2Id()));
        routing.setPaymentVendor3(loadVendor(request.getVendor3Id()));

        // remove existing rules
        paymentVendorRuleRepository.deleteByPaymentVendorRoutingId(id);
        routing.getPaymentVendorRules().clear();

        // add new rules
        for (PaymentVendorRuleRequest r : request.getVendorRules()) {
            PaymentVendorRule rule = createRuleEntity(routing, r);
            routing.addVendorRule(rule);
        }

        return convertToDTO(paymentVendorRoutingRepository.save(routing));
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------
    @Transactional
    public void deleteRouting(Long id) {
        PaymentVendorRouting routing = paymentVendorRoutingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Routing not found with id " + id));

        paymentVendorRoutingRepository.delete(routing);
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PaymentVendorRoutingDTO getRoutingById(Long id) {
        PaymentVendorRouting routing = paymentVendorRoutingRepository.findByIdWithRules(id)
                .orElseThrow(() -> new IllegalArgumentException("Routing not found"));
        return convertToDTO(routing);
    }

    @Transactional(readOnly = true)
    public Page<PaymentVendorRoutingDTO> getAllRoutings(Pageable pageable) {
        return paymentVendorRoutingRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public PaymentVendorRoutingDTO getRoutingByProductId(Long payoutProductId) {
        PaymentVendorRouting routing = paymentVendorRoutingRepository.findByPaymentProductId(payoutProductId)
                .orElseThrow(() -> new IllegalArgumentException("Routing not found for this payout product"));
        return convertToDTO(routing);
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private PaymentVendor loadVendor(Long id) {
        return paymentVendorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment Vendor not found with id " + id));
    }

    private PaymentVendorRule createRuleEntity(PaymentVendorRouting routing, PaymentVendorRuleRequest r) {
        PaymentVendorRule rule = new PaymentVendorRule();
        rule.setPaymentVendor(loadVendor(r.getVendorId()));
        rule.setMinAmount(r.getMinAmount());
        rule.setMaxAmount(r.getMaxAmount());
        rule.setDailyTransactionLimit(r.getDailyTransactionLimit());
        rule.setDailyAmountLimit(r.getDailyAmountLimit());
        rule.setPaymentVendorRouting(routing);
        return rule;
    }

    private void validateUniqueVendors(Long v1, Long v2, Long v3) {
        if (v1.equals(v2) || v1.equals(v3) || v2.equals(v3)) {
            throw new IllegalArgumentException("Vendor1, Vendor2, Vendor3 must be different");
        }
    }

    private void validateVendorRulesMatchSelectedVendors(PaymentVendorRoutingRequest request) {
        List<Long> vendors = Arrays.asList(request.getVendor1Id(), request.getVendor2Id(), request.getVendor3Id());

        for (PaymentVendorRuleRequest rule : request.getVendorRules()) {
            if (!vendors.contains(rule.getVendorId())) {
                throw new IllegalArgumentException(
                        "Rule vendor " + rule.getVendorId() + " is not part of priority vendors"
                );
            }
        }
    }

    private void validateVendorRuleRanges(PaymentVendorRoutingRequest request) {

        request.getVendorRules().forEach(rule -> {
            if (rule.getMinAmount().compareTo(rule.getMaxAmount()) >= 0) {
                throw new IllegalArgumentException("Min amount must be less than max amount");
            }
        });

        var grouped = request.getVendorRules().stream()
                .collect(Collectors.groupingBy(PaymentVendorRuleRequest::getVendorId));

        grouped.forEach((vendorId, rules) -> {
            var sorted = rules.stream()
                    .sorted((a, b) -> a.getMinAmount().compareTo(b.getMinAmount()))
                    .toList();

            for (int i = 0; i < sorted.size() - 1; i++) {
                if (sorted.get(i).getMaxAmount().compareTo(sorted.get(i + 1).getMinAmount()) >= 0) {
                    throw new IllegalArgumentException("Vendor " + vendorId + " has overlapping amount ranges");
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Routing Logic
    // -------------------------------------------------------------------------



    @Transactional(noRollbackFor = NoAvailableVendorException.class)
    public PaymentVendor getVendorForToken(PaymentVendorRouting routing, BigDecimal transactionAmount) {
        List<PaymentVendor> vendorPriority = Arrays.asList(
                routing.getPaymentVendor1(),
                routing.getPaymentVendor2(),
                routing.getPaymentVendor3()
        );

        LocalDate today = LocalDate.now();

        for (PaymentVendor paymentVendor : vendorPriority) {
            if (paymentVendor == null) continue;

            // Priority 1: Check failure rate
            if (hasMultipleRecentFailures(paymentVendor)) {
                continue; // Skip to next vendor
            }

            // Priority 2: Check transaction amount range
            PaymentVendorRule applicableRule = getApplicableRule(routing, paymentVendor, transactionAmount);
            if (applicableRule == null) {
                continue; // Amount not in range
            }

            // Get or create today's log (with pessimistic lock for concurrency)
            PaymentVendorLog todayLog = getTodayLogWithLock(paymentVendor, today);

            // Priority 3: Check daily amount limit
            if (todayLog.getTotalAmountProcessed().add(transactionAmount)
                    .compareTo(applicableRule.getDailyAmountLimit()) > 0) {
                continue;
            }


            // Priority 4: Check daily transaction count limit
            if (todayLog.getTransactionCount() >= applicableRule.getDailyTransactionLimit()) {
                continue; // Would exceed daily transaction limit
            }

            // All checks passed - Reserve capacity immediately
            reserveVendorCapacity(todayLog, transactionAmount);

            return paymentVendor;
        }

        throw new NoAvailableVendorException("No vendor available matching all routing criteria");
    }

    private boolean hasMultipleRecentFailures(PaymentVendor vendor) {
        List<PaymentTransactionHistory> recentTransactions =
                paymentTransactionHistoryRepository.findTop5ByPaymentVendorOrderByCreatedAtDesc(vendor);

        if (recentTransactions.size() < FAILURE_CHECK_LIMIT) {
            return false; // Not enough history
        }

        long failureCount = recentTransactions.stream()
                .filter(t -> t.getStatus() == TransactionStatus.FAILED)
                .count();

        return failureCount >= FAILURE_THRESHOLD;
    }

    private PaymentVendorRule getApplicableRule(PaymentVendorRouting routing, PaymentVendor vendor, BigDecimal amount) {
        return routing.getPaymentVendorRules().stream()
                .filter(rule -> rule.getPaymentVendor().equals(vendor))
                .filter(rule ->
                        amount.compareTo(rule.getMinAmount()) >= 0 &&
                                amount.compareTo(rule.getMaxAmount()) <= 0
                )
                .findFirst()
                .orElse(null);
    }

    @Transactional
    private PaymentVendorLog getTodayLogWithLock(PaymentVendor vendor, LocalDate date) {
        // Use pessimistic lock to prevent race conditions
        PaymentVendorLog log = paymentVendorLogRepository
                .findByVendorAndLogDateWithLock(vendor, date);

        if (log == null) {
            log = new PaymentVendorLog();
            log.setVendor(vendor);
            log.setLogDate(date);
            log = paymentVendorLogRepository.save(log);
        }

        return log;
    }

    private void reserveVendorCapacity(PaymentVendorLog log, BigDecimal amount) {
        log.setTotalAmountProcessed(
                log.getTotalAmountProcessed().add(amount)
        );
        log.setTransactionCount(log.getTransactionCount() + 1);
        paymentVendorLogRepository.save(log);
    }

    // Call this after successful transaction
    public void confirmTransaction(PaymentVendor vendor, String transactionRef, BigDecimal amount) {
        PaymentTransactionHistory history = new PaymentTransactionHistory();
        history.setPaymentVendor(vendor);
        history.setStatus(TransactionStatus.SUCCESS);
        history.setAmount(amount);
        history.setTransactionRef(transactionRef);
        paymentTransactionHistoryRepository.save(history);
    }

    // Call this if transaction fails - rollback the reservation
    @Transactional
    public void handleTransactionFailure(PaymentVendor vendor, String transactionRef, BigDecimal amount) {
        // Rollback the reserved capacity
        LocalDate today = LocalDate.now();
        PaymentVendorLog log = paymentVendorLogRepository.findByVendorAndLogDate(vendor, today);

        if (log != null) {
            log.setTotalAmountProcessed(
                    log.getTotalAmountProcessed().subtract(amount).max(BigDecimal.ZERO)
            );
            log.setTransactionCount(Math.max(0, log.getTransactionCount() - 1));
            log.setFailureCount(log.getFailureCount() + 1);
            paymentVendorLogRepository.save(log);
        }

        // Record failure in history
        PaymentTransactionHistory history = new PaymentTransactionHistory();
        history.setPaymentVendor(vendor);
        history.setStatus(TransactionStatus.FAILED);
        history.setAmount(amount);
        history.setTransactionRef(transactionRef);
        paymentTransactionHistoryRepository.save(history);
    }


    private PaymentVendorRuleDTO convertRuleToDTO(PaymentVendorRule rule) {
        return new PaymentVendorRuleDTO(
                rule.getId(),
                rule.getPaymentVendor().getId(),
                rule.getPaymentVendor().getVendorName(),
                rule.getMinAmount(),
                rule.getMaxAmount(),
                rule.getDailyTransactionLimit(),
                rule.getDailyAmountLimit()
        );
    }

    private PaymentVendorRoutingDTO convertToDTO(PaymentVendorRouting routing) {
        PaymentVendorRoutingDTO dto = new PaymentVendorRoutingDTO();

        dto.setId(routing.getId());
        dto.setPayoutProductId(routing.getPayoutProduct().getId());
        dto.setPayoutProductName(routing.getPayoutProduct().getProductName());

        dto.setVendor1(new VendorIDNameDTO(routing.getPaymentVendor1().getId(), routing.getPaymentVendor1().getVendorName()));
        dto.setVendor2(new VendorIDNameDTO(routing.getPaymentVendor2().getId(), routing.getPaymentVendor2().getVendorName()));
        dto.setVendor3(new VendorIDNameDTO(routing.getPaymentVendor3().getId(), routing.getPaymentVendor3().getVendorName()));

        dto.setVendorRules(
                routing.getPaymentVendorRules().stream()
                        .map(this::convertRuleToDTO)
                        .collect(Collectors.toList())
        );

        dto.setStatus(routing.getStatus());
        dto.setCreatedAt(routing.getCreatedAt());
        dto.setUpdatedAt(routing.getUpdatedAt());

        return dto;
    }
}

