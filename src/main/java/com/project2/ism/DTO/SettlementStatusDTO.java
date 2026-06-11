package com.project2.ism.DTO;

public class SettlementStatusDTO {
    private String status;
    private Long count;

    public SettlementStatusDTO(String status, Long count) {
        this.status = status;
        this.count = count;
    }

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
