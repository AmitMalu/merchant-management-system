package com.project2.ism.Service;

import com.project2.ism.Model.Taxes;
import com.project2.ism.Repository.TaxesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TaxesService {

    @Autowired
    private TaxesRepository taxesRepository;

    public Taxes getTaxes() {
        // Assuming only one record — fetch the first if exists, else create default
        return taxesRepository.findAll().stream().findFirst().orElseGet(() -> {
            Taxes t = new Taxes();
            t.setGst(BigDecimal.ZERO);
            t.setTds(BigDecimal.ZERO);
            return taxesRepository.save(t);
        });
    }

    public Taxes updateTaxes(Taxes updatedTaxes) {
        Taxes existing = getTaxes(); // ensures single record
        existing.setGst(updatedTaxes.getGst());
        existing.setTds(updatedTaxes.getTds());
        return taxesRepository.save(existing);
    }
}
