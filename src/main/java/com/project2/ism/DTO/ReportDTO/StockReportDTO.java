package com.project2.ism.DTO.ReportDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockReportDTO {
    private Long serialNumberId;
    private String sid;
    private String mid;
    private String tid;
    private String vpaid;
    private String mobNumber;

    // Product info
    private Long productId;
    private String productName;
    private String productCode;
    private String productCategory;
    private String brand;
    private String model;

    // Status info
    private String status; // AVAILABLE, ALLOCATED, RETURNED
    private String allocationType; // MERCHANT, FRANCHISE, NONE

    // Allocation info
    private Long allocatedToId;
    private String allocatedToName;
    private String allocatedToType; // MERCHANT, FRANCHISE

    // Transaction dates
    private LocalDate inwardReceivedDate;
    private LocalDate dispatchDate;
    private LocalDate returnDate;
    private LocalDateTime receivedDate;
    private LocalDateTime receivedDateByFranchise;
    private LocalDateTime outwardReceivedDate;

    // Transaction IDs and Numbers
    private Long inwardTransactionId;
    private String invoiceNumber;
    private Long outwardTransactionId;
    private String deliveryNumber;
    private Long returnTransactionId;
    private String returnNumber;
    private Long productDistributionId;

    // Additional details
    private String productCondition;
    private String returnReason;
    private String returnCondition;

    // Constructor
    public StockReportDTO() {}

    // Getters and Setters
    public Long getSerialNumberId() {
        return serialNumberId;
    }

    public void setSerialNumberId(Long serialNumberId) {
        this.serialNumberId = serialNumberId;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getVpaid() {
        return vpaid;
    }

    public void setVpaid(String vpaid) {
        this.vpaid = vpaid;
    }

    public String getMobNumber() {
        return mobNumber;
    }

    public void setMobNumber(String mobNumber) {
        this.mobNumber = mobNumber;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductCategory() {
        return productCategory;
    }

    public void setProductCategory(String productCategory) {
        this.productCategory = productCategory;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAllocationType() {
        return allocationType;
    }

    public void setAllocationType(String allocationType) {
        this.allocationType = allocationType;
    }

    public Long getAllocatedToId() {
        return allocatedToId;
    }

    public void setAllocatedToId(Long allocatedToId) {
        this.allocatedToId = allocatedToId;
    }

    public String getAllocatedToName() {
        return allocatedToName;
    }

    public void setAllocatedToName(String allocatedToName) {
        this.allocatedToName = allocatedToName;
    }

    public String getAllocatedToType() {
        return allocatedToType;
    }

    public void setAllocatedToType(String allocatedToType) {
        this.allocatedToType = allocatedToType;
    }

    public LocalDate getInwardReceivedDate() {
        return inwardReceivedDate;
    }

    public void setInwardReceivedDate(LocalDate inwardReceivedDate) {
        this.inwardReceivedDate = inwardReceivedDate;
    }

    public LocalDate getDispatchDate() {
        return dispatchDate;
    }

    public void setDispatchDate(LocalDate dispatchDate) {
        this.dispatchDate = dispatchDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public LocalDateTime getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDateTime receivedDate) {
        this.receivedDate = receivedDate;
    }

    public LocalDateTime getReceivedDateByFranchise() {
        return receivedDateByFranchise;
    }

    public void setReceivedDateByFranchise(LocalDateTime receivedDateByFranchise) {
        this.receivedDateByFranchise = receivedDateByFranchise;
    }

    public LocalDateTime getOutwardReceivedDate() {
        return outwardReceivedDate;
    }

    public void setOutwardReceivedDate(LocalDateTime outwardReceivedDate) {
        this.outwardReceivedDate = outwardReceivedDate;
    }

    public Long getInwardTransactionId() {
        return inwardTransactionId;
    }

    public void setInwardTransactionId(Long inwardTransactionId) {
        this.inwardTransactionId = inwardTransactionId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Long getOutwardTransactionId() {
        return outwardTransactionId;
    }

    public void setOutwardTransactionId(Long outwardTransactionId) {
        this.outwardTransactionId = outwardTransactionId;
    }

    public String getDeliveryNumber() {
        return deliveryNumber;
    }

    public void setDeliveryNumber(String deliveryNumber) {
        this.deliveryNumber = deliveryNumber;
    }

    public Long getReturnTransactionId() {
        return returnTransactionId;
    }

    public void setReturnTransactionId(Long returnTransactionId) {
        this.returnTransactionId = returnTransactionId;
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public void setReturnNumber(String returnNumber) {
        this.returnNumber = returnNumber;
    }

    public Long getProductDistributionId() {
        return productDistributionId;
    }

    public void setProductDistributionId(Long productDistributionId) {
        this.productDistributionId = productDistributionId;
    }

    public String getProductCondition() {
        return productCondition;
    }

    public void setProductCondition(String productCondition) {
        this.productCondition = productCondition;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public String getReturnCondition() {
        return returnCondition;
    }

    public void setReturnCondition(String returnCondition) {
        this.returnCondition = returnCondition;
    }
}