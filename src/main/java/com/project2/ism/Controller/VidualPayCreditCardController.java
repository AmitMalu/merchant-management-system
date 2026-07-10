package com.project2.ism.Controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.project2.ism.Service.VidualPayCreditCardService;
import com.project2.ism.request.BillerDetailsRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/vidual-pay/credit-card")
public class VidualPayCreditCardController {

    private final VidualPayCreditCardService vidualPayCreditCardService;

    public VidualPayCreditCardController(VidualPayCreditCardService vidualPayCreditCardService) {
        this.vidualPayCreditCardService = vidualPayCreditCardService;
    }

    @PostMapping("/biller-details")
    public ResponseEntity<?> getBillerDetails(
            @RequestBody BillerDetailsRequest request) throws JsonProcessingException {

        return ResponseEntity.ok(
                vidualPayCreditCardService.getBillerDetails(
                        request.getBillerCode(), request.getVendorId()
                )
        );
    }
}
