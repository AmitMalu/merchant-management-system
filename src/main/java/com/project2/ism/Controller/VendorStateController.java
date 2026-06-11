package com.project2.ism.Controller;

import com.project2.ism.DTO.PaymentDTO.VendorStateDTO;
import com.project2.ism.DTO.PaymentDTO.VendorStateResponseDTO;
import com.project2.ism.Service.VendorStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/vimo-states")
public class VendorStateController {

    @Value("${payout.default.vendor.id}")
    private Long defaultPayoutVendorId; // ViMoPay - TODO: Make dynamic when multiple vendors available



    private final VendorStateService vendorStateService;

    public VendorStateController(VendorStateService vendorStateService) {
        this.vendorStateService = vendorStateService;
    }

    @GetMapping
    public List<VendorStateResponseDTO> getStates() {
        return vendorStateService.getVendorStates(defaultPayoutVendorId);
    }
}

