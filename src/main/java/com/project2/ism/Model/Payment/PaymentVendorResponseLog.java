package com.project2.ism.Model.Payment;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_vendor_response_logs")
public class PaymentVendorResponseLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long vendorId;

    private String apiName;

    @Column(columnDefinition = "TEXT")
    private String requestPayload;

    @Column(columnDefinition = "TEXT")
    private String encryptedRequest;

    @Column(columnDefinition = "TEXT")
    private String encryptedResponse;

    @Column(columnDefinition = "TEXT")
    private String decryptedResponse;

    private Integer statusCode;

    private LocalDateTime createdOn;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVendorId() { return vendorId; }
    public void setVendorId(Long vendorId) { this.vendorId = vendorId; }
    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }
    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
    public String getEncryptedRequest() { return encryptedRequest; }
    public void setEncryptedRequest(String encryptedRequest) { this.encryptedRequest = encryptedRequest; }
    public String getEncryptedResponse() { return encryptedResponse; }
    public void setEncryptedResponse(String encryptedResponse) { this.encryptedResponse = encryptedResponse; }
    public String getDecryptedResponse() { return decryptedResponse; }
    public void setDecryptedResponse(String decryptedResponse) { this.decryptedResponse = decryptedResponse; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public LocalDateTime getCreatedOn() { return createdOn; }
    public void setCreatedOn(LocalDateTime createdOn) { this.createdOn = createdOn; }
}
