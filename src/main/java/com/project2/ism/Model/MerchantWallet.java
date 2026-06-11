package com.project2.ism.Model;

import com.project2.ism.Model.Users.Merchant;
import jakarta.persistence.*;

@Entity
public class MerchantWallet extends WalletBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "merchant_id",nullable = false)
    private Merchant merchant;

    public MerchantWallet() {
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
