package com.project2.ism.Service;

import com.project2.ism.DTO.PayoutDTO.VerifyAndAddBankRequest;
import com.project2.ism.Exception.BankVerificationException;
import com.project2.ism.Model.Payout.PayoutBanks;
import com.project2.ism.Repository.PayoutBankRepository;
import com.project2.ism.request.BankVerificationRequest;
import com.project2.ism.response.VerificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PayoutBankService {

    private static final Logger log =
            LoggerFactory.getLogger(PayoutBankService.class);

    @Autowired
    private PayoutBankRepository payoutBankRepository;

    @Autowired
    private VerificationService verificationService;

    private static final int MAX_BANKS_PER_CUSTOMER = 20;

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

    public PayoutBanks verifyAndAddBank(Long customerId, VerifyAndAddBankRequest request) throws Exception {
        // Step 1: Validate basic requirements
        validateBankDetails(request);

        // Step 2: Check if customer already has 20 banks
        long bankCount = payoutBankRepository.countByCustomerTypeAndCustomerId(
                request.getCustomerType(), customerId);

        if (bankCount >= MAX_BANKS_PER_CUSTOMER) {
            throw new Exception("Maximum 20 banks allowed per customer");
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

            log.info("========== VERIFY & ADD BANK START ==========");
            log.info("CustomerId      : {}", customerId);
            log.info("CustomerType    : {}", request.getCustomerType());
            log.info("Account Number  : {}", request.getAccountNumber());
            log.info("IFSC            : {}", request.getIfscCode());

            BankVerificationRequest bankVerificationRequest = new BankVerificationRequest();
            bankVerificationRequest.setAccountNumber(request.getAccountNumber());
            bankVerificationRequest.setIfsc(request.getIfscCode());

            log.info("Calling Surepass Pennyless API...");

            VerificationResponse verificationResponse =
                    verificationService.verifyBank(bankVerificationRequest);

            log.info("Surepass Status : {}", verificationResponse.getStatus());
            log.info("Surepass Message: {}", verificationResponse.getMessage());

            if (verificationResponse == null ||
                    !"SUCCESS".equalsIgnoreCase(verificationResponse.getStatus())) {

                log.error("Surepass verification failed.");

                throw new BankVerificationException(
                        "Bank verification failed. Please check your bank details."
                );
            }

            Map<String, Object> data =
                    (Map<String, Object>) verificationResponse.getData();

            if (data == null) {

                log.error("Surepass returned NULL data.");

                throw new BankVerificationException(
                        "Bank verification failed. No data received from Surepass."
                );
            }

            log.info("Surepass Response Data : {}", data);

            String bankHolderName = String.valueOf(data.get("full_name"));

            Map<String, Object> ifscDetails =
                    (Map<String, Object>) data.get("ifsc_details");

            String bankName =
                    ifscDetails != null && ifscDetails.get("bank_name") != null
                            ? String.valueOf(ifscDetails.get("bank_name"))
                            : request.getBankName();

            String stateName =
                    ifscDetails != null && ifscDetails.get("state") != null
                            ? String.valueOf(ifscDetails.get("state"))
                            : request.getStateName();

            log.info("Account Holder : {}", bankHolderName);
            log.info("Bank Name      : {}", bankName);
            log.info("State          : {}", stateName);

            PayoutBanks payoutBank = new PayoutBanks(
                    request.getCustomerType(),
                    customerId,
                    bankHolderName,
                    bankName,
                    request.getIfscCode(),
                    stateName,
                    request.getAccountNumber()
            );

            payoutBank.setVerified(true);

            log.info("Saving verified bank into database...");

            PayoutBanks savedBank = payoutBankRepository.save(payoutBank);

            log.info("Bank saved successfully. BankId : {}", savedBank.getId());
            log.info("========== VERIFY & ADD BANK COMPLETED ==========");

            return savedBank;

        } catch (BankVerificationException e) {

            log.error("Bank verification failed : {}", e.getMessage(), e);

            throw e;

        } catch (Exception e) {

            log.error("Error while verifying and adding bank.", e);

            throw new Exception("Failed to verify and add bank: " + e.getMessage());
        }

//        try {
//            // Step 6: Call external bank verification API
//            // TODO: Implement actual bank verification API call
//        /*
//        BankVerificationRequest verificationRequest = new BankVerificationRequest();
//        verificationRequest.setIfscCode(request.getIfscCode());
//        verificationRequest.setBankName(request.getBankName());
//        verificationRequest.setAccountNumber(request.getAccountNumber());
//        verificationRequest.setBankHolderName(request.getBankHolderName());
//        verificationRequest.setAmount(BigDecimal.ONE); // ₹1 for verification
//
//        // Call external API (e.g., Razorpay, Cashfree, etc.)
//        BankVerificationResponse verificationResponse = externalBankApi.verifyAccount(verificationRequest);
//
//        if (!verificationResponse.isSuccess()) {
//            // Verification failed, refund the ₹1 back to wallet
//            walletService.credit(
//                customerId,
//                request.getCustomerType(),
//                BigDecimal.ONE,
//                "BANK_VERIFICATION_REFUND",
//                "Refund for failed bank verification"
//            );
//            throw new BankVerificationException("Bank verification failed: " + verificationResponse.getMessage());
//        }
//
//        // Optional: Store verification transaction ID for future reference
//        String verificationTransactionId = verificationResponse.getTransactionId();
//        */
//
//            // For now, simulate successful verification
//            boolean verificationSuccess = simulateBankVerification(request);
//            if (!verificationSuccess) {
//                // TODO: Refund ₹1 back to wallet when actual implementation is done
//                throw new BankVerificationException("Bank verification failed. Please check your bank details.");
//            }
//
//            // Step 7: If verification successful, save bank details to database
//            PayoutBanks payoutBank = new PayoutBanks(
//                    request.getCustomerType(),
//                    customerId,
//                    request.getBankHolderName(),
//                    request.getBankName(),
//                    request.getIfscCode(),
//                    request.getStateName(),
//                    request.getAccountNumber()
//
//            );
//
//            // Set as verified since we successfully verified it
//            payoutBank.setVerified(true);
//            // TODO: Store verification transaction ID when available
//            // payoutBank.setVerificationTransactionId(verificationTransactionId);
//
//            // Save to database
//            PayoutBanks savedBank = payoutBankRepository.save(payoutBank);
//
//            // Step 8: Create audit log entry
//            // TODO: Implement audit logging
//        /*
//        auditService.log(
//            customerId,
//            request.getCustomerType(),
//            "BANK_ADDED",
//            "Bank account verified and added: " + request.getAccountNumber(),
//            savedBank.getId()
//        );
//        */
//
//            return savedBank;
//
//        } catch (BankVerificationException e) {
//            // TODO: Refund ₹1 back to wallet if verification fails
//        /*
//        walletService.credit(
//            customerId,
//            request.getCustomerType(),
//            BigDecimal.ONE,
//            "BANK_VERIFICATION_REFUND",
//            "Refund for failed bank verification"
//        );
//        */
//            throw e;
//        } catch (Exception e) {
//            // TODO: Refund ₹1 back to wallet for any other errors
//        /*
//        walletService.credit(
//            customerId,
//            request.getCustomerType(),
//            BigDecimal.ONE,
//            "BANK_VERIFICATION_REFUND",
//            "Refund due to error during bank verification"
//        );
//        */
//            throw new Exception("Failed to verify and add bank: " + e.getMessage());
//        }
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