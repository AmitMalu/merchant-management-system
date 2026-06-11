    package com.project2.ism.Service;

    import com.project2.ism.DTO.PricingSchemesDTOS.PricingSchemeWarningDTO;
    import com.project2.ism.DTO.PricingSchemesDTOS.PricingSchemesResponseDTO;
    import com.project2.ism.Exception.ResourceNotFoundException;
    import com.project2.ism.Model.PricingScheme.PricingScheme;
    import com.project2.ism.Model.ProductCategory;
    import com.project2.ism.Model.Vendor.VendorRates;
    import com.project2.ism.Model.Vendor.VendorCardRates;
    import com.project2.ism.Model.PricingScheme.CardRate;
    import com.project2.ism.Repository.PricingSchemeRepository;
    import com.project2.ism.Repository.ProductCategoryRepository;
    import com.project2.ism.Repository.ProductRepository;
    import org.springframework.data.domain.Page;
    import org.springframework.data.domain.Pageable;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    import java.math.BigDecimal;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Map;
    import java.util.Optional;
    import java.util.stream.Collectors;

    @Service
    @Transactional
    public class PricingSchemeService {

        private final PricingSchemeRepository pricingSchemeRepository;

        private final ProductRepository productRepository;

        private final ProductCategoryRepository productCategoryRepository;

        private final VendorRatesService vendorRatesService;

        public PricingSchemeService(PricingSchemeRepository pricingSchemeRepository, ProductRepository productRepository, ProductCategoryRepository productCategoryRepository, VendorRatesService vendorRatesService) {
            this.pricingSchemeRepository = pricingSchemeRepository;
            this.productRepository = productRepository;
            this.productCategoryRepository = productCategoryRepository;
            this.vendorRatesService = vendorRatesService;
        }

        public PricingScheme createPricingScheme(PricingScheme pricingScheme) {
            // Check for duplicate scheme
//            if (pricingSchemeRepository.existsDuplicateSchemeForNew(
//                    pricingScheme.getSchemeCode(),
//                    pricingScheme.getRentalByMonth(),
//                    pricingScheme.getCustomerType())) {
//                throw new RuntimeException("Pricing scheme with same code, rental amount and customer type already exists");
//            }
            String code = generateNextSchemeCode();
            pricingScheme.setSchemeCode(code);

            // Set bidirectional relationship for card rates
            if (pricingScheme.getCardRates() != null) {
                pricingScheme.getCardRates().forEach(cardRate -> cardRate.setPricingScheme(pricingScheme));
            }

            return pricingSchemeRepository.save(pricingScheme);
        }

        @Transactional(readOnly = true)
        public Page<PricingScheme> getAllPricingSchemes(Pageable pageable) {
            return pricingSchemeRepository.findAll(pageable);
        }

        @Transactional(readOnly = true)
        public PricingScheme getPricingSchemeById(Long id) {
            return pricingSchemeRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Pricing scheme not found with id: " + id));
        }

        @Transactional(readOnly = true)
        public PricingScheme getPricingSchemeByCode(String schemeCode) {
            return pricingSchemeRepository.findBySchemeCode(schemeCode)
                    .orElseThrow(() -> new RuntimeException("Pricing scheme not found with code: " + schemeCode));
        }

        public PricingScheme updatePricingScheme(Long id, PricingScheme pricingSchemeDetails) {
            PricingScheme existingScheme = getPricingSchemeById(id);

            // Check for duplicate scheme (excluding current scheme)
            if (pricingSchemeRepository.existsDuplicateScheme(
                    pricingSchemeDetails.getSchemeCode(),
                    pricingSchemeDetails.getRentalByMonth(),
                    pricingSchemeDetails.getCustomerType(),
                    id)) {
                throw new RuntimeException("Pricing scheme with same code, rental amount and customer type already exists");
            }

            // Update basic fields
            existingScheme.setSchemeCode(pricingSchemeDetails.getSchemeCode());
            existingScheme.setRentalByMonth(pricingSchemeDetails.getRentalByMonth());
            existingScheme.setCustomerType(pricingSchemeDetails.getCustomerType());
            existingScheme.setDescription(pricingSchemeDetails.getDescription());

            // Clear existing card rates and add new ones
            existingScheme.getCardRates().clear();
            if (pricingSchemeDetails.getCardRates() != null) {
                pricingSchemeDetails.getCardRates().forEach(cardRate -> {
                    cardRate.setPricingScheme(existingScheme);
                    existingScheme.getCardRates().add(cardRate);
                });
            }

            return pricingSchemeRepository.save(existingScheme);
        }

        public void deletePricingScheme(Long id) {
            PricingScheme pricingScheme = getPricingSchemeById(id);
            pricingSchemeRepository.delete(pricingScheme);
        }

        @Transactional(readOnly = true)
        public Page<PricingScheme> searchPricingSchemes(String query, Pageable pageable) {
            if (query == null || query.trim().isEmpty()) {
                return getAllPricingSchemes(pageable);
            }
            return pricingSchemeRepository.searchSchemes(query.trim(), pageable);
        }

        @Transactional(readOnly = true)
        public boolean schemeCodeExists(String schemeCode) {
            return pricingSchemeRepository.existsBySchemeCode(schemeCode);
        }

        @Transactional(readOnly = true)
        public long getTotalSchemesCount() {
            return pricingSchemeRepository.count();
        }

        // Service method
        @Transactional(readOnly = true)
        public Map<String, Long> getSchemeCountsByCustomerType() {
            return pricingSchemeRepository.countByCustomerType().stream()
                    .collect(Collectors.toMap(
                            result -> (String) result[0],
                            result -> (Long) result[1]
                    ));
        }


        @Transactional(readOnly = true)
        public String generateNextSchemeCode() {
            // Get the latest scheme code
            Optional<String> latestCode = pricingSchemeRepository.findTopByOrderBySchemeCodeDesc()
                    .map(PricingScheme::getSchemeCode);

            if (latestCode.isPresent()) {
                String code = latestCode.get();
                // Extract number from scheme code (assuming format like SCHEME_001, SCH_123, etc.)
                String[] parts = code.split("_");
                if (parts.length >= 2) {
                    try {
                        int lastNumber = Integer.parseInt(parts[parts.length - 1]);
                        return parts[0] + "_" + String.format("%03d", lastNumber + 1);
                    } catch (NumberFormatException e) {
                        // If parsing fails, generate default
                        return "SCHEME_001";
                    }
                }
            }

            // If no existing scheme or parsing failed, return default
            return "SCHEME_001";
        }

        public PricingSchemesResponseDTO getValidPricingScheme(Long productId, String productCategory, String customerType) {
            // ✅ Step 1: validate product + category
            if (!productRepository.existsById(productId)) {
                throw new ResourceNotFoundException("Product not found with id " + productId);
            }
            ProductCategory category = productCategoryRepository.findByCategoryName(productCategory)
                    .orElseThrow(() -> new ResourceNotFoundException("Product category not found: " + productCategory));

            Long productCategoryId = category.getId();

            // ✅ Step 2: get vendor rates for product (optional - may not exist)
            VendorRates vendorRates = null;
            String globalWarning = null;

            try {
                vendorRates = vendorRatesService.getRatesByProductId(productId);
            } catch (Exception e) {
                // Vendor rates don't exist - set global warning
                globalWarning = "No vendor rates configured for this product. Unable to validate pricing schemes against vendor costs.";
            }

            // ✅ Step 3: fetch all pricing schemes for that category + customer type
            List<PricingScheme> schemes = pricingSchemeRepository
                    .findByProductCategory_IdAndCustomerType(productCategoryId, customerType);

            List<PricingSchemeWarningDTO> schemeWarnings = new ArrayList<>();

            // ✅ Step 4: Process each scheme and check against vendor rates
            for (PricingScheme scheme : schemes) {
                String warning = null;

                // Only validate if vendor rates exist
                if (vendorRates != null) {
                    List<String> violations = new ArrayList<>();

                    // Check monthly rent
                    if (scheme.getRentalByMonth() < vendorRates.getMonthlyRent().doubleValue()) {
                        violations.add(String.format("Monthly rent (%.2f) is below vendor rate (%.2f)",
                                scheme.getRentalByMonth(),
                                vendorRates.getMonthlyRent()));
                    }

                    // Map vendor card rates by card type for quick lookup
                    Map<String, BigDecimal> vendorCardRateMap = vendorRates.getVendorCardRates()
                            .stream()
                            .collect(Collectors.toMap(VendorCardRates::getCardType, VendorCardRates::getRate));

                    // Check card rates
                    for (CardRate cardRate : scheme.getCardRates()) {
                        BigDecimal vendorRate = vendorCardRateMap.get(cardRate.getCardName());
                        if (vendorRate != null) {
                            double effectiveRate = cardRate.getRate() != null ? cardRate.getRate() :
                                    (customerType.equalsIgnoreCase("FRANCHISE") ? cardRate.getFranchiseRate() : cardRate.getMerchantRate());

                            if (effectiveRate < vendorRate.doubleValue()) {
                                violations.add(String.format("%s rate (%.2f%%) is below vendor rate (%.2f%%)",
                                        cardRate.getCardName(),
                                        effectiveRate,
                                        vendorRate));
                            }
                        }
                    }

                    // Build warning message if violations exist
                    if (!violations.isEmpty()) {
                        warning = "Scheme rates below vendor costs: " + String.join("; ", violations);
                    }
                }

                // Add scheme to response list
                schemeWarnings.add(new PricingSchemeWarningDTO(
                        scheme.getId(),
                        scheme.getSchemeCode(),
                        scheme.getRentalByMonth(),
                        warning
                ));
            }

            return new PricingSchemesResponseDTO(schemeWarnings, globalWarning);
        }

    }
