package com.project2.ism.DTO.PaymentDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Requests and answers of the vendor routing configuration (/payment-routing). */
public final class RoutingDtos {

    private RoutingDtos() {
    }

    // ---- which vendors serve which product ----
    public record ProductVendorRequest(Long productId, Long vendorId, String categories, Boolean enabled) {
    }

    public record ProductVendorDto(Long id, Long productId, String productName, Long vendorId, String vendorName,
                                   boolean vendorActive, String categories, boolean enabled) {
    }

    // ---- a routing ----
    public record RoutingVendorRequest(Long vendorId, Boolean enabled, BigDecimal minAmount, BigDecimal maxAmount,
                                       Integer dailyTransactionLimit, BigDecimal dailyAmountLimit,
                                       Integer monthlyTransactionLimit, BigDecimal monthlyAmountLimit,
                                       Integer yearlyTransactionLimit, BigDecimal yearlyAmountLimit) {

        /** Without monthly and yearly limits (a request from before they existed). */
        public RoutingVendorRequest(Long vendorId, Boolean enabled, BigDecimal minAmount, BigDecimal maxAmount,
                                    Integer dailyTransactionLimit, BigDecimal dailyAmountLimit) {
            this(vendorId, enabled, minAmount, maxAmount, dailyTransactionLimit, dailyAmountLimit,
                    null, null, null, null);
        }
    }

    /** `vendors` is in priority order: the first is Vendor 1 (mandatory), the others are optional. */
    public record RoutingRequest(Long productId, String name, Boolean status, Integer retryCount,
                                 Integer retryWaitSeconds, List<RoutingVendorRequest> vendors) {
    }

    public record RoutingVendorDto(Long vendorId, String vendorName, int priority, boolean enabled,
                                   BigDecimal minAmount, BigDecimal maxAmount, Integer dailyTransactionLimit,
                                   BigDecimal dailyAmountLimit, Integer monthlyTransactionLimit,
                                   BigDecimal monthlyAmountLimit, Integer yearlyTransactionLimit,
                                   BigDecimal yearlyAmountLimit) {

        public RoutingVendorDto(Long vendorId, String vendorName, int priority, boolean enabled,
                                BigDecimal minAmount, BigDecimal maxAmount, Integer dailyTransactionLimit,
                                BigDecimal dailyAmountLimit) {
            this(vendorId, vendorName, priority, enabled, minAmount, maxAmount, dailyTransactionLimit,
                    dailyAmountLimit, null, null, null, null);
        }
    }

    public record RoutingDto(Long id, Long productId, String productName, String name, boolean status,
                             int retryCount, int retryWaitSeconds, List<RoutingVendorDto> vendors,
                             long assignedCount, LocalDateTime createdAt, LocalDateTime updatedAt,
                             String updatedBy) {
    }

    // ---- who is assigned ----
    public record TargetRef(String type, Long id) {
    }

    public record AssignmentRequest(List<TargetRef> targets) {
    }

    public record AssignmentDto(String type, Long id, String name, LocalDateTime assignedAt) {
    }

    public record TargetOption(String type, Long id, String name) {
    }

    // ---- "which vendor would be chosen?" ----
    public record SimulationVendor(Long vendorId, String vendorName, int priority, boolean eligible, String reason) {
    }

    public record SimulationResult(String accountName, Long routingId, String routingName,
                                   List<SimulationVendor> vendors, String chosenVendor, String message) {
    }

    // ---- what a merchant / franchise has ----
    public record MyProduct(Long productId, String productCode, String productName) {
    }

    public record MyProducts(boolean gatingEnabled, List<MyProduct> products) {
    }

    // ---- first-time setup: the admin chooses the vendors; nothing is chosen for them ----
    public record SeedVendorChoice(Long vendorId, String categories) {
    }

    /** `vendors` is in priority order: the first is Vendor 1. */
    public record SeedProductChoice(Long productId, List<SeedVendorChoice> vendors) {
    }

