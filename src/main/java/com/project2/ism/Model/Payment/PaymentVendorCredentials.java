package com.project2.ism.Model.Payment;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_vendor_credentials")
public class PaymentVendorCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ----------------------------------------------------
    // RELATIONSHIPS
    // ----------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_vendor_id", nullable = false)
    private PaymentVendor paymentVendor;
    // REQUIRED. For payout vendor, set vendor.id = X

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_product_id", nullable = true)
    private PaymentProduct paymentProduct;
    // OPTIONAL. Payout is a product or sub-module, but can keep nullable.


    // ----------------------------------------------------
    // UAT / SANDBOX CREDS
    // ----------------------------------------------------

    @Column(name = "base_url_uat")
    private String baseUrlUat;

    @Column(name = "secret_key_uat")
    private String secretKeyUat; // secretKey from vendor

    @Column(name = "salt_key_uat")
    private String saltKeyUat;   // ivKey from vendor

    @Column(name = "encrypt_decrypt_key_uat")
    private String encryptDecryptKeyUat;

    @Column(name = "user_id_uat")
    private String userIdUat;


    // ----------------------------------------------------
    // PRODUCTION CREDS (future)
    // ----------------------------------------------------

    @Column(name = "base_url_prod")
    private String baseUrlProd;

    @Column(name = "secret_key_prod")
    private String secretKeyProd;

    @Column(name = "salt_key_prod")
    private String saltKeyProd;

    @Column(name = "encrypt_decrypt_key_prod")
    private String encryptDecryptKeyProd;

    @Column(name = "user_id_prod")
    private String userIdProd;


    // ----------------------------------------------------
    // CONTROL FIELDS
    // ----------------------------------------------------

    @Column(name = "active_environment")
    private String activeEnvironment;  // "UAT" or "PROD"

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_on", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_on")
    private LocalDateTime updatedAt;


    // ----------------------------------------------------
    // Getters and Setters
    // ----------------------------------------------------
    // (Manually generated)

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PaymentVendor getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(PaymentVendor paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public PaymentProduct getPaymentProduct() {
        return paymentProduct;
    }

    public void setPaymentProduct(PaymentProduct paymentProduct) {
        this.paymentProduct = paymentProduct;
    }

    public String getBaseUrlUat() {
        return baseUrlUat;
    }

    public void setBaseUrlUat(String baseUrlUat) {
        this.baseUrlUat = baseUrlUat;
    }

    public String getSecretKeyUat() {
        return secretKeyUat;
    }

    public void setSecretKeyUat(String secretKeyUat) {
        this.secretKeyUat = secretKeyUat;
    }

    public String getSaltKeyUat() {
        return saltKeyUat;
    }

    public void setSaltKeyUat(String saltKeyUat) {
        this.saltKeyUat = saltKeyUat;
    }

    public String getEncryptDecryptKeyUat() {
        return encryptDecryptKeyUat;
    }

    public void setEncryptDecryptKeyUat(String encryptDecryptKeyUat) {
        this.encryptDecryptKeyUat = encryptDecryptKeyUat;
    }

    public String getUserIdUat() {
        return userIdUat;
    }

    public void setUserIdUat(String userIdUat) {
        this.userIdUat = userIdUat;
    }

    public String getBaseUrlProd() {
        return baseUrlProd;
    }

    public void setBaseUrlProd(String baseUrlProd) {
        this.baseUrlProd = baseUrlProd;
    }

    public String getSecretKeyProd() {
        return secretKeyProd;
    }

    public void setSecretKeyProd(String secretKeyProd) {
        this.secretKeyProd = secretKeyProd;
    }

    public String getSaltKeyProd() {
        return saltKeyProd;
    }

    public void setSaltKeyProd(String saltKeyProd) {
        this.saltKeyProd = saltKeyProd;
    }

    public String getEncryptDecryptKeyProd() {
        return encryptDecryptKeyProd;
    }

    public void setEncryptDecryptKeyProd(String encryptDecryptKeyProd) {
        this.encryptDecryptKeyProd = encryptDecryptKeyProd;
    }

    public String getUserIdProd() {
        return userIdProd;
    }

    public void setUserIdProd(String userIdProd) {
        this.userIdProd = userIdProd;
    }

    public String getActiveEnvironment() {
        return activeEnvironment;
    }

    public void setActiveEnvironment(String activeEnvironment) {
        this.activeEnvironment = activeEnvironment;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
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
