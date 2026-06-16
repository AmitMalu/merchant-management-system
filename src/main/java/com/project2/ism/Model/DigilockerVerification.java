package com.project2.ism.Model;


import com.project2.ism.Enum.DigilockerStatus;
import com.project2.ism.Enum.VerificationType;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "digilocker_verifications",
        indexes = {

                @Index(
                        name = "idx_digilocker_status",
                        columnList = "status"
                ),

                @Index(
                        name = "idx_digilocker_client_id",
                        columnList = "client_id"
                ),

                @Index(
                        name = "idx_digilocker_merchant",
                        columnList = "merchant_id"
                ),

                @Index(
                        name = "idx_digilocker_franchise",
                        columnList = "franchise_id"
                )

        }
)

public class DigilockerVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "franchise_id")
    private Franchise franchise;


    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 50)
    private VerificationType verificationType;


    @Column(
            name = "client_id",
            nullable = false,
            unique = true,
            length = 255
    )
    private String clientId;


    @Column(
            name = "digilocker_url",
            length = 2000
    )
    private String digilockerUrl;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private DigilockerStatus status = DigilockerStatus.PENDING;


    @Column(name = "aadhaar_linked")
    private Boolean aadhaarLinked = false;


    @Column(name = "pan_available")
    private Boolean panAvailable = false;


    @Column(name = "completed")
    private Boolean completed = false;


    @Column(name = "failure_reason", length = 500)
    private String failureReason;


    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;


    @CreationTimestamp
    @Column(
            name = "created_at",
            updatable = false
    )
    private LocalDateTime createdAt;


    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DigilockerVerification() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public Franchise getFranchise() {
        return franchise;
    }

    public void setFranchise(Franchise franchise) {
        this.franchise = franchise;
    }

    public VerificationType getVerificationType() {
        return verificationType;
    }

    public void setVerificationType(VerificationType verificationType) {
        this.verificationType = verificationType;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDigilockerUrl() {
        return digilockerUrl;
    }

    public void setDigilockerUrl(String digilockerUrl) {
        this.digilockerUrl = digilockerUrl;
    }

    public DigilockerStatus getStatus() {
        return status;
    }

    public void setStatus(DigilockerStatus status) {
        this.status = status;
    }

    public Boolean getAadhaarLinked() {
        return aadhaarLinked;
    }

    public void setAadhaarLinked(Boolean aadhaarLinked) {
        this.aadhaarLinked = aadhaarLinked;
    }

    public Boolean getPanAvailable() {
        return panAvailable;
    }

    public void setPanAvailable(Boolean panAvailable) {
        this.panAvailable = panAvailable;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
