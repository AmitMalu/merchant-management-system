package com.project2.ism.DTO.PayoutDTO;


import com.fasterxml.jackson.annotation.JsonProperty;

public class VimoEncryptedRequest {
    @JsonProperty("requestBody")
    private String requestBody;

    public VimoEncryptedRequest() {
    }

    public VimoEncryptedRequest(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }
}
