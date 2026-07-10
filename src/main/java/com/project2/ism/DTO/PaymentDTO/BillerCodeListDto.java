package com.project2.ism.DTO.PaymentDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BillerCodeListDto {

    @JsonProperty("Code")
    private String code;

    @JsonProperty("description")
    private String description;

    public BillerCodeListDto() {
    }

    public BillerCodeListDto(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
