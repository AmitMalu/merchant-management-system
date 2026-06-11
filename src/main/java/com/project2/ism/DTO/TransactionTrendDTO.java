
// 2. Chart DTOs
package com.project2.ism.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class TransactionTrendDTO {
    private LocalDate date;
    private Long count;
    private BigDecimal amount;

    public TransactionTrendDTO(LocalDate date, Long count, BigDecimal amount) {
        this.date = date;
        this.count = count;
        this.amount = amount;
    }

    // Getters and Setters
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