    public record SeedRequest(List<SeedProductChoice> products) {
    }

    public record SeedResult(boolean dryRun, List<String> lines, int linksCreated, int profilesCreated,
                             int assignmentsCreated) {
    }

    // ---- the trial run: what routing WOULD choose, next to what was really used ----
    public record Decision(Long routingId, String routingName, Long chosenVendorId, String chosenVendorName,
                           boolean noRouting, String summary) {
    }

    public record TrialStatus(boolean enabled, int retentionHours, long rows) {
    }

    public record TrialSummaryRow(String productName, String source, String usedVendor, String chosenVendor,
                                  boolean matched, boolean noRouting, long count, BigDecimal amount) {
    }

    public record TrialRow(Long id, LocalDateTime at, String productName, String source, String accountType,
                           Long accountId, String accountName, BigDecimal amount, String category,
                           String routingName, String usedVendor, String chosenVendor, boolean matched,
                           boolean noRouting, String details, String reference) {
    }

    // ---- the payout engine switch (old flow / pilot / new flow) and its attempts ----
    public record PayoutEngineStatus(String mode, List<AssignmentDto> pilotAccounts, boolean useRouting) {
    }

    /** useRouting: null leaves the "use routing for payouts" switch as it is. */
    public record PayoutEngineRequest(String mode, List<TargetRef> pilotAccounts, Boolean useRouting) {
    }

    // ---- menu rules: which menus an account sees, from its routings ----
    /** mode: OFF, PILOT (only the listed accounts) or ON (everyone). */
    public record MenuRulesStatus(String mode, List<AssignmentDto> accounts) {
    }

    public record MenuRulesRequest(String mode, List<TargetRef> accounts) {
    }

    /** What the sidebar shows. applies=false: the rule does not apply to this account, so nothing is hidden. */
    public record MyMenu(boolean applies, boolean payout, boolean billPay, boolean creditCard) {
    }

    // ---- what routing says about one real payment (used by the payout engine) ----
    public record RoutePlanVendor(Long vendorId, String vendorName, int priority,
                                  Integer dailyTransactionLimit, BigDecimal dailyAmountLimit,
                                  Integer monthlyTransactionLimit, BigDecimal monthlyAmountLimit,
                                  Integer yearlyTransactionLimit, BigDecimal yearlyAmountLimit) {

        public RoutePlanVendor(Long vendorId, String vendorName, int priority,
                               Integer dailyTransactionLimit, BigDecimal dailyAmountLimit) {
            this(vendorId, vendorName, priority, dailyTransactionLimit, dailyAmountLimit, null, null, null, null);
        }
    }

    /** Why a vendor of the routing cannot serve, in words that are safe to show (no vendor name). */
    public record RoutePlanRefusal(int priority, String message) {
    }

    /**
     * hasRouting=false: no routing applies to this account (none assigned, or switched off): the fixed vendor is
     * used. notes mention vendor names (for logs); refusals do not (for the merchant).
     */
    public record RoutePlan(boolean hasRouting, Long routingId, String routingName, int retryCount,
                            int retryWaitSeconds, List<RoutePlanVendor> vendors, List<String> notes,
                            List<RoutePlanRefusal> refusals) {

        public RoutePlan(boolean hasRouting, Long routingId, String routingName, int retryCount,
                         int retryWaitSeconds, List<RoutePlanVendor> vendors, List<String> notes) {
            this(hasRouting, routingId, routingName, retryCount, retryWaitSeconds, vendors, notes, List.of());
        }
    }

    public record AttemptRow(Long id, LocalDateTime at, String payoutRef, String accountType, Long accountId,
                             String accountName, BigDecimal amount, int attemptNo, String vendor, String status,
                             String responseCode, String message, String vendorTxnId, String payoutStatus) {
    }

    public record AuditDto(Long id, Long routingId, Long productId, String action, String actor, String details,
                           LocalDateTime at) {
    }
}
