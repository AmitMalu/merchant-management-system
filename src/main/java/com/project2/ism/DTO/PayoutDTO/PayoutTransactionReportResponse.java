package com.project2.ism.DTO.PayoutDTO;

import org.springframework.data.domain.Page;

public class PayoutTransactionReportResponse {

    private Long totalCount;
    private Long payoutCount;
    private Long payoutRefundCount;
    private Page<PayoutTransactionReportDTO> transactions;

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getPayoutCount() {
        return payoutCount;
    }

    public void setPayoutCount(Long payoutCount) {
        this.payoutCount = payoutCount;
    }

    public Long getPayoutRefundCount() {
        return payoutRefundCount;
    }

    public void setPayoutRefundCount(Long payoutRefundCount) {
        this.payoutRefundCount = payoutRefundCount;
    }

    public Page<PayoutTransactionReportDTO> getTransactions() {
        return transactions;
    }

    public void setTransactions(Page<PayoutTransactionReportDTO> transactions) {
        this.transactions = transactions;
    }
}

