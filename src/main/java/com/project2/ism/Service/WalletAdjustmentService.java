package com.project2.ism.Service;

import com.project2.ism.Model.*;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class WalletAdjustmentService {

    private final FranchiseRepository franchiseRepository;
    private final MerchantRepository merchantRepository;
    private final FranchiseWalletRepository franchiseWalletRepository;
    private final MerchantWalletRepository merchantWalletRepository;
    private final FranchiseTransDetRepository franchiseTransactionRepository;
    private final MerchantTransDetRepository merchantTransactionRepository;

    public WalletAdjustmentService(FranchiseRepository franchiseRepository, MerchantRepository merchantRepository, FranchiseWalletRepository franchiseWalletRepository, MerchantWalletRepository merchantWalletRepository, FranchiseTransDetRepository franchiseTransactionRepository, MerchantTransDetRepository merchantTransactionRepository) {
        this.franchiseRepository = franchiseRepository;
        this.merchantRepository = merchantRepository;
        this.franchiseWalletRepository = franchiseWalletRepository;
        this.merchantWalletRepository = merchantWalletRepository;
        this.franchiseTransactionRepository = franchiseTransactionRepository;
        this.merchantTransactionRepository = merchantTransactionRepository;
    }

    @Transactional
    public void adjustFranchiseWallet(Long franchiseId, String actionOnBalance, BigDecimal amount, String remark) {
        Franchise franchise = franchiseRepository.findById(franchiseId)
                .orElseThrow(() -> new RuntimeException("Franchise not found"));

        FranchiseWallet franchiseWallet = franchiseWalletRepository.findByFranchiseIdForUpdate(franchise.getId())
                .orElseGet(() -> {
                    FranchiseWallet w = new FranchiseWallet();
                    Franchise fRef = new Franchise();
                    fRef.setId(franchise.getId());
                    w.setFranchise(fRef);
                    w.setAvailableBalance(BigDecimal.ZERO);
                    w.setLastUpdatedAmount(BigDecimal.ZERO);
                    w.setLastUpdatedAt(LocalDateTime.now());
                    w.setTotalCash(BigDecimal.ZERO);
                    w.setCutOfAmount(BigDecimal.ZERO);
                    w.setUsedCash(BigDecimal.ZERO);
                    return franchiseWalletRepository.save(w);
                });

        BigDecimal balanceBeforeTran = franchiseWallet.getAvailableBalance();
        BigDecimal balanceAfterTran;

        if ("CREDIT".equalsIgnoreCase(actionOnBalance)) {
            balanceAfterTran = balanceBeforeTran.add(amount);
        } else if ("DEBIT".equalsIgnoreCase(actionOnBalance)) {
            if (balanceBeforeTran.compareTo(amount) < 0) {
                throw new RuntimeException("Insufficient balance");
            }
            balanceAfterTran = balanceBeforeTran.subtract(amount);
        } else {
            throw new RuntimeException("Invalid action on balance");
        }

        LocalDateTime now = LocalDateTime.now();

        FranchiseTransactionDetails transaction = new FranchiseTransactionDetails();
        transaction.setFranchise(franchise);
        if (actionOnBalance.equals("DEBIT")) {
            transaction.setAmount(amount.negate());
        } else {
            transaction.setAmount(amount);
        }

        transaction.setBalBeforeTran(balanceBeforeTran);
        transaction.setBalAfterTran(balanceAfterTran);
        transaction.setFinalBalance(balanceAfterTran);
        transaction.setRemarks(remark);
        transaction.setActionOnBalance(actionOnBalance);
        transaction.setTransactionType(actionOnBalance);
        transaction.setTransactionDate(now);
        transaction.setUpdatedDateAndTimeOfTransaction(now);
        transaction.setTranStatus("SUCCESS");
        transaction.setService("ADMIN_ADJUSTMENT");

        franchiseTransactionRepository.save(transaction);

        BigDecimal txnAmount = actionOnBalance.equalsIgnoreCase("DEBIT")
                ? amount.negate()
                : amount;
        franchiseWallet.setAvailableBalance(balanceAfterTran);
        franchiseWallet.setLastUpdatedAmount(txnAmount);
        franchiseWallet.setLastUpdatedAt(now);

        franchiseWalletRepository.save(franchiseWallet);
    }

    @Transactional
    public void adjustMerchantWallet(Long merchantId, String actionOnBalance, BigDecimal amount, String remark) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        MerchantWallet merchantWallet = merchantWalletRepository.findByMerchantIdForUpdate(merchant.getId())
                .orElseGet(() -> {
                    MerchantWallet w = new MerchantWallet();
                    Merchant mRef = new Merchant();
                    mRef.setId(merchant.getId());
                    w.setMerchant(mRef);
                    w.setAvailableBalance(BigDecimal.ZERO);
                    w.setLastUpdatedAmount(BigDecimal.ZERO);
                    w.setLastUpdatedAt(LocalDateTime.now());
                    w.setTotalCash(BigDecimal.ZERO);
                    w.setCutOfAmount(BigDecimal.ZERO);
                    w.setUsedCash(BigDecimal.ZERO);
                    return merchantWalletRepository.save(w);
                });

        BigDecimal balanceBeforeTran = merchantWallet.getAvailableBalance();
        BigDecimal balanceAfterTran;

        if ("CREDIT".equalsIgnoreCase(actionOnBalance)) {
            balanceAfterTran = balanceBeforeTran.add(amount);
        } else if ("DEBIT".equalsIgnoreCase(actionOnBalance)) {
            if (balanceBeforeTran.compareTo(amount) < 0) {
                throw new RuntimeException("Insufficient balance");
            }
            balanceAfterTran = balanceBeforeTran.subtract(amount);
        } else {
            throw new RuntimeException("Invalid action on balance");
        }

        LocalDateTime now = LocalDateTime.now();

        MerchantTransactionDetails transaction = new MerchantTransactionDetails();
        transaction.setMerchant(merchant);
        if (actionOnBalance.equals("DEBIT")) {
            transaction.setAmount(amount.negate());
        } else {
            transaction.setAmount(amount);
        }

        transaction.setBalBeforeTran(balanceBeforeTran);
        transaction.setBalAfterTran(balanceAfterTran);
        transaction.setFinalBalance(balanceAfterTran);
        transaction.setRemarks(remark);
        transaction.setActionOnBalance(actionOnBalance);
        transaction.setTransactionType(actionOnBalance);
        transaction.setTransactionDate(now);
        transaction.setUpdatedDateAndTimeOfTransaction(now);
        transaction.setTranStatus("SUCCESS");
        transaction.setService("ADMIN_ADJUSTMENT");

        merchantTransactionRepository.save(transaction);

        BigDecimal txnAmount = actionOnBalance.equalsIgnoreCase("DEBIT")
                ? amount.negate()
                : amount;

        merchantWallet.setAvailableBalance(balanceAfterTran);
        merchantWallet.setLastUpdatedAmount(txnAmount);
        merchantWallet.setLastUpdatedAt(now);

        merchantWalletRepository.save(merchantWallet);
    }

    public BigDecimal getFranchiseWalletBalance(Long franchiseId) {
        return franchiseWalletRepository.findByFranchiseId(franchiseId)
                .map(WalletBase::getAvailableBalance)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getMerchantWalletBalance(Long merchantId) {
        return merchantWalletRepository.findByMerchantId(merchantId)
                .map(WalletBase::getAvailableBalance)
                .orElse(BigDecimal.ZERO);
    }

}
