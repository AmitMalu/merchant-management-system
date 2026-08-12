package com.project2.ism.DTO;


/**
 * @author SHUBHAM KHOPADE
 */
public class BillPayProviderRequestDTO {

    private String vendorName;
    private String serviceName;

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
