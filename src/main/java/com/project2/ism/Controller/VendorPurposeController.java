package com.project2.ism.Controller;

import com.project2.ism.DTO.PaymentDTO.VendorPurposeDTO;
import com.project2.ism.Service.VendorPurposeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/vimo-purposes")
public class VendorPurposeController {

    @Value("${payout.default.vendor.id}")
    private Long defaultPayoutVendorId; // ViMoPay - TODO: Make dynamic when multiple vendors available

    private final VendorPurposeService vendorPurposeService;

    public VendorPurposeController(VendorPurposeService vendorPurposeService) {
        this.vendorPurposeService = vendorPurposeService;
    }

    @GetMapping
    public List<VendorPurposeDTO> getPurposes() {
        return vendorPurposeService.getVendorPurposes(defaultPayoutVendorId);
    }
}

