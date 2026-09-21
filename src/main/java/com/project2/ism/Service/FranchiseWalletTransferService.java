package com.project2.ism.Service;

import com.project2.ism.DTO.MerchantListDTO;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.MerchantRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Franchise-initiated wallet transfers to/from their own merchants
 * ("Push/Pull Wallet"). Credit moves money out of the franchise's own wallet
 * into the merchant's wallet; Debit moves it back. Both directions reuse
 * WalletAdjustmentService's existing, already-proven balance-lock and
 * insufficient-balance logic — this class only adds the piece that was
 * missing: resolving which franchise is actually calling from the
 * authenticated session (never trusting a client-supplied franchise id), and
 * verifying the target merchant actually belongs to that franchise before
 * touching any money.
 */
@Service
public class FranchiseWalletTransferService {

    // Hard server-side ceiling per transfer, independent of whatever the
    // frontend enforces — a client-side-only limit is not a real limit.
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000");

    private final FranchiseService franchiseService;
    private final MerchantService merchantService;
    private final MerchantRepository merchantRepository;
    private final WalletAdjustmentService walletAdjustmentService;

    public FranchiseWalletTransferService(FranchiseService franchiseService,
                                           MerchantService merchantService,
                                           MerchantRepository merchantRepository,
                                           WalletAdjustmentService walletAdjustmentService) {
        this.franchiseService = franchiseService;
        this.merchantService = merchantService;
        this.merchantRepository = merchantRepository;
        this.walletAdjustmentService = walletAdjustmentService;
    }

    private Franchise resolveAuthenticatedFranchise() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Not authenticated");
        }
        return franchiseService.getFranchiseByEmail(authentication.getName());
    }

    private Merchant resolveOwnMerchant(Franchise franchise, Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        if (merchant.getFranchise() == null || !merchant.getFranchise().getId().equals(franchise.getId())) {
            throw new RuntimeException("Merchant does not belong to this franchise");
        }
        return merchant;
    }

    public Map<String, Object> getProfile() {
        Franchise franchise = resolveAuthenticatedFranchise();
        Map<String, Object> profile = new HashMap<>();
        profile.put("franchiseId", franchise.getId());
        profile.put("franchiseName", franchise.getFranchiseName());
        profile.put("walletBalance", walletAdjustmentService.getFranchiseWalletBalance(franchise.getId()));
        return profile;
    }

    public List<MerchantListDTO> getOwnMerchants() {
        Franchise franchise = resolveAuthenticatedFranchise();
        return merchantService.getMerchantsByFranchise(franchise.getId());
    }

    @Transactional
    public Map<String, Object> transfer(Long merchantId, String action, BigDecimal amount, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new RuntimeException("Amount cannot exceed " + MAX_AMOUNT.toPlainString());
        }
        if (!"CREDIT".equalsIgnoreCase(action) && !"DEBIT".equalsIgnoreCase(action)) {
            throw new RuntimeException("Invalid action — must be CREDIT or DEBIT");
        }

        Franchise franchise = resolveAuthenticatedFranchise();
        Merchant merchant = resolveOwnMerchant(franchise, merchantId);

        String finalRemark = (remark != null && !remark.isBlank())
                ? remark
                : ("CREDIT".equalsIgnoreCase(action) ? "WALLET_CREDIT_TO_MERCHANT_" : "WALLET_DEBIT_FROM_MERCHANT_") + merchantId;

        if ("CREDIT".equalsIgnoreCase(action)) {
            // Debit the source (franchise) first — this is where the
            // insufficient-balance check lives, so a franchise can never
            // push out more than it actually has.
            walletAdjustmentService.adjustFranchiseWallet(franchise.getId(), "DEBIT", amount, finalRemark, "FRANCHISE_WALLET_TRANSFER");
            walletAdjustmentService.adjustMerchantWallet(merchant.getId(), "CREDIT", amount, finalRemark, "FRANCHISE_WALLET_TRANSFER");
        } else {
            // Debit the source (merchant) first, same reasoning in reverse.
            walletAdjustmentService.adjustMerchantWallet(merchant.getId(), "DEBIT", amount, finalRemark, "FRANCHISE_WALLET_TRANSFER");
            walletAdjustmentService.adjustFranchiseWallet(franchise.getId(), "CREDIT", amount, finalRemark, "FRANCHISE_WALLET_TRANSFER");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("merchantId", merchant.getId());
        result.put("merchantBalance", walletAdjustmentService.getMerchantWalletBalance(merchant.getId()));
        result.put("franchiseBalance", walletAdjustmentService.getFranchiseWalletBalance(franchise.getId()));
        return result;
    }
}
