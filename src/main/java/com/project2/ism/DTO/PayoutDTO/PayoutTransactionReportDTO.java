package com.project2.ism.DTO.PayoutDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PayoutTransactionReportDTO {

    Long getTransactionId();

    BigDecimal getCharge();

    BigDecimal getAmount();

    BigDecimal getBalAfterTran();

    BigDecimal getBalBeforeTran();

    String getRemarks();

    String getTranStatus();

    LocalDateTime getTransactionDate();

    String getTransactionType(); // CREDIT / DEBIT

    Long getMerchantId();   // populated only for merchant records

    Long getFranchiseId();  // populated only for franchise records
}
