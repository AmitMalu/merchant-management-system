package com.project2.ism.Model.Bbps;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "biller_code_list",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_biller_code_vendor_category_code",
                        columnNames = {"vendor_id", "biller_category_id", "code"}
                )
        })
public class BillerCodeList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "biller_category_id", nullable = false)
    private BillerCategoryList billerCategory;

    @Column(name = "code")
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public BillerCategoryList getBillerCategory() {
        return billerCategory;
    }

    public void setBillerCategory(BillerCategoryList billerCategory) {
        this.billerCategory = billerCategory;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
