package com.project2.ism.DTO.CreditCardDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request / response shapes for the credit-card bill payment APIs.
 * Nothing here carries the merchant id: it is always taken from the login token.
 */
public final class CreditCardDtos {

    private CreditCardDtos() {
    }

    public record FetchBillRequest(String billerId, String cardLast4, String mobileNumber) {
    }

    public record PayBillRequest(String requestId, String customerPan) {
    }

    public record ComplaintRequest(String description) {
    }

    public record Biller(String billerId, String billerName) {
    }

    /** What the merchant sees after "Fetch Bill". */
    public record FetchedBill(String requestId,
                              String billerId,
                              String billerName,
                              String customerName,
                              String billNumber,
                              String billDate,
                              String dueDate,
                              BigDecimal billAmount,
                              BigDecimal charges,
                              BigDecimal totalPayable,
                              boolean panRequired) {
    }

    public record PaymentResult(Long transactionId,
                                String requestId,
                                String status,
                                String message,
                                BigDecimal billAmount,
                                BigDecimal charges,
                                BigDecimal totalDeducted,
                                String walletTxnId,
                                String vendorTxnId) {
    }

    public record TransactionView(Long id,
                                  String requestId,
                                  String billerName,
                                  String cardLast4,
                                  String customerName,
                                  BigDecimal billAmount,
                                  BigDecimal charges,
                                  BigDecimal totalDeducted,
                                  String status,
                                  String message,
                                  String walletTxnId,
                                  String complaintTicketId,
                                  LocalDateTime createdAt,
                                  LocalDateTime completedAt) {
    }

    /** One line of the credit card bill payment report. requestId / vendorTxnId are filled for admins only. */
    public record ReportRow(Long id,
                            LocalDateTime paidAt,
                            LocalDateTime completedAt,
                            String walletTxnId,
                            String customerType,
                            Long customerId,
                            String customerName,
                            String franchiseName,
                            String billerName,
                            String cardLast4,
                            String cardHolder,
                            String mobile,
                            BigDecimal billAmount,
                            BigDecimal charges,
                            BigDecimal totalDeducted,
                            String status,
                            String message,
                            String complaintTicketId,
                            String requestId,
                            String vendorTxnId) {
    }

    /** Counts are for every status; the money totals are for SUCCESS payments only. */
    public record ReportSummary(long totalCount,
                                long successCount,
                                long pendingCount,
                                long failedCount,
                                BigDecimal successBillAmount,
                                BigDecimal successCharges,
                                BigDecimal successTotalDeducted) {
    }

    public record ReportResponse(java.util.List<ReportRow> rows,
                                 long totalCount,
                                 int page,
                                 int size,
                                 ReportSummary summary) {
    }

    public record ComplaintStatus(String ticketId,
                                  String type,
                                  String serviceName,
                                  String description,
                                  String status,
                                  String createdAt) {
    }
}
