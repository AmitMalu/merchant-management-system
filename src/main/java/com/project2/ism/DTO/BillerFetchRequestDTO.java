package com.project2.ism.DTO;

import java.util.List;

/**
 * @author SHUBHAM KHOPADE
 */
public class BillerFetchRequestDTO {

    private String agentId;
    private String requestId;
    private boolean billerAdhoc;

    private AgentDeviceInfo agentDeviceInfo;
    private CustomerInfo customerInfo;

    private String billerId;
    private InputParams inputParams;

    public static class AgentDeviceInfo {
        private String ip;
        private String initChannel;
        private String mac;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getMac() {
            return mac;
        }

        public void setMac(String mac) {
            this.mac = mac;
        }

        public String getInitChannel() {
            return initChannel;
        }

        public void setInitChannel(String initChannel) {
            this.initChannel = initChannel;
        }
    }

    public static class CustomerInfo {
        private Long customerMobile;
        private String customerEmail;
        private String customerAdhaar;
        private String customerPan;

        public Long getCustomerMobile() {
            return customerMobile;
        }

        public void setCustomerMobile(Long customerMobile) {
            this.customerMobile = customerMobile;
        }

        public String getCustomerEmail() {
            return customerEmail;
        }

        public void setCustomerEmail(String customerEmail) {
            this.customerEmail = customerEmail;
        }

        public String getCustomerAdhaar() {
            return customerAdhaar;
        }

        public void setCustomerAdhaar(String customerAdhaar) {
            this.customerAdhaar = customerAdhaar;
        }

        public String getCustomerPan() {
            return customerPan;
        }

        public void setCustomerPan(String customerPan) {
            this.customerPan = customerPan;
        }
    }

    public static class InputParams {
        private java.util.List<Input> input;

        public List<Input> getInput() {
            return input;
        }

        public void setInput(List<Input> input) {
            this.input = input;
        }

        public static class Input {
            private String paramName;
            private String paramValue;

            public String getParamName() {
                return paramName;
            }

            public void setParamName(String paramName) {
                this.paramName = paramName;
            }

            public String getParamValue() {
                return paramValue;
            }

            public void setParamValue(String paramValue) {
                this.paramValue = paramValue;
            }
        }
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public boolean isBillerAdhoc() {
        return billerAdhoc;
    }

    public void setBillerAdhoc(boolean billerAdhoc) {
        this.billerAdhoc = billerAdhoc;
    }

    public AgentDeviceInfo getAgentDeviceInfo() {
        return agentDeviceInfo;
    }

    public void setAgentDeviceInfo(AgentDeviceInfo agentDeviceInfo) {
        this.agentDeviceInfo = agentDeviceInfo;
    }

    public CustomerInfo getCustomerInfo() {
        return customerInfo;
    }

    public void setCustomerInfo(CustomerInfo customerInfo) {
        this.customerInfo = customerInfo;
    }

    public String getBillerId() {
        return billerId;
    }

    public void setBillerId(String billerId) {
        this.billerId = billerId;
    }

    public InputParams getInputParams() {
        return inputParams;
    }

    public void setInputParams(InputParams inputParams) {
        this.inputParams = inputParams;
    }
}
