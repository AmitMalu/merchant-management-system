package com.project2.ism.Controller;

import com.project2.ism.DTO.FranchiseStatsDTO;
import com.project2.ism.DTO.InventoryTransactionStatsDTO;
import com.project2.ism.DTO.MerchantStatsDTO;
import com.project2.ism.DTO.ReportDTO.FranchiseReportsDTO;
import com.project2.ism.DTO.ReportDTO.MerchantReportDTO;
import com.project2.ism.DTO.ReportDTO.SettledUnsettledReportDto;
import com.project2.ism.DTO.ReportDTO.VendorReportsDTO;
import com.project2.ism.Service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/franchises/{id}")
    public ResponseEntity<FranchiseStatsDTO> getFranchiseStats(@PathVariable Long id) {
        return ResponseEntity.ok(statsService.getFranchiseStats(id));
    }

    @GetMapping("/merchants/{id}")
    public ResponseEntity<MerchantStatsDTO> getMerchantStats(@PathVariable Long id) {
        return ResponseEntity.ok(statsService.getMerchantStats(id));
    }


    @GetMapping("/vendor-reports")
    public ResponseEntity<VendorReportsDTO> getVendorReports() {
        return ResponseEntity.ok(statsService.getVendorReports());
    }

    @GetMapping("/franchise-reports")
    public ResponseEntity<List<FranchiseReportsDTO>> getFranchiseReports() {
        List<FranchiseReportsDTO> reports = statsService.getFranchiseReports();
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/merchant-reports")
    public  ResponseEntity<List<MerchantReportDTO>> getMerchantReports(){
        List<MerchantReportDTO> reports = statsService.getMerchantReports();
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/settled-unsettled-reports")
    public ResponseEntity<List<SettledUnsettledReportDto>> getSettledUnsettledReports(
            @RequestParam(required = false) Boolean settled,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "TRANSACTION_DATE") String dateType
    ) {
        LocalDateTime to;
        LocalDateTime from;

        // DEFAULT: last 7 days
        if (fromDate == null && toDate == null) {
            to = LocalDateTime.now();
            from = to.minusDays(7);
        } else {
            from = fromDate != null
                    ? OffsetDateTime.parse(fromDate).toLocalDateTime()
                    : null;

            to = toDate != null
                    ? OffsetDateTime.parse(toDate).toLocalDateTime()
                    : null;
        }

        List<SettledUnsettledReportDto> reports =
                statsService.getSettledUnsettled(settled, from, to, dateType);

        return ResponseEntity.ok(reports);
    }



}

