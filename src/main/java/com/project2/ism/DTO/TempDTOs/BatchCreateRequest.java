package com.project2.ism.DTO.TempDTOs;

import java.time.LocalDateTime;

public class BatchCreateRequest {
    public LocalDateTime windowStart;
    public LocalDateTime windowEnd;
    public String cycleKey;
    public String createdBy;
}
