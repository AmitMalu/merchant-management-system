    package com.project2.ism.Service;

    import com.project2.ism.DTO.PricingSchemesDTOS.PricingSchemeWarningDTO;
    import com.project2.ism.DTO.PricingSchemesDTOS.PricingSchemesResponseDTO;
    import com.project2.ism.Exception.ResourceNotFoundException;
    import com.project2.ism.Model.PricingScheme.PricingScheme;
    import com.project2.ism.Model.ProductCategory;
    import com.project2.ism.Model.Vendor.VendorRates;
    import com.project2.ism.Model.Vendor.VendorCardRates;
    import com.project2.ism.Model.PricingScheme.CardRate;
    import com.project2.ism.Repository.CardRateRepository;
    import com.project2.ism.Repository.PricingSchemeRepository;
    import com.project2.ism.Repository.ProductCategoryRepository;
    import com.project2.ism.Repository.ProductRepository;
    import jakarta.persistence.EntityManager;
    import jakarta.persistence.PersistenceContext;
    import org.springframework.data.domain.Page;
    import org.springframework.data.domain.Pageable;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    import java.math.BigDecimal;
    import java.util.*;
    import java.util.stream.Collectors;

    @Service
    @Transactional
    public class PricingSchemeService {

        private final PricingSchemeRepository pricingSchemeRepository;

        private final ProductRepository productRepository;

        private final ProductCategoryRepository productCategoryRepository;

        private final VendorRatesService vendorRatesService;

        private final CardRateRepository cardRateRepository;

        @PersistenceContext
        private EntityManager entityManager;

        public PricingSchemeService(PricingSchemeRepository pricingSchemeRepository, ProductRepository productRepository, ProductCategoryRepository productCategoryRepository,
                                    VendorRatesService vendorRatesService, CardRateRepository cardRateRepository) {
            this.pricingSchemeRepository = pricingSchemeRepository;
            this.productRepository = productRepository;
            this.productCategoryRepository = productCategoryRepository;
            this.vendorRatesService = vendorRatesService;
            this.cardRateRepository = cardRateRepository;
        }

        @Transactional
        public PricingScheme createPricingScheme(PricingScheme pricingScheme) {

            String code = generateNextSchemeCode();
            pricingScheme.setSchemeCode(code);

            if (pricingScheme.getCardRates() == null
                    || pricingScheme.getCardRates().isEmpty()) {

                throw new RuntimeException(
                        "Please add at least one product card rate"
                );
            }

            Set<String> combinations = new HashSet<>();

            for (CardRate cardRate : pricingScheme.getCardRates()) {

                if (cardRate.getProductCategory() == null
                        || cardRate.getProductCategory().getId() == null) {

                    throw new RuntimeException(
                            "Product category is required for card: "
                                    + cardRate.getCardName()
                    );
                }

                if (cardRate.getCardName() == null
                        || cardRate.getCardName().trim().isEmpty()) {

                    throw new RuntimeException("Card name is required");
                }

                Long productCategoryId =
                        cardRate.getProductCategory().getId();

                ProductCategory productCategory =
                        productCategoryRepository
                                .findById(productCategoryId)
                                .orElseThrow(() ->
                                        new RuntimeException(
                                                "Product category not found with id: "
                                                        + productCategoryId
                                        )
                                );

                String combinationKey =
                        productCategoryId
                                + "_"
                                + cardRate.getCardName()
                                .trim()
                                .toUpperCase();

                if (!combinations.add(combinationKey)) {
                    throw new RuntimeException(
                            "Duplicate card rate found for product "
                                    + productCategory.getCategoryName()
                                    + " and card "
                                    + cardRate.getCardName()
                    );
                }

                cardRate.setId(null);
                cardRate.setCardName(cardRate.getCardName().trim());
                cardRate.setProductCategory(productCategory);
                cardRate.setPricingScheme(pricingScheme);
            }

            return pricingSchemeRepository.save(pricingScheme);
        }

        @Transactional(readOnly = true)
        public Page<PricingScheme> getAllPricingSchemes(Pageable pageable) {

            return pricingSchemeRepository
                    .findAllWithCardRatesAndProductCategory(pageable);
        }

        @Transactional(readOnly = true)
        public PricingScheme getPricingSchemeById(Long id) {

            return pricingSchemeRepository
                    .findByIdWithCardRatesAndProductCategory(id)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Pricing scheme not found with id: " + id
                            )
                    );
        }

        @Transactional(readOnly = true)
        public PricingScheme getPricingSchemeByCode(String schemeCode) {
            return pricingSchemeRepository.findBySchemeCode(schemeCode)
                    .orElseThrow(() -> new RuntimeException("Pricing scheme not found with code: " + schemeCode));
        }

        @Transactional
        public PricingScheme updatePricingScheme(
                Long id,
                PricingScheme pricingSchemeDetails
        ) {

            PricingScheme existingScheme = getPricingSchemeById(id);

            if (pricingSchemeRepository.existsDuplicateScheme(
                    pricingSchemeDetails.getSchemeCode(),
                    pricingSchemeDetails.getRentalByMonth(),
                    pricingSchemeDetails.getCustomerType(),
                    id
            )) {
                throw new RuntimeException(
                        "Pricing scheme with same code, rental amount and customer type already exists"
                );
            }

            existingScheme.setSchemeCode(pricingSchemeDetails.getSchemeCode());
            existingScheme.setRentalByMonth(pricingSchemeDetails.getRentalByMonth());
            existingScheme.setCustomerType(pricingSchemeDetails.getCustomerType());
            existingScheme.setDescription(pricingSchemeDetails.getDescription());
            existingScheme.setGst(pricingSchemeDetails.getGst());

            List<CardRate> requestedCardRates = pricingSchemeDetails.getCardRates();

            if (requestedCardRates == null || requestedCardRates.isEmpty()) {
                throw new RuntimeException("Please add at least one product card rate");
            }

            Set<String> uniqueCombinations = new HashSet<>();
            List<CardRate> newCardRates = new ArrayList<>();

            for (CardRate requestedCardRate : requestedCardRates) {

                if (requestedCardRate.getProductCategory() == null
                        || requestedCardRate.getProductCategory().getId() == null) {
                    throw new RuntimeException(
                            "Product category is required for card: "
                                    + requestedCardRate.getCardName()
                    );
                }

                if (requestedCardRate.getCardName() == null
                        || requestedCardRate.getCardName().trim().isEmpty()) {
                    throw new RuntimeException("Card name is required");
                }

                Long productCategoryId = requestedCardRate.getProductCategory().getId();

                ProductCategory productCategory =
                        productCategoryRepository.findById(productCategoryId)
                                .orElseThrow(() ->
                                        new RuntimeException(
                                                "Product category not found with id: "
                                                        + productCategoryId
                                        )
                                );

                String normalizedCardName =
                        requestedCardRate.getCardName().trim().toUpperCase();

                String key = productCategoryId + "_" + normalizedCardName;

                if (!uniqueCombinations.add(key)) {
                    throw new RuntimeException(
                            "Duplicate card rate found for product "
                                    + productCategory.getCategoryName()
                                    + " and card "
                                    + requestedCardRate.getCardName()
                    );
                }

                CardRate newCardRate = new CardRate();

                newCardRate.setCardName(requestedCardRate.getCardName().trim());
                newCardRate.setCategory(requestedCardRate.getCategory());

                // Remove this line if your CardRate entity no longer has 'rate'
                newCardRate.setRate(requestedCardRate.getRate());

                newCardRate.setFranchiseRate(requestedCardRate.getFranchiseRate());
                newCardRate.setMerchantRate(requestedCardRate.getMerchantRate());

                newCardRate.setProductCategory(productCategory);
                newCardRate.setPricingScheme(existingScheme);

                newCardRates.add(newCardRate);
            }

            // Remove existing card rates
            existingScheme.getCardRates().clear();
            pricingSchemeRepository.save(existingScheme);

            // Execute DELETE immediately
            entityManager.flush();

            // Add new card rates
            existingScheme.getCardRates().addAll(newCardRates);

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

        @Transactional(readOnly = true)
        public PricingSchemesResponseDTO getValidPricingScheme(
                Long productId,
                String productCategory,
                String customerType
        ) {

            // Step 1: Validate request values
            if (productId == null) {
                throw new IllegalArgumentException("Product id is required");
            }

            if (productCategory == null || productCategory.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Product category is required"
                );
            }

            if (customerType == null || customerType.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Customer type is required"
                );
            }

            String normalizedProductCategory =
                    productCategory.trim();

            String normalizedCustomerType =
                    customerType.trim();

            // Step 2: Validate product
            if (!productRepository.existsById(productId)) {
                throw new ResourceNotFoundException(
                        "Product not found with id " + productId
                );
            }

            // Step 3: Find product category
            ProductCategory category =
                    productCategoryRepository
                            .findByCategoryName(normalizedProductCategory)
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "Product category not found: "
                                                    + normalizedProductCategory
                                    )
                            );

            Long productCategoryId = category.getId();

            // Step 4: Get vendor rates
            VendorRates vendorRates = null;
            String globalWarning = null;

            try {
                vendorRates =
                        vendorRatesService.getRatesByProductId(productId);
            } catch (Exception e) {
                globalWarning =
                        "No vendor rates configured for this product. "
                                + "Unable to validate pricing schemes "
                                + "against vendor costs.";
            }

            // Step 5: Find schemes containing this product category
            List<PricingScheme> schemes =
                    pricingSchemeRepository
                            .findValidSchemesByProductCategoryAndCustomerType(
                                    productCategoryId,
                                    normalizedCustomerType
                            );

            List<PricingSchemeWarningDTO> schemeWarnings =
                    new ArrayList<>();

            // Step 6: Process every matching scheme
            for (PricingScheme scheme : schemes) {

                String warning = null;

                if (vendorRates != null) {

                    List<String> violations = new ArrayList<>();

                    // Validate monthly rental
                    if (vendorRates.getMonthlyRent() != null
                            && scheme.getRentalByMonth() != null
                            && scheme.getRentalByMonth()
                            < vendorRates.getMonthlyRent().doubleValue()) {

                        violations.add(
                                String.format(
                                        "Monthly rent (%.2f) is below vendor rate (%.2f)",
                                        scheme.getRentalByMonth(),
                                        vendorRates.getMonthlyRent()
                                )
                        );
                    }

                    // Vendor card rates mapped by normalized card name
                    Map<String, BigDecimal> vendorCardRateMap =
                            vendorRates.getVendorCardRates() == null
                                    ? Collections.emptyMap()
                                    : vendorRates.getVendorCardRates()
                                    .stream()
                                    .filter(rate ->
                                            rate.getCardType() != null
                                                    && rate.getRate() != null
                                    )
                                    .collect(
                                            Collectors.toMap(
                                                    rate -> rate.getCardType()
                                                            .trim()
                                                            .toUpperCase(),
                                                    VendorCardRates::getRate,
                                                    (first, second) -> first
                                            )
                                    );

                    /*
                     * Important:
                     * A scheme may contain AXIS POS, HDFC POS, ICICI POS.
                     *
                     * Here we validate only card rates belonging to the
                     * requested product category.
                     */
                    List<CardRate> selectedProductCardRates =
                            scheme.getCardRates()
                                    .stream()
                                    .filter(cardRate ->
                                            cardRate.getProductCategory() != null
                                                    && cardRate
                                                    .getProductCategory()
                                                    .getId() != null
                                                    && cardRate
                                                    .getProductCategory()
                                                    .getId()
                                                    .equals(productCategoryId)
                                    )
                                    .toList();

                    for (CardRate cardRate : selectedProductCardRates) {

                        if (cardRate.getCardName() == null) {
                            continue;
                        }

                        String normalizedCardName =
                                cardRate.getCardName()
                                        .trim()
                                        .toUpperCase();

                        BigDecimal vendorRate =
                                vendorCardRateMap.get(normalizedCardName);

                        if (vendorRate == null) {
                            continue;
                        }

                        Double effectiveRate =
                                resolveEffectiveRate(
                                        cardRate,
                                        normalizedCustomerType
                                );

                        if (effectiveRate == null) {
                            violations.add(
                                    String.format(
                                            "%s rate is not configured for product %s",
                                            cardRate.getCardName(),
                                            category.getCategoryName()
                                    )
                            );

                            continue;
                        }

                        if (effectiveRate < vendorRate.doubleValue()) {
                            violations.add(
                                    String.format(
                                            "%s rate (%.2f%%) is below vendor rate (%.2f%%)",
                                            cardRate.getCardName(),
                                            effectiveRate,
                                            vendorRate
                                    )
                            );
                        }
                    }

                    if (!violations.isEmpty()) {
                        warning =
                                "Scheme rates below vendor costs for "
                                        + category.getCategoryName()
                                        + ": "
                                        + String.join("; ", violations);
                    }
                }

                schemeWarnings.add(
                        new PricingSchemeWarningDTO(
                                scheme.getId(),
                                scheme.getSchemeCode(),
                                scheme.getRentalByMonth(),
                                warning
                        )
                );
            }

            return new PricingSchemesResponseDTO(
                    schemeWarnings,
                    globalWarning
            );
        }

        private Double resolveEffectiveRate(
                CardRate cardRate,
                String customerType
        ) {

            /*
             * Direct merchant rate has first priority when the generic
             * rate field is configured.
             */
            if (cardRate.getRate() != null) {
                return cardRate.getRate();
            }

            if ("FRANCHISE".equalsIgnoreCase(customerType)) {
                return cardRate.getFranchiseRate();
            }

            if ("MERCHANT".equalsIgnoreCase(customerType)) {
                return cardRate.getMerchantRate();
            }

            throw new IllegalArgumentException(
                    "Unsupported customer type: " + customerType
            );
        }

    }
