package com.project2.ism.DTO.DigiLockerDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SurepassInitializeResponse {

    private DataResponse data;

    @JsonProperty("status_code")
    private Integer statusCode;

    @JsonProperty("message_code")
    private String messageCode;

    private String message;

    private Boolean success;

    public DataResponse getData() {
        return data;
    }

    public void setData(DataResponse data) {
        this.data = data;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public void setMessageCode(String messageCode) {
        this.messageCode = messageCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public static class DataResponse {

        @JsonProperty("client_id")
        private String clientId;

        private String token;

        private String url;

        @JsonProperty("expiry_seconds")
        private Integer expirySeconds;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Integer getExpirySeconds() {
            return expirySeconds;
        }

        public void setExpirySeconds(Integer expirySeconds) {
            this.expirySeconds = expirySeconds;
        }
    }

}
