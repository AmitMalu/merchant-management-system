package com.project2.ism.Model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class Taxes {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private BigDecimal gst;

    private BigDecimal tds;

    public Long getId() {   
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BigDecimal getGst() {
        return gst;
    }

    public void setGst(BigDecimal gst) {
        this.gst = gst;
    }

    public BigDecimal getTds() {
        return tds;
    }

    public void setTds(BigDecimal tds) {
        this.tds = tds;
    }
}
