package com.project2.ism.DTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request shape for the BBPS Bill Payment API, per the Bharat Bill Payment
 * System (Bill Avenue) API Integration Document — JSON request fields match
 * the spec's "Bill Payment Sample JSON Request" section exactly.
 *
 * merchantId is our own addition (not part of the Bill Avenue spec) so the
 * service layer knows which merchant's wallet to debit — it is excluded from
 * the JSON sent to the vendor.
 *
 * @author SHUBHAM KHOPADE
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillPaymentRequest {

    @JsonIgnore
    private Long merchantId;

    private String requestId;
    private String billerAdhoc;
    private String agentId;

    private AgentDeviceInfo agentDeviceInfo;
    private CustomerInfo customerInfo;

    private String billerId;

    private InputParams inputParams;

    private JsonNode billerResponse;

    private AdditionalInfo additionalInfo;

    private AmountInfo amountInfo;
    private PaymentMethod paymentMethod;
    private PaymentInfo paymentInfo;

    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getBillerAdhoc() { return billerAdhoc; }
    public void setBillerAdhoc(String billerAdhoc) { this.billerAdhoc = billerAdhoc; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public AgentDeviceInfo getAgentDeviceInfo() { return agentDeviceInfo; }
    public void setAgentDeviceInfo(AgentDeviceInfo agentDeviceInfo) { this.agentDeviceInfo = agentDeviceInfo; }

    public CustomerInfo getCustomerInfo() { return customerInfo; }
    public void setCustomerInfo(CustomerInfo customerInfo) { this.customerInfo = customerInfo; }

    public String getBillerId() { return billerId; }
    public void setBillerId(String billerId) { this.billerId = billerId; }

    public InputParams getInputParams() { return inputParams; }
    public void setInputParams(InputParams inputParams) { this.inputParams = inputParams; }

    public JsonNode getBillerResponse() { return billerResponse; }
    public void setBillerResponse(JsonNode billerResponse) { this.billerResponse = billerResponse; }

    public AdditionalInfo getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(AdditionalInfo additionalInfo) { this.additionalInfo = additionalInfo; }

    public AmountInfo getAmountInfo() { return amountInfo; }
    public void setAmountInfo(AmountInfo amountInfo) { this.amountInfo = amountInfo; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public PaymentInfo getPaymentInfo() { return paymentInfo; }
    public void setPaymentInfo(PaymentInfo paymentInfo) { this.paymentInfo = paymentInfo; }

    public static class AgentDeviceInfo {
        private String ip;
        private String initChannel;
        private String mac;

        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }

        public String getInitChannel() { return initChannel; }
        public void setInitChannel(String initChannel) { this.initChannel = initChannel; }

        public String getMac() { return mac; }
        public void setMac(String mac) { this.mac = mac; }
    }

    public static class CustomerInfo {
        private String customerMobile;
        private String customerEmail;
        private String customerAdhaar;
        private String customerPan;

        public String getCustomerMobile() { return customerMobile; }
        public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }

        public String getCustomerEmail() { return customerEmail; }
        public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

        public String getCustomerAdhaar() { return customerAdhaar; }
        public void setCustomerAdhaar(String customerAdhaar) { this.customerAdhaar = customerAdhaar; }

        public String getCustomerPan() { return customerPan; }
        public void setCustomerPan(String customerPan) { this.customerPan = customerPan; }
    }

    public static class InputParams {
        private List<Input> input;

        public List<Input> getInput() { return input; }
        public void setInput(List<Input> input) { this.input = input; }

        public static class Input {
            private String paramName;
            private String paramValue;

            public String getParamName() { return paramName; }
            public void setParamName(String paramName) { this.paramName = paramName; }

            public String getParamValue() { return paramValue; }
            public void setParamValue(String paramValue) { this.paramValue = paramValue; }
        }
    }

    public static class AdditionalInfo {
        private List<Info> info;

        public List<Info> getInfo() { return info; }
        public void setInfo(List<Info> info) { this.info = info; }

        public static class Info {
            private String infoName;
            private String infoValue;

            public String getInfoName() { return infoName; }
            public void setInfoName(String infoName) { this.infoName = infoName; }

            public String getInfoValue() { return infoValue; }
            public void setInfoValue(String infoValue) { this.infoValue = infoValue; }
        }
    }

    public static class AmountInfo {
        private BigDecimal amount;
        private String currency = "356"; // ISO 4217 numeric code for INR
        private String custConvFee = "0";

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public String getCustConvFee() { return custConvFee; }
        public void setCustConvFee(String custConvFee) { this.custConvFee = custConvFee; }
    }

    public static class PaymentMethod {
        private String paymentMode;
        private String quickPay = "N";
        private String splitPay = "N";

        public String getPaymentMode() { return paymentMode; }
        public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

        public String getQuickPay() { return quickPay; }
        public void setQuickPay(String quickPay) { this.quickPay = quickPay; }

        public String getSplitPay() { return splitPay; }
        public void setSplitPay(String splitPay) { this.splitPay = splitPay; }
    }

    public static class PaymentInfo {
        private List<Info> info;

        public List<Info> getInfo() { return info; }
        public void setInfo(List<Info> info) { this.info = info; }

        public static class Info {
            private String infoName;
            private String infoValue;

            public String getInfoName() { return infoName; }
            public void setInfoName(String infoName) { this.infoName = infoName; }

            public String getInfoValue() { return infoValue; }
            public void setInfoValue(String infoValue) { this.infoValue = infoValue; }
        }
    }
}
