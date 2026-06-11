package com.project2.ism.Model;

import com.project2.ism.Model.Users.Franchise;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "franchise_transaction_details")
public class FranchiseTransactionDetails extends TransactionDetailsBase{

    @NotNull
    @ManyToOne
    @JoinColumn(name = "franchise_id",nullable = false)
    private Franchise franchise;


    @OneToOne
    @JoinColumn(name = "merchant_transaction_id", unique = true, nullable = true)
    private MerchantTransactionDetails merchantTransactionDetail;

    public FranchiseTransactionDetails() {
    }

    public Franchise getFranchise() {
        return franchise;
    }

    public void setFranchise(Franchise franchise) {
        this.franchise = franchise;
    }

    public MerchantTransactionDetails getMerchantTransactionDetail() {
        return merchantTransactionDetail;
    }

    public void setMerchantTransactionDetail(MerchantTransactionDetails merchantTransactionDetail) {
        this.merchantTransactionDetail = merchantTransactionDetail;
    }
}
