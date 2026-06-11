package com.project2.ism.Service;


import com.project2.ism.Model.VendorTransactions;
import com.project2.ism.Repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public VendorTransactions saveTransaction(VendorTransactions vendorTransactions) {
        return transactionRepository.save(vendorTransactions);
    }

    public List<VendorTransactions> saveAll(List<VendorTransactions> vendorTransactions) {
        return transactionRepository.saveAll(vendorTransactions);
    }

    public List<VendorTransactions> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Optional<VendorTransactions> getTransactionById(Long id) {
        return transactionRepository.findById(id);
    }

    public void deleteTransaction(Long id) {
        transactionRepository.deleteById(id);
    }
}