package com.project2.ism.Controller;

import com.project2.ism.DTO.PaymentDTO.VendorBankDTO;
import com.project2.ism.DTO.PaymentDTO.VendorBankResponseDTO;
import com.project2.ism.Service.VendorBankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/vimo-banks")
public class VendorBankController {

    @Value("${payout.default.vendor.id}")
    private Long defaultPayoutVendorId; // ViMoPay - TODO: Make dynamic when multiple vendors available


    private final VendorBankService vendorBankService;

    public VendorBankController(VendorBankService vendorBankService) {
        this.vendorBankService = vendorBankService;
    }

    @GetMapping
    public List<VendorBankResponseDTO> getVendorBanks() {
        return vendorBankService.getVendorBankList(defaultPayoutVendorId)
                .stream()
                .map(bank -> {
                    VendorBankResponseDTO dto = new VendorBankResponseDTO();
                    dto.setBankName(bank.getBankName());
                    dto.setBankCode(bank.getBankCode());
                    return dto;
                })
                .toList();
    }
}

