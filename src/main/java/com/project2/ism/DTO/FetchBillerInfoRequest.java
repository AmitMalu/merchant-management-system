package com.project2.ism.DTO;

import java.util.List;

/**
 * @author SHUBHAM KHOPADE
 */
public class FetchBillerInfoRequest {

    private List<String> billerId;

    public FetchBillerInfoRequest(List<String> billerId) {
        this.billerId = billerId;
    }

    public List<String> getBillerId() {
        return billerId;
    }

    public void setBillerId(List<String> billerId) {
        this.billerId = billerId;
    }
}
