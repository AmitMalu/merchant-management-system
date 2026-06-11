package com.project2.ism.Service;

import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Model.MerchantTransactionDetails;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import com.project2.ism.Repository.MerchantTransDetRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MerchantTransDetService {

    private final MerchantTransDetRepository merchantTransDetRepository;

    public MerchantTransDetService(MerchantTransDetRepository merchantTransDetRepository) {
        this.merchantTransDetRepository = merchantTransDetRepository;
    }

    public List<MerchantTransactionDetails> getAllFranchiseTransactionDetails() {
        return merchantTransDetRepository.findAll();
    }

    public Optional<MerchantTransactionDetails> getFranchiseTransactionDetailsById(Long id) {
        return merchantTransDetRepository.findById(id);
    }

    public Optional<MerchantTransactionDetails> getFranchiseTransactionDetailsByTransactionDetailsId(Long transactionId) {
        return merchantTransDetRepository.findByTransactionId(transactionId);
    }

    public MerchantTransactionDetails saveTransaction(MerchantTransactionDetails merchantTransactionDetails) {
        return merchantTransDetRepository.save(merchantTransactionDetails);
    }

    public void deleteTransactionDetails(Long id) {
        merchantTransDetRepository.deleteById(id);
    }
}


