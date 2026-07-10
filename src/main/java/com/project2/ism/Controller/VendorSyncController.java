package com.project2.ism.Controller;

import com.project2.ism.Service.VimoPayClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment-vimo")
public class VendorSyncController {

    private static final Long DEFAULT_PAYOUT_VENDOR_ID = 1L; // ViMoPay - TODO: Make dynamic when multiple vendors available

    private final VimoPayClientService vimoPayClientService;

    public VendorSyncController(VimoPayClientService vimoPayClientService) {
        this.vimoPayClientService = vimoPayClientService;
    }

    @PostMapping("/{vendorId}/sync")
    public ResponseEntity<?> syncVendorLists(@PathVariable Long vendorId) {
        vimoPayClientService.fetchAndSaveAll(vendorId);
        return ResponseEntity.ok().body("Sync completed");
    }

    @PostMapping("/{vendorId}/sync-bbps")
    public ResponseEntity<?> syncVimoCreditCardListApi(@PathVariable Long vendorId) {
        vimoPayClientService.fetchAndSaveAllCreditCard(vendorId);
        return ResponseEntity.ok().body("Sync Completed");
    }

    @PostMapping("/{vendorId}/{billerCode}/sync-bbps")
    public ResponseEntity<?> syncVimoCreditCardBillerCodeList(@PathVariable Long vendorId, @PathVariable String billerCode) {
        vimoPayClientService.fetchAndSaveAllCreditCardBillerCode(vendorId, billerCode);
        return ResponseEntity.ok().body("Biller Code List Sync Completed");
    }

}
