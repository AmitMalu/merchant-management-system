package com.project2.ism.DTO.DigiLockerDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SurepassInitializeRequest {

    private Data data;

    public static class Data {

        @JsonProperty("prefill_options")
        private PrefillOptions prefillOptions;

        @JsonProperty("expiry_minutes")
        private Integer expiryMinutes;

        @JsonProperty("send_sms")
        private Boolean sendSms;

        @JsonProperty("send_email")
        private Boolean sendEmail;

        @JsonProperty("verify_phone")
        private Boolean verifyPhone;

        @JsonProperty("verify_email")
        private Boolean verifyEmail;

        @JsonProperty("signup_flow")
        private Boolean signupFlow;

        @JsonProperty("redirect_url")
        private String redirectUrl;

        private String state;

        public PrefillOptions getPrefillOptions() {
            return prefillOptions;
        }

        public void setPrefillOptions(PrefillOptions prefillOptions) {
            this.prefillOptions = prefillOptions;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getRedirectUrl() {
            return redirectUrl;
        }

        public void setRedirectUrl(String redirectUrl) {
            this.redirectUrl = redirectUrl;
        }

        public Boolean getSignupFlow() {
            return signupFlow;
        }

        public void setSignupFlow(Boolean signupFlow) {
            this.signupFlow = signupFlow;
        }

        public Boolean getVerifyEmail() {
            return verifyEmail;
        }

        public void setVerifyEmail(Boolean verifyEmail) {
            this.verifyEmail = verifyEmail;
        }

        public Boolean getVerifyPhone() {
            return verifyPhone;
        }

        public void setVerifyPhone(Boolean verifyPhone) {
            this.verifyPhone = verifyPhone;
        }

        public Boolean getSendEmail() {
            return sendEmail;
        }

        public void setSendEmail(Boolean sendEmail) {
            this.sendEmail = sendEmail;
        }

        public Boolean getSendSms() {
            return sendSms;
        }

        public void setSendSms(Boolean sendSms) {
            this.sendSms = sendSms;
        }

        public Integer getExpiryMinutes() {
            return expiryMinutes;
        }

        public void setExpiryMinutes(Integer expiryMinutes) {
            this.expiryMinutes = expiryMinutes;
        }
    }

    public static class PrefillOptions {

        @JsonProperty("full_name")
        private String fullName;

        @JsonProperty("mobile_number")
        private String mobileNumber;

        @JsonProperty("user_email")
        private String userEmail;

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getMobileNumber() {
            return mobileNumber;
        }

        public void setMobileNumber(String mobileNumber) {
            this.mobileNumber = mobileNumber;
        }

        public String getUserEmail() {
            return userEmail;
        }

        public void setUserEmail(String userEmail) {
            this.userEmail = userEmail;
        }
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }
}
