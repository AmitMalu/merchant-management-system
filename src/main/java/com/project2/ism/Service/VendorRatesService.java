package com.project2.ism.Service;

import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.Vendor.VendorCardRates;
import com.project2.ism.Model.Vendor.VendorRates;
import com.project2.ism.Repository.VendorRatesRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VendorRatesService {

    private final VendorRatesRepository vendorRatesRepository;

    public VendorRatesService(VendorRatesRepository vendorRatesRepository) {
        this.vendorRatesRepository = vendorRatesRepository;
    }

    // CREATE
    public VendorRates createVendorRates(@Valid VendorRates vendorRates) {
        return vendorRatesRepository.save(vendorRates);
    }

    // READ ALL
    public List<VendorRates> getAllVendorRates() {
        return vendorRatesRepository.findAll();
    }

    // READ BY ID
    public VendorRates getVendorRatesById(Long id) {
        return vendorRatesRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VendorRates",id));
    }

    // UPDATE
    public VendorRates updateVendorRates(Long id, @Valid VendorRates updatedRates) {
        VendorRates existingRates = getVendorRatesById(id);

        existingRates.setMonthlyRent(updatedRates.getMonthlyRent());
        existingRates.setEffectiveDate(updatedRates.getEffectiveDate());
        existingRates.setExpiryDate(updatedRates.getExpiryDate());
        existingRates.setVendor(updatedRates.getVendor());
        existingRates.setProduct(updatedRates.getProduct());
        existingRates.setRemark(updatedRates.getRemark());

        // Replace vendorCardRates
        existingRates.getVendorCardRates().clear();
        for (VendorCardRates cardRate : updatedRates.getVendorCardRates()) {
            existingRates.addVendorCardRate(cardRate); // helper method sets back-reference
        }

        return vendorRatesRepository.save(existingRates);
    }

//    public VendorRates updateVendorRates(Long id, @Valid VendorRates updatedRates) {
//        System.out.println("========== UPDATE SERVICE METHOD START ==========");
//
//        VendorRates existingRates = getVendorRatesById(id);
//        System.out.println("Loaded existing rates: " + existingRates.getId());
//        //System.out.println("Is managed? " + entityManager.contains(existingRates));
//
//        System.out.println("Old monthlyRent: " + existingRates.getMonthlyRent());
//        existingRates.setMonthlyRent(updatedRates.getMonthlyRent());
//        System.out.println("New monthlyRent: " + existingRates.getMonthlyRent());
//
//        existingRates.setEffectiveDate(updatedRates.getEffectiveDate());
//        existingRates.setExpiryDate(updatedRates.getExpiryDate());
//        existingRates.setPaymentVendor(updatedRates.getPaymentVendor());
//        existingRates.setPayoutProduct(updatedRates.getPayoutProduct());
//        existingRates.setRemark(updatedRates.getRemark());
//
//        // Replace vendorCardRates
//        existingRates.getVendorCardRates().clear();
//        for (VendorCardRates cardRate : updatedRates.getVendorCardRates()) {
//            existingRates.addVendorCardRate(cardRate);
//        }
//
//        System.out.println("About to save...");
//        VendorRates saved = vendorRatesRepository.save(existingRates);
//        System.out.println("Save completed");
//        System.out.println("========== UPDATE SERVICE METHOD END ==========");
//
//        return saved;
//    }

    // DELETE
    public void deleteVendorRates(Long id) {
        VendorRates existingRates = getVendorRatesById(id);
        vendorRatesRepository.delete(existingRates);
    }
    public VendorRates getRatesByProductId(Long productId) {
        return vendorRatesRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vendor rates not found for product ID: " + productId
                ));
    }
}