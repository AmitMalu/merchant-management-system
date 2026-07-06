package com.project2.ism.DTO.PaymentDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BillerCategoryListDto {

    @JsonProperty("description")
    private String description;

    @JsonProperty("Code")
    private String code;

    public BillerCategoryListDto() {
    }

    public BillerCategoryListDto(String description, String code) {
        this.description = description;
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
