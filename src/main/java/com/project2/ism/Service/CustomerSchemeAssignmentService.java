package com.project2.ism.Service;

import com.project2.ism.DTO.CustomerSchemeAssignmentDTO;
import com.project2.ism.DTO.ProductSchemeReportDTO;
import com.project2.ism.DTO.SchemeGroupedResponseDTO;
import com.project2.ism.Exception.DuplicateResourceException;
import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.CustomerSchemeAssignment;
import com.project2.ism.Model.PricingScheme.PricingScheme;
import com.project2.ism.Model.Product;
import com.project2.ism.Model.Users.Franchise;
import com.project2.ism.Model.Users.Merchant;
import com.project2.ism.DTO.CardRateDTO;
import com.project2.ism.Model.PricingScheme.CardRate;
import com.project2.ism.Repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomerSchemeAssignmentService {

    private final CustomerSchemeAssignmentRepository assignmentRepo;
    private final PricingSchemeRepository schemeRepo;
    private final ProductRepository productRepository;
    private final FranchiseRepository franchiseRepository;
    private final MerchantRepository merchantRepository;
    private final PricingSchemeService pricingSchemeService;

    public CustomerSchemeAssignmentService(
            CustomerSchemeAssignmentRepository assignmentRepo,
            PricingSchemeRepository schemeRepo,
            ProductRepository productRepository,
            FranchiseRepository franchiseRepository,
            MerchantRepository merchantRepository,
            PricingSchemeService pricingSchemeService) {
        this.assignmentRepo = assignmentRepo;
        this.schemeRepo = schemeRepo;
        this.productRepository = productRepository;
        this.franchiseRepository = franchiseRepository;
        this.merchantRepository = merchantRepository;
        this.pricingSchemeService = pricingSchemeService;
    }

    public List<CustomerSchemeAssignmentDTO> createAssignments(
            List<CustomerSchemeAssignmentDTO> dtos) {

        List<CustomerSchemeAssignmentDTO> result = new ArrayList<>();

        for (CustomerSchemeAssignmentDTO dto : dtos) {
            result.add(createAssignment(dto));
        }

        return result;
    }


    public CustomerSchemeAssignmentDTO createAssignment(CustomerSchemeAssignmentDTO dto) {

        String customerType = dto.getCustomerType();

        PricingScheme scheme = schemeRepo.findById(dto.getSchemeId())
                .orElseThrow(() -> new ResourceNotFoundException("Scheme not found"));

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Merchant merchant = merchantRepository.findById(dto.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        CustomerSchemeAssignment entity = new CustomerSchemeAssignment();
        entity.setScheme(scheme);
        entity.setProduct(product);
        entity.setCustomerType(dto.getCustomerType());
        entity.setEffectiveDate(dto.getEffectiveDate());
        entity.setExpiryDate(dto.getExpiryDate());
        entity.setRemarks(dto.getRemarks());

        // Set either franchise or merchant based on customer type
        if ("FRANCHISE".equalsIgnoreCase(dto.getCustomerType()) && dto.getFranchiseId() != null) {

            // 🔍 Check for overlaps before assigning
            List<CustomerSchemeAssignment> overlaps = assignmentRepo.findOverlappingAssignments(
                    dto.getMerchantId(),
                    customerType.toUpperCase(),
                    product.getId(),
                    dto.getEffectiveDate(),
                    dto.getExpiryDate()
            );

            if (!overlaps.isEmpty()) {
                throw new DuplicateResourceException("Customer already has a scheme assigned for this product during the given period.");
            }

            Franchise franchise = franchiseRepository.findById(dto.getFranchiseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Franchise not found"));

            entity.setMerchant(merchant);
            entity.setFranchise(franchise);

        } else if ("MERCHANT".equalsIgnoreCase(dto.getCustomerType()) && dto.getMerchantId() != null) {

            // 🔍 Check for overlaps before assigning
            List<CustomerSchemeAssignment> overlaps = assignmentRepo.findOverlappingAssignments(
                    dto.getMerchantId(),
                    customerType.toUpperCase(),
                    product.getId(),
                    dto.getEffectiveDate(),
                    dto.getExpiryDate()
            );

            if (!overlaps.isEmpty()) {
                throw new DuplicateResourceException("Customer already has a scheme assigned for this product during the given period.");
            }

            entity.setMerchant(merchant);
            entity.setFranchise(null);
        }

        CustomerSchemeAssignment saved = assignmentRepo.save(entity);
        return toDTO(saved);
    }


    public List<CustomerSchemeAssignmentDTO> getAssignmentsByCustomer(Long customerId, String customerType) {
        List<CustomerSchemeAssignment> assignments;

        if ("FRANCHISE".equals(customerType)) {
            assignments = assignmentRepo.findByFranchiseId(customerId);
        } else if ("MERCHANT".equals(customerType)) {
            assignments = assignmentRepo.findByMerchantId(customerId);
        } else {
            throw new IllegalArgumentException("Invalid customer type: " + customerType);
        }

        return assignments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<CustomerSchemeAssignmentDTO> getAll() {
        return assignmentRepo.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<SchemeGroupedResponseDTO> getAllGroupedByScheme() {
        List<PricingScheme> allSchemes = schemeRepo.findAll();
        Map<Long, List<CustomerSchemeAssignment>> assignmentsByScheme =
                assignmentRepo.findAll().stream()
                        .collect(Collectors.groupingBy(a -> a.getScheme().getId()));

        return allSchemes.stream()
                .map(scheme -> {
                    SchemeGroupedResponseDTO dto = new SchemeGroupedResponseDTO();
                    dto.setSchemeCode(scheme.getSchemeCode());
                    dto.setDescription(scheme.getDescription());
                    dto.setCustomerType(scheme.getCustomerType());
                    dto.setRentalByMonth(scheme.getRentalByMonth());

                    List<CustomerSchemeAssignment> assignments =
                            assignmentsByScheme.getOrDefault(scheme.getId(), Collections.emptyList());
                    dto.setAssignments(assignments.stream()
                            .map(this::toDTO)
                            .collect(Collectors.toList()));

                    return dto;
                })
                .collect(Collectors.toList());
    }

    public CustomerSchemeAssignmentDTO updateAssignment(Long id, CustomerSchemeAssignmentDTO dto) {
        CustomerSchemeAssignment entity = assignmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found with id: " + id));

        PricingScheme scheme = schemeRepo.findById(dto.getSchemeId())
                .orElseThrow(() -> new ResourceNotFoundException("Scheme not found"));

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Update entity fields
        entity.setScheme(scheme);
        entity.setProduct(product);
        entity.setCustomerType(dto.getCustomerType());
        entity.setEffectiveDate(dto.getEffectiveDate());
        entity.setExpiryDate(dto.getExpiryDate());
        entity.setRemarks(dto.getRemarks());

        // Clear existing customer assignments
        entity.setFranchise(null);
        entity.setMerchant(null);

        // Set new customer assignment
        if ("FRANCHISE".equals(dto.getCustomerType()) && dto.getFranchiseId() != null) {
            Franchise franchise = franchiseRepository.findById(dto.getFranchiseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Franchise not found"));
            entity.setFranchise(franchise);
        } else if ("MERCHANT".equals(dto.getCustomerType()) && dto.getMerchantId() != null) {
            Merchant merchant = merchantRepository.findById(dto.getMerchantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));
            entity.setMerchant(merchant);
        }

        CustomerSchemeAssignment saved = assignmentRepo.save(entity);
        return toDTO(saved);
    }

    public void deleteAssignment(Long id) {
        CustomerSchemeAssignment entity = assignmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found with id: " + id));
        assignmentRepo.delete(entity);
    }

    public List<CustomerSchemeAssignmentDTO> getTop5ExpiryDateForDashboard(){
        return assignmentRepo.findTop5ByExpiryDateGreaterThanEqualOrderByExpiryDateAsc(LocalDate.now())
                .stream()
                .map(this::toDTO)
                .toList();

    }
//    private CustomerSchemeAssignmentDTO toDTO(CustomerSchemeAssignment entity) {
//        CustomerSchemeAssignmentDTO dto = new CustomerSchemeAssignmentDTO();
//        dto.setId(entity.getId());
//        dto.setSchemeId(entity.getScheme().getId());
//        dto.setSchemeCode(entity.getScheme().getSchemeCode());
//        dto.setProductId(entity.getProduct().getId());
//        dto.setProductName(entity.getProduct().getProductName());
//        dto.setCustomerType(entity.getCustomerType());
//        dto.setEffectiveDate(entity.getEffectiveDate());
//        dto.setExpiryDate(entity.getExpiryDate());
//        dto.setRemarks(entity.getRemarks());
//
//        // Set customer ID and name based on type
//        if ("FRANCHISE".equalsIgnoreCase(entity.getCustomerType()) && entity.getFranchise() != null) {
//            dto.setFranchiseId(entity.getFranchise().getId());
//            dto.setCustomerName(entity.getFranchise().getFranchiseName());
//        } else if ("MERCHANT".equalsIgnoreCase(entity.getCustomerType()) && entity.getMerchant() != null) {
//            dto.setMerchantId(entity.getMerchant().getId());
//            dto.setCustomerName(entity.getMerchant().getBusinessName());
//        }
//
//        return dto;
//    }

    private CustomerSchemeAssignmentDTO toDTO(CustomerSchemeAssignment entity) {

        CustomerSchemeAssignmentDTO dto = new CustomerSchemeAssignmentDTO();

        dto.setId(entity.getId());
        dto.setSchemeId(entity.getScheme().getId());
        dto.setSchemeCode(entity.getScheme().getSchemeCode());

        dto.setProductId(entity.getProduct().getId());
        dto.setProductName(entity.getProduct().getProductName());

        dto.setCustomerType(entity.getCustomerType());
        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setExpiryDate(entity.getExpiryDate());
        dto.setRemarks(entity.getRemarks());
        dto.setIsRateChanged(entity.getIsRateChanged());

        // ===============================
        // DIRECT MERCHANT
        // ===============================
        if ("MERCHANT".equalsIgnoreCase(entity.getCustomerType())
                && entity.getMerchant() != null) {

            dto.setMerchantId(entity.getMerchant().getId());
            dto.setCustomerName(entity.getMerchant().getBusinessName());
        }

        // ===============================
        // FRANCHISE (WITH MERCHANT)
        // ===============================
        else if ("FRANCHISE".equalsIgnoreCase(entity.getCustomerType())
                && entity.getFranchise() != null) {

            dto.setFranchiseId(entity.getFranchise().getId());

            String franchiseName = entity.getFranchise().getFranchiseName();
            String merchantName = entity.getMerchant() != null
                    ? entity.getMerchant().getBusinessName()
                    : "N/A";

            dto.setMerchantId(
                    entity.getMerchant() != null ? entity.getMerchant().getId() : null
            );

            // CONCAT LOGIC HERE
            dto.setCustomerName(
                    franchiseName + " (Merchant: " + merchantName + ")"
            );
        }

        return dto;
    }

    @Transactional
    public CustomerSchemeAssignmentDTO franchiseUpdateRate(
            Long assignmentId,
            List<CardRateDTO> newRates
    ) {

        CustomerSchemeAssignment assignment =
                assignmentRepo.findById(assignmentId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Assignment not found with id: " + assignmentId
                                )
                        );

        if (Boolean.TRUE.equals(assignment.getIsRateChanged())) {
            throw new IllegalArgumentException(
                    "Rates have already been overridden for this assignment."
            );
        }

        PricingScheme originalScheme = assignment.getScheme();

        if (originalScheme == null) {
            throw new ResourceNotFoundException(
                    "No pricing scheme associated with this assignment."
            );
        }

        if (originalScheme.getCardRates() == null ||
                originalScheme.getCardRates().isEmpty()) {

            throw new IllegalArgumentException(
                    "The assigned pricing scheme does not contain any card rates."
            );
        }

        if (newRates == null) {
            newRates = Collections.emptyList();
        }

        /*
         * Key format:
         * productCategoryId_cardName
         *
         * This is necessary because the same card name can exist
         * under multiple product categories.
         */
        Map<String, Double> newRateMap =
                newRates.stream()
                        .filter(Objects::nonNull)
                        .filter(rate ->
                                rate.productCategoryId() != null &&
                                        rate.cardName() != null &&
                                        !rate.cardName().isBlank()
                        )
                        .collect(
                                Collectors.toMap(
                                        rate -> buildCardRateKey(
                                                rate.productCategoryId(),
                                                rate.cardName()
                                        ),
                                        rate -> rate.merchantRate() != null
                                                ? rate.merchantRate()
                                                : 0.0,
                                        (existingRate, replacementRate) -> {
                                            throw new IllegalArgumentException(
                                                    "Duplicate product and card combination found in request."
                                            );
                                        }
                                )
                        );

        // Create cloned pricing scheme
        PricingScheme newScheme = new PricingScheme();

        String newCode =
                pricingSchemeService.generateNextSchemeCode();

        newScheme.setSchemeCode(newCode);
        newScheme.setRentalByMonth(
                originalScheme.getRentalByMonth()
        );
        newScheme.setGst(
                originalScheme.getGst()
        );
        newScheme.setCustomerType(
                originalScheme.getCustomerType()
        );

        String originalDescription =
                originalScheme.getDescription() != null
                        ? originalScheme.getDescription()
                        : "";

        newScheme.setDescription(
                originalDescription +
                        " (Modified by franchise for assignment ID: " +
                        assignmentId +
                        ")"
        );

        for (CardRate originalCardRate :
                originalScheme.getCardRates()) {

            if (originalCardRate.getProductCategory() == null ||
                    originalCardRate.getProductCategory().getId() == null) {

                throw new IllegalArgumentException(
                        "Product category is missing for card rate: " +
                                originalCardRate.getCardName()
                );
            }

            Long productCategoryId =
                    originalCardRate
                            .getProductCategory()
                            .getId();

            String cardName =
                    originalCardRate.getCardName();

            String rateKey =
                    buildCardRateKey(
                            productCategoryId,
                            cardName
                    );

            Double enteredRate =
                    newRateMap.get(rateKey);

            /*
             * If frontend has not sent a new rate,
             * retain the original merchant rate.
             */
            if (enteredRate == null) {
                enteredRate =
                        originalCardRate.getMerchantRate() != null
                                ? originalCardRate.getMerchantRate()
                                : 0.0;
            }

            Double minimumAllowedRate =
                    originalCardRate.getFranchiseRate() != null
                            ? originalCardRate.getFranchiseRate()
                            : 0.0;

            if (enteredRate < minimumAllowedRate) {
                throw new IllegalArgumentException(
                        "Merchant rate for product " +
                                originalCardRate
                                        .getProductCategory()
                                        .getCategoryName() +
                                " and card " +
                                cardName +
                                " (" +
                                enteredRate +
                                "%) cannot be less than the franchise rate (" +
                                minimumAllowedRate +
                                "%)."
                );
            }

            CardRate newCardRate = new CardRate();

            newCardRate.setProductCategory(
                    originalCardRate.getProductCategory()
            );

            newCardRate.setCardName(
                    originalCardRate.getCardName()
            );

            newCardRate.setCategory(
                    originalCardRate.getCategory()
            );

            newCardRate.setFranchiseRate(
                    originalCardRate.getFranchiseRate()
            );

            newCardRate.setMerchantRate(
                    enteredRate
            );

            /*
             * addCardRate must set:
             * cardRate.setPricingScheme(this)
             */
            newScheme.addCardRate(newCardRate);
        }

        PricingScheme savedScheme =
                schemeRepo.save(newScheme);

        assignment.setScheme(savedScheme);
        assignment.setIsRateChanged(true);

        CustomerSchemeAssignment savedAssignment =
                assignmentRepo.save(assignment);

        return toDTO(savedAssignment);
    }

    private String buildCardRateKey(
            Long productCategoryId,
            String cardName
    ) {

        return productCategoryId +
                "_" +
                cardName.trim().toLowerCase();
    }


//----------------------------------For ProductScheme Reports------------------------------------------------

    // Add these methods to the existing CustomerSchemeAssignmentService class

    /**
     * Get all scheme assignments with complete product and customer details for reporting
     */
    public List<ProductSchemeReportDTO> getAllSchemeAssignmentsForReport() {
        List<CustomerSchemeAssignment> assignments = assignmentRepo.findAllWithCompleteDetails();
        return assignments.stream()
                .map(this::toProductSchemeReportDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get filtered scheme assignments for reporting
     */
    public List<ProductSchemeReportDTO> getFilteredSchemeAssignmentsForReport(
            String customerType,
            Long productId,
            Long schemeId,
            Long categoryId,
            Boolean activeOnly) {

        LocalDate currentDate = LocalDate.now();
        boolean active = activeOnly != null && activeOnly;

        List<CustomerSchemeAssignment> assignments = assignmentRepo.findAllWithCompleteDetailsFiltered(
                customerType,
                productId,
                schemeId,
                categoryId,
                active,
                currentDate
        );

        return assignments.stream()
                .map(this::toProductSchemeReportDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get scheme assignments by customer type for reporting
     */
    public List<ProductSchemeReportDTO> getSchemeAssignmentsByCustomerTypeForReport(String customerType) {
        List<CustomerSchemeAssignment> assignments =
                assignmentRepo.findAllWithCompleteDetailsByCustomerType(customerType);
        return assignments.stream()
                .map(this::toProductSchemeReportDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get scheme assignments by product for reporting
     */
    public List<ProductSchemeReportDTO> getSchemeAssignmentsByProductForReport(Long productId) {
        List<CustomerSchemeAssignment> assignments =
                assignmentRepo.findAllWithCompleteDetailsByProduct(productId);
        return assignments.stream()
                .map(this::toProductSchemeReportDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get active scheme assignments for reporting
     */
    public List<ProductSchemeReportDTO> getActiveSchemeAssignmentsForReport() {
        LocalDate currentDate = LocalDate.now();
        List<CustomerSchemeAssignment> assignments =
                assignmentRepo.findAllActiveWithCompleteDetails(currentDate);
        return assignments.stream()
                .map(this::toProductSchemeReportDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convert CustomerSchemeAssignment entity to ProductSchemeReportDTO
     */
    private ProductSchemeReportDTO toProductSchemeReportDTO(CustomerSchemeAssignment entity) {
        ProductSchemeReportDTO dto = new ProductSchemeReportDTO();

        // Assignment details
        dto.setAssignmentId(entity.getId());
        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setExpiryDate(entity.getExpiryDate());
        dto.setRemarks(entity.getRemarks());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Check if assignment is active
        LocalDate now = LocalDate.now();
        boolean isActive = entity.getEffectiveDate() != null &&
                !entity.getEffectiveDate().isAfter(now) &&
                (entity.getExpiryDate() == null || !entity.getExpiryDate().isBefore(now));
        dto.setActive(isActive);

        // Scheme details
        if (entity.getScheme() != null) {
            dto.setSchemeId(entity.getScheme().getId());
            dto.setSchemeCode(entity.getScheme().getSchemeCode());
            dto.setSchemeDescription(entity.getScheme().getDescription());
            dto.setRentalByMonth(entity.getScheme().getRentalByMonth());
            dto.setSchemeCustomerType(entity.getScheme().getCustomerType());
        }

        // Product details
        if (entity.getProduct() != null) {
            dto.setProductId(entity.getProduct().getId());
            dto.setProductCode(entity.getProduct().getProductCode());
            dto.setProductName(entity.getProduct().getProductName());
            dto.setProductModel(entity.getProduct().getModel());
            dto.setProductBrand(entity.getProduct().getBrand());
            dto.setProductDescription(entity.getProduct().getDescription());
            dto.setWarrantyPeriod(entity.getProduct().getWarrantyPeriod());
            dto.setWarrantyType(entity.getProduct().getWarrantyType());
            dto.setHsn(entity.getProduct().getHsn());
            dto.setProductStatus(entity.getProduct().getStatus());
            dto.setMinOrderQuantity(entity.getProduct().getMinOrderQuantity());
            dto.setMaxOrderQuantity(entity.getProduct().getMaxOrderQuantity());

            // Product Category details
            if (entity.getProduct().getProductCategory() != null) {
                dto.setCategoryId(entity.getProduct().getProductCategory().getId());
                dto.setCategoryName(entity.getProduct().getProductCategory().getCategoryName());
                dto.setCategoryCode(entity.getProduct().getProductCategory().getCategoryCode());
            }

            // Vendor details
            if (entity.getProduct().getVendor() != null) {
                dto.setVendorId(entity.getProduct().getVendor().getId());
                dto.setVendorName(entity.getProduct().getVendor().getName());
            }
        }

        // Customer details
        dto.setCustomerType(entity.getCustomerType());

        if ("FRANCHISE".equalsIgnoreCase(entity.getCustomerType()) && entity.getFranchise() != null) {
            dto.setCustomerId(entity.getFranchise().getId());
            dto.setCustomerName(entity.getFranchise().getFranchiseName());
            dto.setFranchiseAddress(entity.getFranchise().getAddress());
            dto.setFranchiseContact(entity.getFranchise().getContactPerson().getPhoneNumber());
        } else if ("MERCHANT".equalsIgnoreCase(entity.getCustomerType()) && entity.getMerchant() != null) {
            dto.setCustomerId(entity.getMerchant().getId());
            dto.setCustomerName(entity.getMerchant().getBusinessName());
            dto.setMerchantBusinessName(entity.getMerchant().getBusinessName());
            dto.setMerchantAddress(entity.getMerchant().getAddress());
            dto.setMerchantContact(entity.getMerchant().getContactPerson().getPhoneNumber());
        }

        return dto;
    }
}