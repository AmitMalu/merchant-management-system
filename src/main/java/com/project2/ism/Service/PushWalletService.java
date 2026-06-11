package com.project2.ism.Service;

import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PushWalletService {

    private final MerchantRepository merchantRepository;
    private final WalletAdjustmentService walletAdjustmentService;

    public PushWalletService(MerchantRepository merchantRepository, WalletAdjustmentService walletAdjustmentService) {
        this.merchantRepository = merchantRepository;
        this.walletAdjustmentService = walletAdjustmentService;
    }

    @Transactional
    public void pushWalletFromFranchiseToMerchant(
            Long franchiseId,
            Long merchantId,
            BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }

        // Validate merchant belongs to franchise
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        if (!merchant.getFranchise().getId().equals(franchiseId)) {
            throw new RuntimeException("Merchant does not belong to this franchise");
        }

        String remark = "WALLET_PUSH_TO_MERCHANT_" + merchantId;

        // Debit from Franchise (this already checks balance + locks row)
        walletAdjustmentService.adjustFranchiseWallet(
                franchiseId,
                "DEBIT",
                amount,
                remark
        );

        // Credit to Merchant
        walletAdjustmentService.adjustMerchantWallet(
                merchantId,
                "CREDIT",
                amount,
                remark
        );
    }

}
