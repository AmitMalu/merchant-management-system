package com.project2.ism.Model.Logs;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "digilocker_logs",
        indexes = {

                @Index(
                        name = "idx_client_id",
                        columnList = "client_id"
                ),

                @Index(
                        name = "idx_created_at",
                        columnList = "created_at"
                )
        }
)
public class DigilockerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "client_id")
    private String clientId;


    @Column(name = "merchant_id")
    private Long merchantId;


    @Column(name = "franchise_id")
    private Long franchiseId;


    @Column(name = "api_name", length = 50)
    private String apiName;


    @Column(name = "request_url", length = 500)
    private String requestUrl;


    @Column(
            name = "request_body",
            columnDefinition = "LONGTEXT"
    )
    private String requestBody;


    @Column(
            name = "response_body",
            columnDefinition = "LONGTEXT"
    )
    private String responseBody;

    @Column(
            name = "process_status",
            length = 20
    )
    private String processStatus;


    @Column(
            name = "error_message",
            columnDefinition = "TEXT"
    )
    private String errorMessage;


    @Column(name = "retry_count")
    private Integer retryCount = 0;


    @Column(name = "http_status")
    private Integer httpStatus;


    @Column(name = "processing_time_ms")
    private Long processingTimeMs;


    @Column(name = "created_at")
    private LocalDateTime createdAt
            = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public Long getFranchiseId() {
        return franchiseId;
    }

    public void setFranchiseId(Long franchiseId) {
        this.franchiseId = franchiseId;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
