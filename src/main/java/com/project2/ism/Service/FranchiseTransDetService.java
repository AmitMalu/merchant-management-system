package com.project2.ism.Service;

import com.project2.ism.Model.FranchiseTransactionDetails;
import com.project2.ism.Repository.FranchiseTransDetRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FranchiseTransDetService {

    private final FranchiseTransDetRepository franchiseTransDetRepository;

    public FranchiseTransDetService(FranchiseTransDetRepository franchiseTransDetRepository) {
        this.franchiseTransDetRepository = franchiseTransDetRepository;
    }

    public List<FranchiseTransactionDetails> getAllFranchiseTransactionDetails() {
        return franchiseTransDetRepository.findAll();
    }

    public Optional<FranchiseTransactionDetails> getFranchiseTransactionDetailsById(Long id) {
        return franchiseTransDetRepository.findById(id);
    }

    public Optional<FranchiseTransactionDetails> getFranchiseTransactionDetailsByTransactionDetailsId(Long transactionId) {
        return franchiseTransDetRepository.findByTransactionId(transactionId);
    }

    public FranchiseTransactionDetails saveTransaction(FranchiseTransactionDetails franchiseTransactionDetails) {
        return franchiseTransDetRepository.save(franchiseTransactionDetails);
    }

    public void deleteTransactionDetails(Long id) {
        franchiseTransDetRepository.deleteById(id);
    }
}
