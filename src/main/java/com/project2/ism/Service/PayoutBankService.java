package com.project2.ism.Service;

import com.project2.ism.DTO.PayoutDTO.VerifyAndAddBankRequest;
import com.project2.ism.Exception.BankVerificationException;
import com.project2.ism.Model.Payout.PayoutBanks;
import com.project2.ism.Repository.PayoutBankRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PayoutBankService {

    @Autowired
    private PayoutBankRepository payoutBankRepository;

    private static final int MAX_BANKS_PER_CUSTOMER = 3;

    public List<PayoutBanks> getBanksByCustomer(String customerType, Long customerId) {
        return payoutBankRepository.findByCustomerTypeAndCustomerId(customerType, customerId);
    }

    public void deleteBank(Long bankId) throws Exception {
        Optional<PayoutBanks> bankOpt = payoutBankRepository.findById(bankId);

        if (bankOpt.isEmpty()) {
            throw new Exception("Bank not found");
        }

        payoutBankRepository.deleteById(bankId);
    }

    public PayoutBanks getBankById(Long bankId) throws Exception {
        Optional<PayoutBanks> bankOpt = payoutBankRepository.findById(bankId);

        if (bankOpt.isEmpty()) {
            throw new Exception("Bank not found");
        }

        return bankOpt.get();
    }

    // Simulate bank verification (replace with actual API integration)
    private boolean simulateBankVerification(PayoutBanks bank) {
        // Simulate verification logic
        // In real implementation, integrate with bank verification API
        return bank.getIfscCode() != null && bank.getIfscCode().length() == 11
                && bank.getAccountNumber() != null && bank.getAccountNumber().length() >= 9;
    }

    public PayoutBanks verifyAndAddBank(Long customerId, VerifyAndAddBankRequest request) throws Exception {
        // Step 1: Validate basic requirements
        validateBankDetails(request);

        // Step 2: Check if customer already has 3 banks
        long bankCount = payoutBankRepository.countByCustomerTypeAndCustomerId(
                request.getCustomerType(), customerId);

        if (bankCount >= MAX_BANKS_PER_CUSTOMER) {
            throw new Exception("Maximum 3 banks allowed per customer");
        }

        // Step 3: Check if account number already exists for this customer
        boolean accountExists = payoutBankRepository.existsByCustomerTypeAndCustomerIdAndAccountNumber(
                request.getCustomerType(), customerId, request.getAccountNumber());

        if (accountExists) {
            throw new Exception("Account number already exists for this customer");
        }

        // Step 4: Check wallet balance (minimum ₹1 required for verification)
        // TODO: Implement wallet service integration
    /*
    WalletService walletService = // inject wallet service
    BigDecimal walletBalance = walletService.getBalance(customerId, request.getCustomerType());

    if (walletBalance.compareTo(BigDecimal.ONE) < 0) {
        throw new InsufficientBalanceException("Insufficient wallet balance. Minimum ₹1 required for bank verification.");
    }
    */

        // Step 5: Debit ₹1 from customer wallet for verification
        // TODO: Implement wallet debit
    /*
    WalletTransaction debitTransaction = walletService.debit(
        customerId,
        request.getCustomerType(),
        BigDecimal.ONE,
        "BANK_VERIFICATION",
        "Bank verification charge for account: " + request.getAccountNumber()
    );
    */

        try {
            // Step 6: Call external bank verification API
            // TODO: Implement actual bank verification API call
        /*
        BankVerificationRequest verificationRequest = new BankVerificationRequest();
        verificationRequest.setIfscCode(request.getIfscCode());
        verificationRequest.setBankName(request.getBankName());
        verificationRequest.setAccountNumber(request.getAccountNumber());
        verificationRequest.setBankHolderName(request.getBankHolderName());
        verificationRequest.setAmount(BigDecimal.ONE); // ₹1 for verification

        // Call external API (e.g., Razorpay, Cashfree, etc.)
        BankVerificationResponse verificationResponse = externalBankApi.verifyAccount(verificationRequest);

        if (!verificationResponse.isSuccess()) {
            // Verification failed, refund the ₹1 back to wallet
            walletService.credit(
                customerId,
                request.getCustomerType(),
                BigDecimal.ONE,
                "BANK_VERIFICATION_REFUND",
                "Refund for failed bank verification"
            );
            throw new BankVerificationException("Bank verification failed: " + verificationResponse.getMessage());
        }

        // Optional: Store verification transaction ID for future reference
        String verificationTransactionId = verificationResponse.getTransactionId();
        */

            // For now, simulate successful verification
            boolean verificationSuccess = simulateBankVerification(request);
            if (!verificationSuccess) {
                // TODO: Refund ₹1 back to wallet when actual implementation is done
                throw new BankVerificationException("Bank verification failed. Please check your bank details.");
            }

            // Step 7: If verification successful, save bank details to database
            PayoutBanks payoutBank = new PayoutBanks(
                    request.getCustomerType(),
                    customerId,
                    request.getBankHolderName(),
                    request.getBankName(),
                    request.getIfscCode(),
                    request.getStateName(),
                    request.getAccountNumber()

            );

            // Set as verified since we successfully verified it
            payoutBank.setVerified(true);
            // TODO: Store verification transaction ID when available
            // payoutBank.setVerificationTransactionId(verificationTransactionId);

            // Save to database
            PayoutBanks savedBank = payoutBankRepository.save(payoutBank);

            // Step 8: Create audit log entry
            // TODO: Implement audit logging
        /*
        auditService.log(
            customerId,
            request.getCustomerType(),
            "BANK_ADDED",
            "Bank account verified and added: " + request.getAccountNumber(),
            savedBank.getId()
        );
        */

            return savedBank;

        } catch (BankVerificationException e) {
            // TODO: Refund ₹1 back to wallet if verification fails
        /*
        walletService.credit(
            customerId,
            request.getCustomerType(),
            BigDecimal.ONE,
            "BANK_VERIFICATION_REFUND",
            "Refund for failed bank verification"
        );
        */
            throw e;
        } catch (Exception e) {
            // TODO: Refund ₹1 back to wallet for any other errors
        /*
        walletService.credit(
            customerId,
            request.getCustomerType(),
            BigDecimal.ONE,
            "BANK_VERIFICATION_REFUND",
            "Refund due to error during bank verification"
        );
        */
            throw new Exception("Failed to verify and add bank: " + e.getMessage());
        }
    }

    // Helper method to validate bank details
    private void validateBankDetails(VerifyAndAddBankRequest request) throws Exception {
        if (request.getIfscCode() == null || request.getIfscCode().length() != 11) {
            throw new Exception("Invalid IFSC code. Must be 11 characters.");
        }

        if (request.getAccountNumber() == null || request.getAccountNumber().length() < 9) {
            throw new Exception("Invalid account number. Must be at least 9 digits.");
        }

        if (request.getBankHolderName() == null || request.getBankHolderName().trim().isEmpty()) {
            throw new Exception("Bank holder name is required.");
        }

        if (request.getBankName() == null || request.getBankName().trim().isEmpty()) {
            throw new Exception("Bank name is required.");
        }
    }

    // Temporary simulation method (remove when actual API is integrated)
    private boolean simulateBankVerification(VerifyAndAddBankRequest request) {
        // For now, just validate basic format
        return request.getIfscCode() != null && request.getIfscCode().length() == 11
                && request.getAccountNumber() != null && request.getAccountNumber().length() >= 9
                && request.getBankHolderName() != null && !request.getBankHolderName().trim().isEmpty();
    }
}