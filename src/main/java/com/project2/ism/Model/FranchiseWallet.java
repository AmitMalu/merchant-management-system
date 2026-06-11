package com.project2.ism.Model;

import com.project2.ism.Model.Users.Franchise;
import jakarta.persistence.*;

@Entity
public class FranchiseWallet extends WalletBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "franchise_id",nullable = false)
    private Franchise franchise;

    public FranchiseWallet() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Franchise getFranchise() {
        return franchise;
    }

    public void setFranchise(Franchise franchise) {
        this.franchise = franchise;
    }

}
