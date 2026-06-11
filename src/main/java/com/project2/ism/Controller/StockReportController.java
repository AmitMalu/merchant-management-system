package com.project2.ism.Controller;

// StockReportController.java

import com.project2.ism.DTO.ReportDTO.StockReportResponseDTO;
import com.project2.ism.Service.StockReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/reports/stock")
@CrossOrigin(origins = "*")
public class StockReportController {

    @Autowired
    private StockReportService stockReportService;

    @GetMapping
    public ResponseEntity<StockReportResponseDTO> getStockReport(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long franchiseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate
    ) {
        StockReportResponseDTO report = stockReportService.generateStockReport(
                status, productId, merchantId, franchiseId, fromDate, toDate
        );
        return ResponseEntity.ok(report);
    }
}