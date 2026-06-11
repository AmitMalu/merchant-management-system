package com.project2.ism.Model.Payment;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_modes")
public class PaymentMode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;       // IMPS, NEFT, RTGS, VISA_CREDIT, BBPS, etc.

    @Column(nullable = false)
    private String description;  // Human readable name

    @Column(nullable = false)
    private Boolean status = true;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }
}

