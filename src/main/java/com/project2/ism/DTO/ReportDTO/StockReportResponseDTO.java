package com.project2.ism.DTO.ReportDTO;

// StockReportResponseDTO.java

import java.util.List;

public class StockReportResponseDTO {
    private List<StockReportDTO> stockReports;
    private StockReportSummaryDTO summary;

    public StockReportResponseDTO() {}

    public StockReportResponseDTO(List<StockReportDTO> stockReports, StockReportSummaryDTO summary) {
        this.stockReports = stockReports;
        this.summary = summary;
    }

    public List<StockReportDTO> getStockReports() {
        return stockReports;
    }

    public void setStockReports(List<StockReportDTO> stockReports) {
        this.stockReports = stockReports;
    }

    public StockReportSummaryDTO getSummary() {
        return summary;
    }

    public void setSummary(StockReportSummaryDTO summary) {
        this.summary = summary;
    }
}