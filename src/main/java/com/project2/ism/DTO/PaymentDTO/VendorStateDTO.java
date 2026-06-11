package com.project2.ism.DTO.PaymentDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VendorStateDTO {

    @JsonProperty("description")
    private String stateName;

    @JsonProperty("code")
    private String stateCode;
    // getters, setters

    public VendorStateDTO() {
    }

    public VendorStateDTO(String stateName, String stateCode) {
        this.stateName = stateName;
        this.stateCode = stateCode;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }
}

