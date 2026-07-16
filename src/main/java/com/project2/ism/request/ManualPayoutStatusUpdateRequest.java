package com.project2.ism.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;


public class ManualPayoutStatusUpdateRequest {

    @NotEmpty(message = "At least one payout transaction ID is required")
    private List<Long> transactionIds;

    @NotNull(message = "Status is required")
    private ManualPayoutStatus status;

    private String remarks;

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
}
