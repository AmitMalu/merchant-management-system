package com.project2.ism.Model;

import com.project2.ism.Model.Vendor.Vendor;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "upload_records")
public class UploadRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "vendor_id",nullable = false)
    private Vendor vendor;
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    private String fileName;
    private LocalDateTime uploadedAt = LocalDateTime.now();

    public UploadRecord() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}