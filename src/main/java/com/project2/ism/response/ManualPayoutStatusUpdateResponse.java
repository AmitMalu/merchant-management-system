package com.project2.ism.response;



import com.project2.ism.request.ManualPayoutUpdateResult;

import java.time.LocalDateTime;
import java.util.List;

public class ManualPayoutStatusUpdateResponse {

    private String requestedStatus;

    private int totalRequested;

    private int processedSuccessfully;

    private int failedToProcess;

    private List<ManualPayoutUpdateResult> results;

    private LocalDateTime processedAt;

    public String getRequestedStatus() {
        return requestedStatus;
    }

    public void setRequestedStatus(String requestedStatus) {
        this.requestedStatus = requestedStatus;
    }

    public int getTotalRequested() {
        return totalRequested;
    }

    public void setTotalRequested(int totalRequested) {
        this.totalRequested = totalRequested;
    }

    public int getProcessedSuccessfully() {
        return processedSuccessfully;
    }

    public void setProcessedSuccessfully(int processedSuccessfully) {
        this.processedSuccessfully = processedSuccessfully;
    }

    public int getFailedToProcess() {
        return failedToProcess;
    }

    public void setFailedToProcess(int failedToProcess) {
        this.failedToProcess = failedToProcess;
    }

    public List<ManualPayoutUpdateResult> getResults() {
        return results;
    }

    public void setResults(List<ManualPayoutUpdateResult> results) {
        this.results = results;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
