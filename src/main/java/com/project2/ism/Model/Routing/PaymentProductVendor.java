package com.project2.ism.Model.Routing;

import com.project2.ism.Model.Payment.PaymentProduct;
import com.project2.ism.Model.Payment.PaymentVendor;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Which vendors can serve which product (the routing form lists only these). For a product with several
 * bill categories, `categories` says which ones the vendor serves (comma separated; empty = all).
 */
@Entity
@Table(name = "payment_product_vendors",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_vendor", columnNames = {"product_id", "vendor_id"}))
public class PaymentProductVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private PaymentProduct product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private PaymentVendor vendor;

    @Column(name = "categories", length = 500)
    private String categories;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public PaymentProduct getProduct() { return product; }
    public void setProduct(PaymentProduct product) { this.product = product; }
    public PaymentVendor getVendor() { return vendor; }
    public void setVendor(PaymentVendor vendor) { this.vendor = vendor; }
    public String getCategories() { return categories; }
    public void setCategories(String categories) { this.categories = categories; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
