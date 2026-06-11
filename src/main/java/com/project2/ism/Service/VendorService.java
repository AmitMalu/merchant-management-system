package com.project2.ism.Service;

import com.project2.ism.DTO.Vendor.VendorIDNameDTO;
import com.project2.ism.DTO.Vendor.VendorStatsDTO;
import com.project2.ism.Exception.DuplicateResourceException;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Vendor.Vendor;
import com.project2.ism.Repository.ProductRepository;
import com.project2.ism.Repository.VendorRatesRepository;
import com.project2.ism.Repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class VendorService {


    private final VendorRepository vendorRepository;

    private final VendorRatesRepository vendorRatesRepository;

    private final ProductRepository productRepository;

    public VendorService(VendorRepository vendorRepository, VendorRatesRepository vendorRatesRepository, ProductRepository productRepository) {
        this.vendorRepository = vendorRepository;
        this.vendorRatesRepository = vendorRatesRepository;
        this.productRepository = productRepository;
    }

    // Create or Save Vendor
    public Vendor createVendor(@Valid Vendor vendor) {
        if (vendorRepository.existsByNameIgnoreCase(vendor.getName())) {
            throw new DuplicateResourceException("Vendor name already exists: " + vendor.getName());
        }        return vendorRepository.save(vendor);
    }

    // Get all vendors
    public List<Vendor> getAllVendors() {
        return vendorRepository.findAll();
    }

    // Get vendor by ID with error handling
    public Vendor getVendorById(Long id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found with ID: " + id));
    }

    // Update vendor
    public Vendor updateVendor(Long id, @Valid Vendor updatedVendor) {
        Vendor existingVendor = vendorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cannot update. Vendor not found with ID: " + id));

        if (vendorRepository.existsByNameIgnoreCase(updatedVendor.getName()) &&
                !existingVendor.getName().equalsIgnoreCase(updatedVendor.getName())) {
            throw new DuplicateResourceException("Vendor name already exists: " + updatedVendor.getName());
        }

        updatedVendor.setId(existingVendor.getId());// ensure ID consistency
        if(updatedVendor.getStatus()==false){
            List<Product> vendorProducts = productRepository.findByVendorId(updatedVendor.getId());
            if (vendorProducts != null) {
                for (Product p : vendorProducts) {
                    p.setStatus(false);
                }
                productRepository.saveAll(vendorProducts);
            }
        }
        else{
            List<Product> vendorProducts = productRepository.findByVendorId(updatedVendor.getId());
            if (vendorProducts != null) {
                for (Product p : vendorProducts) {
                    p.setStatus(true);
                }
                productRepository.saveAll(vendorProducts);
            }
        }
        return vendorRepository.save(updatedVendor);
    }

    // Delete vendor
    public void deleteVendor(Long id) {
        if (!vendorRepository.existsById(id)) {
            throw new EntityNotFoundException("Cannot delete. Vendor not found with ID: " + id);
        }
        vendorRepository.deleteById(id);
    }

    public List<VendorIDNameDTO> getAllVendorsIdAndName() {
        return vendorRepository.findByStatusTrue()
                .stream()
                .map(v -> new VendorIDNameDTO(v.getId(), v.getName()))
                .toList();
    }

    public VendorStatsDTO getVendorStats() {
        VendorStatsDTO dto = new VendorStatsDTO();

        // Vendors
        dto.totalVendors = vendorRepository.count();
        dto.activeVendors = vendorRepository.countByStatus(true);
        dto.inactiveVendors = vendorRepository.countByStatus(false);

        // Vendor Rates
        dto.totalVendorRates = vendorRatesRepository.count();
        LocalDate today = LocalDate.now();
        dto.activeVendorRates = vendorRatesRepository.countByEffectiveDateBeforeAndExpiryDateAfter(today, today);

        // Total Monthly Rent
        dto.totalMonthlyRent = vendorRatesRepository.sumActiveMonthlyRent(today, today);

        // Card Type Distribution
        dto.cardTypeDistribution = vendorRatesRepository.countGroupByCardType();

        return dto;
    }

    // Get all vendors
    public List<Vendor> getAllActiveVendors() {
        return vendorRepository.findByStatusTrue();
    }

}
