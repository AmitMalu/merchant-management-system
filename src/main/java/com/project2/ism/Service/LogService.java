package com.project2.ism.Service;

import com.project2.ism.Model.*;
import com.project2.ism.Model.Logs.FranchiseOrMerchantNotificationLog;
import com.project2.ism.Model.Logs.RazorpayNotificationLog;
import com.project2.ism.Model.Logs.DigilockerLog;
import com.project2.ism.Model.Logs.MosambeeNotificationLog;
import com.project2.ism.Model.Payment.PaymentVendorLog;
import com.project2.ism.Model.Payment.PaymentVendorMonthlyLog;
import com.project2.ism.Model.Payment.PaymentVendorResponseLog;
import com.project2.ism.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LogService {


    private final RequestLogRepository requestLogRepository;
    private final RazorpayNotificationLogRepository razorpayNotificationLogRepository;
    private final PaymentVendorResponseLogRepository paymentVendorResponseLogRepository;
    private final PaymentVendorLogRepository paymentVendorLogRepository;
    private final PaymentVendorMonthlyLogRepository paymentVendorMonthlyLogRepository;
    private final FranchiseOrMerchantNotificationLogRepository franchiseOrMerchantNotificationLogRepo;
    private final DigilockerLogRepository digilockerLogRepository;
    private final MosambeeNotificationLogRepository mosambeeNotificationLogRepository;

    public LogService(RequestLogRepository requestLogRepository, RazorpayNotificationLogRepository razorpayNotificationLogRepository, PaymentVendorResponseLogRepository paymentVendorResponseLogRepository, PaymentVendorLogRepository paymentVendorLogRepository, PaymentVendorMonthlyLogRepository paymentVendorMonthlyLogRepository, FranchiseOrMerchantNotificationLogRepository franchiseOrMerchantNotificationLogRepo, DigilockerLogRepository digilockerLogRepository, MosambeeNotificationLogRepository mosambeeNotificationLogRepository) {
        this.requestLogRepository = requestLogRepository;
        this.razorpayNotificationLogRepository = razorpayNotificationLogRepository;
        this.paymentVendorResponseLogRepository = paymentVendorResponseLogRepository;
        this.paymentVendorLogRepository = paymentVendorLogRepository;
        this.paymentVendorMonthlyLogRepository = paymentVendorMonthlyLogRepository;
        this.franchiseOrMerchantNotificationLogRepo = franchiseOrMerchantNotificationLogRepo;
        this.digilockerLogRepository = digilockerLogRepository;
        this.mosambeeNotificationLogRepository = mosambeeNotificationLogRepository;
    }

    // ==================== REQUEST LOGS ====================

    @Async
    public void saveRequestLogAsync(RequestLog log) {
        try {
            requestLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Failed to save request log: " + e.getMessage());
        }
    }

    public Page<RequestLog> getRequestLogs(Pageable pageable) {
        return requestLogRepository.findByOrderByCreatedAtDesc(pageable);
    }

    public Optional<RequestLog> getRequestLogById(Long id) {
        return requestLogRepository.findById(id);
    }

    public Page<RequestLog> getRequestLogsByUserId(String userId, Pageable pageable) {
        return requestLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public Page<RequestLog> getRequestLogsByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return requestLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable);
    }

    public Page<RequestLog> getRequestLogsByStatus(Integer status, Pageable pageable) {
        return requestLogRepository.findByResponseStatusOrderByCreatedAtDesc(status, pageable);
    }

    public Page<RequestLog> getErrorRequestLogs(Pageable pageable) {
        return requestLogRepository.findByResponseStatusGreaterThanEqualOrderByCreatedAtDesc(400, pageable);
    }

    public Page<RequestLog> getSlowRequestLogs(Long thresholdMs, Pageable pageable) {
        return requestLogRepository.findByExecutionTimeMsGreaterThanEqualOrderByCreatedAtDesc(thresholdMs, pageable);
    }

    public Page<RequestLog> searchRequestLogs(String userId, String method, Integer status,
                                              LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return requestLogRepository.findLogsByFilters(userId, method, status, start, end, pageable);
    }

    public Long countRequestsByUser(String userId) {
        return requestLogRepository.countRequestsByUserId(userId);
    }

    public Long countRequestsByDateRange(LocalDateTime start, LocalDateTime end) {
        return requestLogRepository.countByCreatedAtBetween(start, end);
    }

    @Transactional
    public void deleteRequestLog(Long id) {
        requestLogRepository.deleteById(id);
    }

    @Transactional
    public void deleteOldRequestLogs(LocalDateTime before) {
        requestLogRepository.deleteByCreatedAtBefore(before);
    }

    // ==================== RAZORPAY NOTIFICATION LOGS ====================

    @Async
    public void saveRazorpayLogAsync(RazorpayNotificationLog log) {
        try {
            razorpayNotificationLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Failed to save Razorpay notification log: " + e.getMessage());
        }
    }

    public Page<RazorpayNotificationLog> getRazorpayLogs(Pageable pageable) {
        return razorpayNotificationLogRepository.findByOrderByCreatedAtDesc(pageable);
    }

    public Optional<RazorpayNotificationLog> getRazorpayLogById(Long id) {
        return razorpayNotificationLogRepository.findById(id);
    }

    public List<RazorpayNotificationLog> getRazorpayLogsByTxnId(String txnId) {
        return razorpayNotificationLogRepository.findByTxnIdOrderByCreatedAtDesc(txnId);
    }

    public Page<RazorpayNotificationLog> getRazorpayLogsByStatus(String status, Pageable pageable) {
        return razorpayNotificationLogRepository.findByProcessStatusOrderByCreatedAtDesc(status, pageable);
    }

    public Page<RazorpayNotificationLog> getRazorpayFailedLogs(Pageable pageable) {
        return razorpayNotificationLogRepository.findByProcessStatusOrderByCreatedAtDesc("FAILED", pageable);
    }

    public Page<RazorpayNotificationLog> getRazorpayLogsByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return razorpayNotificationLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable);
    }

    public Long countRazorpayLogsByStatus(String status) {
        return razorpayNotificationLogRepository.countByProcessStatus(status);
    }

    public Page<FranchiseOrMerchantNotificationLog>
    getFranchiseOrMerchantNotificationLogs(
            Pageable pageable,
            LocalDate start,
            LocalDate end,
            String status) {

        boolean hasDateFilter = start != null && end != null;
        boolean hasStatusFilter = status != null && !status.equalsIgnoreCase("all");

        // Convert status to boolean
        Boolean isSend = null;
        if (hasStatusFilter) {
            isSend = Boolean.parseBoolean(status);
        }

        if (hasDateFilter && hasStatusFilter) {
            return franchiseOrMerchantNotificationLogRepo
                    .findByIsSendAndCreatedAtBetweenOrderByCreatedAtDesc(
                            isSend,
                            start.atStartOfDay(),
                            end.atTime(23, 59, 59),
                            pageable
                    );
        }

        if (hasDateFilter) {
            return franchiseOrMerchantNotificationLogRepo
                    .findByCreatedAtBetweenOrderByCreatedAtDesc(
                            start.atStartOfDay(),
                            end.atTime(23, 59, 59),
                            pageable
                    );
        }

        if (hasStatusFilter) {
            return franchiseOrMerchantNotificationLogRepo
                    .findByIsSendOrderByCreatedAtDesc(isSend, pageable);
        }

        return franchiseOrMerchantNotificationLogRepo
                .findAllByOrderByCreatedAtDesc(pageable);
    }



    @Transactional
    public void deleteRazorpayLog(Long id) {
        razorpayNotificationLogRepository.deleteById(id);
    }

    // ==================== PAYMENT VENDOR RESPONSE LOGS ====================

    public Page<PaymentVendorResponseLog> getVendorResponseLogs(Pageable pageable) {
        return paymentVendorResponseLogRepository.findByOrderByCreatedOnDesc(pageable);
    }

    public Optional<PaymentVendorResponseLog> getVendorResponseLogById(Long id) {
        return paymentVendorResponseLogRepository.findById(id);
    }

    public Page<PaymentVendorResponseLog> getVendorResponseLogsByVendorId(Long vendorId, Pageable pageable) {
        return paymentVendorResponseLogRepository.findByVendorIdOrderByCreatedOnDesc(vendorId, pageable);
    }

    public Page<PaymentVendorResponseLog> getVendorResponseLogsByApi(String apiName, Pageable pageable) {
        return paymentVendorResponseLogRepository.findByApiNameOrderByCreatedOnDesc(apiName, pageable);
    }

    public Page<PaymentVendorResponseLog> getVendorResponseLogsByStatus(Integer statusCode, Pageable pageable) {
        return paymentVendorResponseLogRepository.findByStatusCodeOrderByCreatedOnDesc(statusCode, pageable);
    }

    public Page<PaymentVendorResponseLog> getVendorResponseLogsByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return paymentVendorResponseLogRepository.findByCreatedOnBetweenOrderByCreatedOnDesc(start, end, pageable);
    }

    public Long countVendorResponseLogsByVendor(Long vendorId) {
        return paymentVendorResponseLogRepository.countByVendorId(vendorId);
    }

    @Transactional
    public void deleteVendorResponseLog(Long id) {
        paymentVendorResponseLogRepository.deleteById(id);
    }

    // ==================== PAYMENT VENDOR LOGS (DAILY) ====================

    public Page<PaymentVendorLog> getVendorDailyLogs(Pageable pageable) {
        return paymentVendorLogRepository.findByOrderByLogDateDesc(pageable);
    }

    public Optional<PaymentVendorLog> getVendorDailyLogById(Long id) {
        return paymentVendorLogRepository.findById(id);
    }

    public Page<PaymentVendorLog> getVendorDailyLogsByVendorId(Long vendorId, Pageable pageable) {
        return paymentVendorLogRepository.findByPaymentVendorIdOrderByLogDateDesc(vendorId, pageable);
    }

    public Optional<PaymentVendorLog> getVendorDailyLogByVendorAndDate(Long vendorId, LocalDate date) {
        return paymentVendorLogRepository.findByPaymentVendorIdAndLogDate(vendorId, date);
    }

    public List<PaymentVendorLog> getVendorDailyLogsByDateRange(Long vendorId, LocalDate start, LocalDate end) {
        return paymentVendorLogRepository.findByPaymentVendorIdAndLogDateBetweenOrderByLogDateDesc(vendorId, start, end);
    }

    @Transactional
    public void deleteVendorDailyLog(Long id) {
        paymentVendorLogRepository.deleteById(id);
    }

    // ==================== PAYMENT VENDOR MONTHLY LOGS ====================

    public Page<PaymentVendorMonthlyLog> getVendorMonthlyLogs(Pageable pageable) {
        return paymentVendorMonthlyLogRepository.findByOrderByYearDescMonthDesc(pageable);
    }

    public Optional<PaymentVendorMonthlyLog> getVendorMonthlyLogById(Long id) {
        return paymentVendorMonthlyLogRepository.findById(id);
    }

    public Page<PaymentVendorMonthlyLog> getVendorMonthlyLogsByVendorId(Long vendorId, Pageable pageable) {
        return paymentVendorMonthlyLogRepository.findByPaymentVendorIdOrderByYearDescMonthDesc(vendorId, pageable);
    }

    public Optional<PaymentVendorMonthlyLog> getVendorMonthlyLogByVendorAndYearMonth(Long vendorId, Integer year, Integer month) {
        return paymentVendorMonthlyLogRepository.findByPaymentVendorIdAndYearAndMonth(vendorId, year, month);
    }

    public List<PaymentVendorMonthlyLog> getVendorMonthlyLogsByYear(Long vendorId, Integer year) {
        return paymentVendorMonthlyLogRepository.findByPaymentVendorIdAndYearOrderByMonthDesc(vendorId, year);
    }

    @Transactional
    public void deleteVendorMonthlyLog(Long id) {
        paymentVendorMonthlyLogRepository.deleteById(id);
    }

    // ==================== DIGILOCKER LOGS ====================

    public Page<DigilockerLog> getDigilockerLogs(
            Pageable pageable,
            LocalDate start,
            LocalDate end,
            String status,
            String clientId,
            Long merchantId,
            Long franchiseId,
            String apiName) {

        LocalDateTime startDateTime = start != null ? start.atStartOfDay() : null;
        LocalDateTime endDateTime = end != null ? end.atTime(23, 59, 59) : null;

        String processStatus = (status != null && !status.equalsIgnoreCase("all")) ? status : null;

        return digilockerLogRepository.findLogsByFilters(
                clientId,
                merchantId,
                franchiseId,
                processStatus,
                apiName,
                startDateTime,
                endDateTime,
                pageable
        );
    }

    // ==================== MOSAMBEE LOGS ====================

    public Page<MosambeeNotificationLog> getMosambeeLogs(
            Pageable pageable,
            LocalDate start,
            LocalDate end,
            String status,
            String txnId) {

        LocalDateTime startDateTime = start != null ? start.atStartOfDay() : null;
        LocalDateTime endDateTime = end != null ? end.atTime(23, 59, 59) : null;

        String processStatus = (status != null && !status.equalsIgnoreCase("all")) ? status : null;

        return mosambeeNotificationLogRepository.findLogsByFilters(
                txnId,
                processStatus,
                startDateTime,
                endDateTime,
                pageable
        );
    }

    // ==================== CLEANUP SCHEDULED TASKS ====================

    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);

        try {
            requestLogRepository.deleteByCreatedAtBefore(cutoffDate);
            System.out.println("Cleaned up request logs older than " + cutoffDate);
        } catch (Exception e) {
            System.err.println("Error cleaning up request logs: " + e.getMessage());
        }

        try {
            razorpayNotificationLogRepository.deleteByCreatedAtBefore(cutoffDate);
            System.out.println("Cleaned up Razorpay logs older than " + cutoffDate);
        } catch (Exception e) {
            System.err.println("Error cleaning up Razorpay logs: " + e.getMessage());
        }

        try {
            paymentVendorResponseLogRepository.deleteByCreatedOnBefore(cutoffDate);
            System.out.println("Cleaned up vendor response logs older than " + cutoffDate);
        } catch (Exception e) {
            System.err.println("Error cleaning up vendor response logs: " + e.getMessage());
        }

        try {
            digilockerLogRepository.deleteByCreatedAtBefore(cutoffDate);
            System.out.println("Cleaned up Digilocker logs older than " + cutoffDate);
        } catch (Exception e) {
            System.err.println("Error cleaning up Digilocker logs: " + e.getMessage());
        }

        try {
            mosambeeNotificationLogRepository.deleteByCreatedAtBefore(cutoffDate);
            System.out.println("Cleaned up Mosambee logs older than " + cutoffDate);
        } catch (Exception e) {
            System.err.println("Error cleaning up Mosambee logs: " + e.getMessage());
        }
    }
}