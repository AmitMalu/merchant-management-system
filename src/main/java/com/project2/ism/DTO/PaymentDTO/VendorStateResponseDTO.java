package com.project2.ism.DTO.PaymentDTO;

public class VendorStateResponseDTO {
    private String stateName;
    private String stateCode;

    public VendorStateResponseDTO() {
    }

    public VendorStateResponseDTO(String stateName, String stateCode) {
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
