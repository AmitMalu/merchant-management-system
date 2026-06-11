package com.project2.ism.Model;

import com.project2.ism.Enum.RequestStatus;
import com.project2.ism.Enum.RequestedType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "prefund_wallet_request")
public class PrefundWalletRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔹 NEW: Who raised request
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_type", nullable = false)
    private RequestedType requestedType;

    // 🔹 NEW: Merchant ID or Franchise ID
    @Column(name = "requested_by_id", nullable = false)
    private Long requestedById;

    @Column(name = "deposit_amount")
    private BigDecimal depositAmount;

    @Column(name = "payment_mode", nullable = false)
    private String paymentMode;

    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_holder_name")
    private String bankHolderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus requestStatus = RequestStatus.PENDING;

    @Column(name = "tranx_id", length = 20, nullable = false)
    private String tranxId;

    @Column(name = "deposit_date", nullable = false)
    private LocalDate depositDate;

    @Lob
    @Column(name = "deposit_image")
    private String depositImage;

    @Column(name = "narration")
    private String narration;

    @Column(name = "approve_reject_date")
    private LocalDate approveRejectDate;

    @Column(name = "deposite_time")
    private LocalTime depositeTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RequestedType getRequestedType() {
        return requestedType;
    }

    public void setRequestedType(RequestedType requestedType) {
        this.requestedType = requestedType;
    }

    public Long getRequestedById() {
        return requestedById;
    }

    public void setRequestedById(Long requestedById) {
        this.requestedById = requestedById;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public String getBankAccountName() {
        return bankAccountName;
    }

    public void setBankAccountName(String bankAccountName) {
        this.bankAccountName = bankAccountName;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getBankHolderName() {
        return bankHolderName;
    }

    public void setBankHolderName(String bankHolderName) {
        this.bankHolderName = bankHolderName;
    }

    public RequestStatus getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(RequestStatus requestStatus) {
        this.requestStatus = requestStatus;
    }

    public String getTranxId() {
        return tranxId;
    }

    public void setTranxId(String tranxId) {
        this.tranxId = tranxId;
    }

    public LocalDate getDepositDate() {
        return depositDate;
    }

    public void setDepositDate(LocalDate depositDate) {
        this.depositDate = depositDate;
    }

    public String getDepositImage() {
        return depositImage;
    }

    public void setDepositImage(String depositImage) {
        this.depositImage = depositImage;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public LocalDate getApproveRejectDate() {
        return approveRejectDate;
    }

    public void setApproveRejectDate(LocalDate approveRejectDate) {
        this.approveRejectDate = approveRejectDate;
    }

    public LocalTime getDepositeTime() {
        return depositeTime;
    }

    public void setDepositeTime(LocalTime depositeTime) {
        this.depositeTime = depositeTime;
    }
}

