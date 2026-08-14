package com.project2.ism.Controller;

import com.project2.ism.DTO.BillPayConfigDTO;
import com.project2.ism.DTO.BillPayProviderRequestDTO;
import com.project2.ism.DTO.BillerFetchRequestDTO;
import com.project2.ism.DTO.FetchBillerInfoRequest;
import com.project2.ism.Service.BillPayConfigService;
import com.project2.ism.response.CommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * @author SHUBHAM KHOPADE
 */
@RestController
@RequestMapping("/billpay/config")
public class BillPayConfigController {

    private static final Logger log =
            LoggerFactory.getLogger(BillPayConfigController.class);

    private final BillPayConfigService billPayConfigService;

    public BillPayConfigController(
            BillPayConfigService billPayConfigService) {
        this.billPayConfigService = billPayConfigService;
    }

    @PostMapping("/services")
    public Map<String, Object> getServices(
            @RequestBody BillPayConfigDTO req) {

        return billPayConfigService.getBillAvenueServices(req);
    }

    @PostMapping("/providers")
    public Map<String, Object> getProviders(
            @RequestBody BillPayProviderRequestDTO req) {

        return billPayConfigService.getBillAvenueProviders(req);
    }

    @PostMapping("/biller-info")
    public CommonResponse<Object> fetchBiller(
            @RequestBody FetchBillerInfoRequest request)
            throws Exception {

        return billPayConfigService.fetchBillerInfo(request);
    }

    @PostMapping("/bill-fetch")
    public CommonResponse<Object> billFetch(
            @RequestBody BillerFetchRequestDTO request) throws Exception {

        log.info(
                "Bill fetch request received. requestId={}",
                request != null ? request.getRequestId() : null
        );

        Object response = billPayConfigService.fetchBill(request);

        log.info(
                "Bill fetch request completed successfully. requestId={}",
                request != null ? request.getRequestId() : null
        );

        return CommonResponse.success(
                response,
                "Bill fetched successfully"
        );
    }
}

