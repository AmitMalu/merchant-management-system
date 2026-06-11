package com.project2.ism.Controller;

import com.project2.ism.DTO.Vendor.VendorCredentialsRequestDTO;
import com.project2.ism.DTO.Vendor.VendorCredentialsResponseDTO;
import com.project2.ism.Service.PaymentVendorCredentialsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payment-vendor-credentials")
public class PaymentVendorCredentialsController {

    private final PaymentVendorCredentialsService paymentVendorCredentialsService;

    public PaymentVendorCredentialsController(PaymentVendorCredentialsService paymentVendorCredentialsService) {
        this.paymentVendorCredentialsService = paymentVendorCredentialsService;
    }


    @PostMapping
    public VendorCredentialsResponseDTO create(@RequestBody VendorCredentialsRequestDTO dto) {
        return paymentVendorCredentialsService.create(dto);
    }

    @PutMapping("/{id}")
    public VendorCredentialsResponseDTO update(@PathVariable Long id,
                                               @RequestBody VendorCredentialsRequestDTO dto) {
        return paymentVendorCredentialsService.update(id, dto);
    }

    @GetMapping("/{id}")
    public VendorCredentialsResponseDTO getOne(@PathVariable Long id) {
        return paymentVendorCredentialsService.getOne(id);
    }

    @GetMapping
    public List<VendorCredentialsResponseDTO> getAll() {
        return paymentVendorCredentialsService.getAll();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        paymentVendorCredentialsService.delete(id);
    }
}
