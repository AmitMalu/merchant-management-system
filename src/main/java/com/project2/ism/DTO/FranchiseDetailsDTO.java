package com.project2.ism.DTO;

import com.project2.ism.Model.Users.Franchise;

import java.util.List;

public class FranchiseDetailsDTO {
    private final FranchiseViewDTO franchise;
    private final List<MerchantListDTO> merchants;

    public FranchiseDetailsDTO(FranchiseViewDTO franchise, List<MerchantListDTO> merchants) {
        this.franchise = franchise;
        this.merchants = merchants;
    }

    public FranchiseViewDTO getFranchise() {
        return franchise;
    }

    public List<MerchantListDTO> getMerchants() {
        return merchants;
    }
}

