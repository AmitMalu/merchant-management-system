package com.project2.ism.DTO;

import com.project2.ism.Enum.RequestedType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PrefundWalletRequestDTO {

    // 🔹 Who is raising request
    @NotNull(message = "Requested type is required")
    private RequestedType requestedType;   // MERCHANT / FRANCHISE

    @NotNull(message = "Requested by id is required")
    private Long requestedById;

    // 🔹 Transaction details
    @NotNull(message = "Deposit amount is required")
    private BigDecimal depositAmount;

    @NotNull(message = "Deposite Date Is Requried")
    private LocalDate depositeDate;

    @NotBlank(message = "Payment mode is required")
    private String paymentMode;

    @NotBlank(message = "Transaction id is required")
    private String tranxId;

    // 🔹 Bank details (MATCH ENTITY)
    @NotBlank(message = "Bank account name is required")
    private String bankAccountName;

    @NotBlank(message = "Bank account number is required")
    private String bankAccountNumber;

    @NotBlank(message = "Bank holder name is required")
    private String bankHolderName;

    // 🔹 Optional
    private String narration;

    // 🔹 Image
    private MultipartFile depositImage;

    public @NotNull(message = "Requested type is required") RequestedType getRequestedType() {
        return requestedType;
    }

    public void setRequestedType(@NotNull(message = "Requested type is required") RequestedType requestedType) {
        this.requestedType = requestedType;
    }

    public @NotNull(message = "Requested by id is required") Long getRequestedById() {
        return requestedById;
    }

    public void setRequestedById(@NotNull(message = "Requested by id is required") Long requestedById) {
        this.requestedById = requestedById;
    }

    public @NotNull(message = "Deposit amount is required") BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(@NotNull(message = "Deposit amount is required") BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public @NotBlank(message = "Payment mode is required") String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(@NotBlank(message = "Payment mode is required") String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public @NotBlank(message = "Transaction id is required") String getTranxId() {
        return tranxId;
    }

    public void setTranxId(@NotBlank(message = "Transaction id is required") String tranxId) {
        this.tranxId = tranxId;
    }

    public @NotBlank(message = "Bank account name is required") String getBankAccountName() {
        return bankAccountName;
    }

    public void setBankAccountName(@NotBlank(message = "Bank account name is required") String bankAccountName) {
        this.bankAccountName = bankAccountName;
    }

    public @NotBlank(message = "Bank account number is required") String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(@NotBlank(message = "Bank account number is required") String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public @NotBlank(message = "Bank holder name is required") String getBankHolderName() {
        return bankHolderName;
    }

    public void setBankHolderName(@NotBlank(message = "Bank holder name is required") String bankHolderName) {
        this.bankHolderName = bankHolderName;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public MultipartFile getDepositImage() {
        return depositImage;
    }

    public void setDepositImage(MultipartFile depositImage) {
        this.depositImage = depositImage;
    }

    public @NotNull(message = "Deposite Date Is Requried") LocalDate getDepositeDate() {
        return depositeDate;
    }

    public void setDepositeDate(@NotNull(message = "Deposite Date Is Requried") LocalDate depositeDate) {
        this.depositeDate = depositeDate;
    }
}

