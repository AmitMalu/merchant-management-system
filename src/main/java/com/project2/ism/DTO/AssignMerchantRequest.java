package com.project2.ism.DTO;

import java.util.List;

public class AssignMerchantRequest {
    private Long franchiseId;
    private Long merchantId;
    private List<Long> selectedDeviceIds;

    private String distributedBy;

    // getters & setters

    public String getDistributedBy() {
        return distributedBy;
    }

    public void setDistributedBy(String distributedBy) {
        this.distributedBy = distributedBy;
    }

    public Long getFranchiseId() {
        return franchiseId;
    }

    public void setFranchiseId(Long franchiseId) {
        this.franchiseId = franchiseId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public List<Long> getSelectedDeviceIds() {
        return selectedDeviceIds;
    }

    public void setSelectedDeviceIds(List<Long> selectedDeviceIds) {
        this.selectedDeviceIds = selectedDeviceIds;
    }
}

