package com.project2.ism.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;


public class ManualPayoutStatusUpdateRequest {

    @NotEmpty(message = "At least one payout transaction ID is required")
    private List<Long> transactionIds;

    @NotNull(message = "Status is required")
    private ManualPayoutStatus status;

    private String remarks;

    /**
     * Whose ledger the ids belong to: MERCHANT (default, also when missing) or FRANCHISE (a
     * franchise's own payouts). Merchant and franchise ledgers are separate tables whose ids
     * overlap, so the ids alone cannot say which one is meant.
     */
    private String accountType;

    public enum ManualPayoutStatus {
        SUCCESS,
        FAILED
    }

    public @NotEmpty(message = "At least one payout transaction ID is required") List<Long> getTransactionIds() {
        return transactionIds;
    }

    public void setTransactionIds(@NotEmpty(message = "At least one payout transaction ID is required") List<Long> transactionIds) {
        this.transactionIds = transactionIds;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public @NotNull(message = "Status is required") ManualPayoutStatus getStatus() {
        return status;
    }

    public void setStatus(@NotNull(message = "Status is required") ManualPayoutStatus status) {
        this.status = status;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    @JsonIgnore
    public boolean isFranchiseAccount() {
        return accountType != null && "FRANCHISE".equalsIgnoreCase(accountType.trim());
    }

    /** True when accountType is missing, MERCHANT or FRANCHISE. */
    @JsonIgnore
    public boolean hasValidAccountType() {
        return accountType == null
                || accountType.isBlank()
                || "MERCHANT".equalsIgnoreCase(accountType.trim())
                || isFranchiseAccount();
    }
}
