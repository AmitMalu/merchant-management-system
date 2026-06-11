package com.project2.ism.Controller;


import com.project2.ism.Service.WalletAdjustmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/wallet-adjustment")
public class WalletAdjustmentController {

    @Autowired
    private WalletAdjustmentService walletAdjustmentService;

    @PostMapping("/franchise")
    public ResponseEntity<?> adjustFranchiseWallet(@RequestBody WalletAdjustmentRequest request) {
        try {
            walletAdjustmentService.adjustFranchiseWallet(
                    request.getCustomerId(),
                    request.getActionOnBalance(),
                    request.getAmount(),
                    request.getRemark()
            );
            return ResponseEntity.ok().body(new MessageResponse("Franchise wallet adjusted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/merchant")
    public ResponseEntity<?> adjustMerchantWallet(@RequestBody WalletAdjustmentRequest request) {
        try {
            walletAdjustmentService.adjustMerchantWallet(
                    request.getCustomerId(),
                    request.getActionOnBalance(),
                    request.getAmount(),
                    request.getRemark()
            );
            return ResponseEntity.ok().body(new MessageResponse("Merchant wallet adjusted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @GetMapping("/franchise/{franchiseId}")
    public ResponseEntity<?> getFranchiseWalletBalance(@PathVariable Long franchiseId) {
        try {
            BigDecimal balance = walletAdjustmentService.getFranchiseWalletBalance(franchiseId);
            return ResponseEntity.ok().body(new WalletBalanceResponse(franchiseId, balance));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<?> getMerchantWalletBalance(@PathVariable Long merchantId) {
        try {
            BigDecimal balance = walletAdjustmentService.getMerchantWalletBalance(merchantId);
            return ResponseEntity.ok().body(new WalletBalanceResponse(merchantId, balance));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    // Inner response class for balance
    static class WalletBalanceResponse {
        private Long customerId;
        private BigDecimal availableBalance;

        public WalletBalanceResponse(Long customerId, BigDecimal availableBalance) {
            this.customerId = customerId;
            this.availableBalance = availableBalance;
        }

        public Long getCustomerId() {
            return customerId;
        }

        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }

        public BigDecimal getAvailableBalance() {
            return availableBalance;
        }

        public void setAvailableBalance(BigDecimal availableBalance) {
            this.availableBalance = availableBalance;
        }
    }


    static class WalletAdjustmentRequest {
        private Long customerId;
        private String actionOnBalance;
        private BigDecimal amount;
        private String remark;

        public Long getCustomerId() {
            return customerId;
        }

        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }

        public String getActionOnBalance() {
            return actionOnBalance;
        }

        public void setActionOnBalance(String actionOnBalance) {
            this.actionOnBalance = actionOnBalance;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    static class MessageResponse {
        private String message;

        public MessageResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}