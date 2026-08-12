package com.project2.ism.Controller;

import com.project2.ism.DTO.BillPayConfigDTO;
import com.project2.ism.DTO.BillPayProviderRequestDTO;
import com.project2.ism.Service.BillPayConfigService;
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
}
