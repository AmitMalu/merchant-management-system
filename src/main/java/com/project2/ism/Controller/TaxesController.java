package com.project2.ism.Controller;

import com.project2.ism.Model.Taxes;
import com.project2.ism.Service.TaxesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/taxes")
public class TaxesController {

    @Autowired
    private TaxesService taxesService;

    @GetMapping
    public Taxes getTaxes() {
        return taxesService.getTaxes();
    }

    @PutMapping
    public Taxes updateTaxes(@RequestBody Taxes taxes) {
        return taxesService.updateTaxes(taxes);
    }

}
