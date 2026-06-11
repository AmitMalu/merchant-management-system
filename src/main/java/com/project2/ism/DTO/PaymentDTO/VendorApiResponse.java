package com.project2.ism.DTO.PaymentDTO;


public class VendorApiResponse {
    private Boolean successStatus;
    private String message;
    private String responseCode;
    private String data;

    public Boolean getSuccessStatus() { return successStatus; }
    public void setSuccessStatus(Boolean successStatus) { this.successStatus = successStatus; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
