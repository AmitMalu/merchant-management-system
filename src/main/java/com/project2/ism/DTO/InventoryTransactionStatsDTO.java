package com.project2.ism.DTO;

import java.util.Map;

public class InventoryTransactionStatsDTO {
    public Long totalInwardTransactions;
    public Long totalOutwardTransactions;
    public Long totalReturnTransactions;
    public Long totalProductSerials;

    // breakdowns
    public Map<String, Long> inwardByVendor;       // vendorId -> count
    public Map<String, Long> outwardByCustomer;    // franchise vs merchant counts
    public Map<String, Long> returnReasons;        // reason -> count
    public Map<String, Long> productSerialStatus;  // allocated vs free vs returned
}
