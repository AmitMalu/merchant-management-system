package com.project2.ism.Service;

import com.project2.ism.DTO.BillPayConfigDTO;
import com.project2.ism.DTO.BillPayProviderDTO;
import com.project2.ism.DTO.BillPayProviderRequestDTO;
import com.project2.ism.DTO.BillPayServiceDTO;
import com.project2.ism.Repository.BillAvenueConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author SHUBHAM KHOPADE
 */

@Service
@Transactional
public class BillPayConfigService {

    private final BillAvenueConfigRepository billAvenueConfigRepository;

    public BillPayConfigService(BillAvenueConfigRepository billAvenueConfigRepository) {
        this.billAvenueConfigRepository = billAvenueConfigRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBillAvenueServices(BillPayConfigDTO req) {

        List<BillPayServiceDTO> data = billAvenueConfigRepository
                .findDistinctServicesByVendorName(req.getVendorName())
                .stream()
                .map(BillPayServiceDTO::new)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", 200);
        response.put("message", "Bill Avenue Service List Fetched Successfully");
        response.put("vendorName", req.getVendorName());
        response.put("data", data);

        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBillAvenueProviders(BillPayProviderRequestDTO req) {

        List<BillPayProviderDTO> providers =
                billAvenueConfigRepository.findProvidersByServiceName(
                        req.getVendorName(),
                        req.getServiceName()
                );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCode", 200);
        response.put("message", "Bill Avenue Provider List Fetched Successfully");
        response.put("vendorName", req.getVendorName());
        response.put("serviceName", req.getServiceName());
        response.put("data", providers);

        return response;
    }
}
