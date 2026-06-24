
package com.project2.ism.Service;

import com.project2.ism.DTO.*;
import com.project2.ism.DTO.DigiLockerDTO.DigilockerInitializeRequestDTO;
import com.project2.ism.Enum.VerificationType;
import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.*;
import com.project2.ism.Model.InventoryTransactions.ProductSerialNumbers;
import com.project2.ism.Model.Users.BankDetails;
import com.project2.ism.Model.Users.Franchise;

import com.project2.ism.Repository.*;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class FranchiseService {

    private static final Logger log =
            LoggerFactory.getLogger(FranchiseService.class);

    private final FranchiseRepository franchiseRepository;
    private final FileStorageService fileStorageService;
    private final UserService userService;
    private final MerchantRepository merchantRepository;
    private final MerchantWalletRepository merchantWalletRepository;

    private final ProductSerialsRepository serialRepo;
    private final OutwardTransactionRepository outwardRepo;
    private final DigiLockerService digilockerService;


    private final FranchiseWalletRepository franchiseWalletRepository;
    public FranchiseService(FranchiseRepository franchiseRepository,
                            FileStorageService fileStorageService,
                            UserService userService, MerchantRepository merchantRepository, MerchantWalletRepository merchantWalletRepository,
                            ProductSerialsRepository serialRepo, OutwardTransactionRepository outwardRepo, FranchiseWalletRepository franchiseWalletRepository,
                            DigiLockerService digilockerService) {
        this.franchiseRepository = franchiseRepository;
        this.fileStorageService = fileStorageService;
        this.userService = userService;
        this.merchantRepository = merchantRepository;
        this.merchantWalletRepository = merchantWalletRepository;
        this.serialRepo = serialRepo;
        this.outwardRepo = outwardRepo;
        this.franchiseWalletRepository = franchiseWalletRepository;
        this.digilockerService = digilockerService;
    }

    public void createFranchise(FranchiseFormDTO dto) {
        Franchise franchise = new Franchise();

        // Basic details
        franchise.setFranchiseName(dto.getFranchiseName());
        franchise.setLegalName(dto.getLegalName());
        franchise.setBusinessType(dto.getBusinessType());
        franchise.setGstNumber(dto.getGstNumber());
        franchise.setPanNumber(dto.getPanNumber());
        franchise.setPanVerified(dto.getPanVerified());
        franchise.setRegistrationNumber(dto.getRegistrationNumber());
        franchise.setAddress(dto.getBusinessAddress());

        // Contact Person
        ContactPerson contact = new ContactPerson();
        contact.setName(dto.getPrimaryContactName());
        contact.setPhoneNumber(dto.getPrimaryContactMobile());
        contact.setAlternatePhoneNum(dto.getAlternateContactMobile());
        contact.setEmail(dto.getPrimaryContactEmail());
        contact.setLandlineNumber(dto.getLandlineNumber());
        franchise.setContactPerson(contact);

        // Bank Details
        BankDetails bank = new BankDetails();
        bank.setBankName(dto.getBankName());
        bank.setAccountHolderName(dto.getAccountHolderName());
        bank.setAccountNumber(dto.getAccountNumber());
        bank.setIfsc(dto.getIfscCode());
        bank.setBranchName(dto.getBranchName());
        bank.setAccountType(dto.getAccountType());
        bank.setBankVerified(dto.getBankVerified());
        franchise.setBankDetails(bank);


        // Upload Documents
        UploadDocuments docs = new UploadDocuments();
        if (dto.getPanCardDocument() != null && !dto.getPanCardDocument().isEmpty()) {
            docs.setPanProof(fileStorageService.store(dto.getPanCardDocument(), "franchise_pan"));
        }
        if (dto.getGstCertificate() != null && !dto.getGstCertificate().isEmpty()) {
            docs.setGstCertificateProof(fileStorageService.store(dto.getGstCertificate(), "franchise_gst"));
        }
        if (dto.getAddressProof() != null && !dto.getAddressProof().isEmpty()) {
            docs.setAddressProof(fileStorageService.store(dto.getAddressProof(), "franchise_address"));
        }
        if (dto.getBankProof() != null && !dto.getBankProof().isEmpty()) {
            docs.setBankAccountProof(fileStorageService.store(dto.getBankProof(), "franchise_bank"));
        }
        if (dto.getFranchiseAgreement() != null && !dto.getFranchiseAgreement().isEmpty()) {
            docs.setOther1(fileStorageService.store(dto.getFranchiseAgreement(), "franchise_agreement"));
        }
        franchise.setUploadDocuments(docs);

        // Set default values
        franchise.setStatus("ACTIVE");
        franchise.setCreatedAt(LocalDateTime.now());
        franchise.setUpdatedAt(LocalDateTime.now());

        Franchise saved = franchiseRepository.save(franchise);

        FranchiseWallet w = new FranchiseWallet();
        Franchise fRef = new Franchise();
        fRef.setId(saved.getId());
      
        w.setFranchise(fRef);
        w.setAvailableBalance(BigDecimal.ZERO);
        w.setLastUpdatedAmount(BigDecimal.ZERO);
        w.setLastUpdatedAt(LocalDateTime.now());
        w.setTotalCash(BigDecimal.ZERO);
        w.setCutOfAmount(BigDecimal.ZERO);
        w.setUsedCash(BigDecimal.ZERO);
        franchiseWalletRepository.save(w);
        // Create login credentials
        userService.createAndSendCredentials(
                dto.getPrimaryContactEmail(),
                "FRANCHISE",
                null
        );

//        try {
//
//            digilockerService.initializeForFranchise(saved);
//
//        } catch (Exception ex) {
//
//            log.error(
//                    "Digilocker initialization failed for franchise id {}",
//                    saved.getId(),
//                    ex
//            );
//
//        }
    }

    public List<FranchiseListDTO> getAllFranchisesForList() {
        Map<Long, BigDecimal> walletBalances = franchiseWalletRepository.findAll().stream()
                .collect(Collectors.toMap(wallet -> wallet.getFranchise().getId(),
                        FranchiseWallet::getAvailableBalance));

        return franchiseRepository.findAll()
                .stream()
                .map(franchise -> {
                    Long merchantCount = merchantRepository.countByFranchiseId(franchise.getId());
                    BigDecimal walletBalance = walletBalances.getOrDefault(franchise.getId(), BigDecimal.ZERO);
                    return mapToListDTO(franchise, walletBalance, merchantCount);
                })
                .collect(Collectors.toList());

    }

    public List<FranchiseListDTO> getAllFranchisesWithMerchantCount() {
        return franchiseRepository.findAllWithMerchantCount();
    }


    public Franchise getFranchiseById(Long id) {

        return franchiseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Franchise not found with ID: " + id));
    }

    public FranchiseViewDTO getFranchiseViewDTOById(Long id) {
        // Get the franchise entity using existing method
        Franchise franchise = getFranchiseById(id);

        // Create and populate the DTO
        FranchiseViewDTO dto = new FranchiseViewDTO(
                franchise.getFranchiseName(),
                franchise.getLegalName(),
                franchise.getBusinessType(),
                franchise.getGstNumber(),
                franchise.getPanNumber(),
                franchise.getRegistrationNumber(),
                franchise.getAddress(),
                franchise.getContactPerson().getName(),
                franchise.getContactPerson().getPhoneNumber(),
                franchise.getContactPerson().getAlternatePhoneNum(),
                franchise.getContactPerson().getEmail(),
                franchise.getContactPerson().getLandlineNumber(),
                franchise.getBankDetails().getBankName(),
                franchise.getBankDetails().getAccountHolderName(),
                franchise.getBankDetails().getAccountNumber(),
                franchise.getBankDetails().getIfsc(),
                franchise.getBankDetails().getBranchName(),
                franchise.getBankDetails().getAccountType(),
                franchise.getUploadDocuments().getPanProof(), // panCardDocument - MultipartFile (not stored in entity)
                franchise.getUploadDocuments().getGstCertificateProof(), // gstCertificate - MultipartFile (not stored in entity)
                franchise.getUploadDocuments().getAddressProof(), // addressProof - MultipartFile (not stored in entity)
                franchise.getUploadDocuments().getBankAccountProof(), // bankProof - MultipartFile (not stored in entity)
                franchise.getUploadDocuments().getOther1(),  // franchiseAgreement - MultipartFile (not stored in entity),
                getWalletBalance(franchise.getId()),
                franchise.getPanVerified(),
                franchise.getBankDetails().getBankVerified()
        );

        return dto;
    }

    public Franchise updateFranchise(Long id, FranchiseFormDTO dto) throws IOException {
        Franchise franchise = getFranchiseById(id);

        // Update basic details (only if provided)
        if (dto.getFranchiseName() != null && !dto.getFranchiseName().isEmpty()) {
            franchise.setFranchiseName(dto.getFranchiseName());
        }
        if (dto.getLegalName() != null && !dto.getLegalName().isEmpty()) {
            franchise.setLegalName(dto.getLegalName());
        }
        if (dto.getBusinessType() != null && !dto.getBusinessType().isEmpty()) {
            franchise.setBusinessType(dto.getBusinessType());
        }
        if (dto.getBusinessAddress() != null && !dto.getBusinessAddress().isEmpty()) {
            franchise.setAddress(dto.getBusinessAddress());
        }
        if (dto.getGstNumber() != null && !dto.getGstNumber().isEmpty()) {
            franchise.setGstNumber(dto.getGstNumber());
        }
        if (dto.getPanNumber() != null && !dto.getPanNumber().isEmpty()) {
            franchise.setPanNumber(dto.getPanNumber());
        }
        if (dto.getPanVerified() != null) {
            franchise.setPanVerified(dto.getPanVerified());
        }
        if (dto.getRegistrationNumber() != null && !dto.getRegistrationNumber().isEmpty()) {
            franchise.setRegistrationNumber(dto.getRegistrationNumber());
        }

        // Update contact details
        ContactPerson contactPerson = franchise.getContactPerson();
        if (contactPerson == null) {
            contactPerson = new ContactPerson();
        }

        if (dto.getPrimaryContactName() != null && !dto.getPrimaryContactName().isEmpty()) {
            contactPerson.setName(dto.getPrimaryContactName());
        }
        if (dto.getPrimaryContactMobile() != null && !dto.getPrimaryContactMobile().isEmpty()) {
            contactPerson.setPhoneNumber(dto.getPrimaryContactMobile());
        }
        if (dto.getPrimaryContactEmail() != null && !dto.getPrimaryContactEmail().isEmpty()) {
            contactPerson.setEmail(dto.getPrimaryContactEmail());
        }
        if (dto.getAlternateContactMobile() != null && !dto.getAlternateContactMobile().isEmpty()) {
            contactPerson.setAlternatePhoneNum(dto.getAlternateContactMobile());
        }
        if (dto.getLandlineNumber() != null && !dto.getLandlineNumber().isEmpty()) {
            contactPerson.setLandlineNumber(dto.getLandlineNumber());
        }

        franchise.setContactPerson(contactPerson);

        // Update bank details
        BankDetails bankDetails = franchise.getBankDetails();
        if (bankDetails == null) {
            bankDetails = new BankDetails();
        }

        if (dto.getBankName() != null && !dto.getBankName().isEmpty()) {
            bankDetails.setBankName(dto.getBankName());
        }
        if (dto.getAccountHolderName() != null && !dto.getAccountHolderName().isEmpty()) {
            bankDetails.setAccountHolderName(dto.getAccountHolderName());
        }
        if (dto.getAccountNumber() != null && !dto.getAccountNumber().isEmpty()) {
            bankDetails.setAccountNumber(dto.getAccountNumber());
        }
        if (dto.getIfscCode() != null && !dto.getIfscCode().isEmpty()) {
            bankDetails.setIfsc(dto.getIfscCode());
        }
        if (dto.getBranchName() != null && !dto.getBranchName().isEmpty()) {
            bankDetails.setBranchName(dto.getBranchName());
        }
        if (dto.getAccountType() != null && !dto.getAccountType().isEmpty()) {
            bankDetails.setAccountType(dto.getAccountType());
        }
        if (dto.getBankVerified() != null) {
            bankDetails.setBankVerified(dto.getBankVerified());
        }

        franchise.setBankDetails(bankDetails);

        // Handle document uploads (only if new files are provided)
        UploadDocuments uploadDocuments = franchise.getUploadDocuments();
        if (uploadDocuments == null) {
            uploadDocuments = new UploadDocuments();
        }

        if (dto.getPanCardDocument() != null && !dto.getPanCardDocument().isEmpty()) {
            String panPath = fileStorageService.store(dto.getPanCardDocument(), "franchise_pan");
            uploadDocuments.setPanProof(panPath);
        }
        if (dto.getGstCertificate() != null && !dto.getGstCertificate().isEmpty()) {
            String gstPath = fileStorageService.store(dto.getGstCertificate(), "franchise_gst");
            uploadDocuments.setGstCertificateProof(gstPath);
        }
        if (dto.getAddressProof() != null && !dto.getAddressProof().isEmpty()) {
            String addressPath = fileStorageService.store(dto.getAddressProof(), "franchise_address");
            uploadDocuments.setAddressProof(addressPath);
        }
        if (dto.getBankProof() != null && !dto.getBankProof().isEmpty()) {
            String bankPath = fileStorageService.store(dto.getBankProof(), "franchise_bank");
            uploadDocuments.setBankAccountProof(bankPath);
        }

        franchise.setUploadDocuments(uploadDocuments);
        franchise.setUpdatedAt(LocalDateTime.now());

        return franchiseRepository.save(franchise);
    }
    public void deleteFranchise(Long id) {
        Franchise franchise = getFranchiseById(id);

        // Delete associated files
        UploadDocuments docs = franchise.getUploadDocuments();
        if (docs != null) {
            deleteDocumentFile(docs.getPanProof());
            deleteDocumentFile(docs.getGstCertificateProof());
            deleteDocumentFile(docs.getAddressProof());
            deleteDocumentFile(docs.getBankAccountProof());
            deleteDocumentFile(docs.getOther1());
            deleteDocumentFile(docs.getOther2());
            deleteDocumentFile(docs.getOther3());
        }

        franchiseRepository.delete(franchise);
    }

    private void deleteDocumentFile(String filePath) {
        if (filePath != null && !filePath.isEmpty()) {
            try {
                fileStorageService.deleteFile(filePath);
            } catch (Exception e) {
                // Log error but don't fail the deletion
                System.err.println("Failed to delete file: " + filePath + " - " + e.getMessage());
            }
        }
    }

    private FranchiseListDTO mapToListDTO(Franchise franchise,BigDecimal walletBalance, Long merchantCount) {
        return new FranchiseListDTO(
                franchise.getId(),
                franchise.getFranchiseName(),
                franchise.getContactPerson() != null ? franchise.getContactPerson().getName() : null,
                franchise.getContactPerson() != null ? franchise.getContactPerson().getEmail() : null,
                franchise.getContactPerson() != null ? franchise.getContactPerson().getPhoneNumber() : null,
                franchise.getAddress(),
                merchantCount,
                walletBalance != null ? walletBalance : BigDecimal.ZERO,
                franchise.getStatus() != null ? franchise.getStatus() : "ACTIVE",
                franchise.getCreatedAt()
        );
    }


    public Franchise getFranchiseByEmail(String email) {
        return franchiseRepository.findByContactPerson_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException("Franchise not found with email " + email));
    }


    public List<ProductSerialNumbers> getValidPSN(Long productId,Long franchiseId){
        return serialRepo.findByFranchise_IdAndProduct_IdAndMerchant_IdIsNullAndReceivedDateByFranchiseIsNotNull(franchiseId,productId);

    }
//    public List<ProductSerialNumbers> getInTransitPSN(Long outwardID) {
//        return serialRepo.findByOutwardTransaction_IdAndMerchantIsNotNullAndReceivedDateIsNull(outwardID);
//    }



//    public List<FranchiseProductSummaryDTO> getProductsOfFranchise(Long franchiseId) {
//        List<OutwardTransactions> outwardList = outwardRepo.findByFranchiseIdAndReceivedDateIsNotNull(franchiseId);
//
//        List<FranchiseProductSummaryDTO> result = new ArrayList<>();
//        for (OutwardTransactions o : outwardList) {
//            int totalQty = o.getQuantity();
//            int valid = getValidPSN(o.getId()).size();
//            int inTransit = getInTransitPSN(o.getId()).size();
//
//            result.add(new FranchiseProductSummaryDTO(
//                    o.getProduct().getId(),
//                    o.getProduct().getProductName(),
//                    o.getProduct().getProductCode(),
//                    o.getProduct().getProductCategory().getCategoryName(), // assuming you have category relation
//                    totalQty,
//                    valid,
//                    inTransit
//            ));
//        }
//        return result;
//    }

    public List<FranchiseProductSummaryDTO> getProductsOfFranchise(Long franchiseId) {

        // Fetch all PSNs for the franchise
        List<ProductSerialNumbers> psns = serialRepo.findByFranchise_IdAndReceivedDateByFranchiseIsNotNull(franchiseId);

        // Map<productId, DTO> for aggregation
        Map<Long, FranchiseProductSummaryDTO> summaryMap = new HashMap<>();

        for (ProductSerialNumbers psn : psns) {
            Product product = psn.getProduct();
            Long productId = product.getId();

            // Aggregate per product
            FranchiseProductSummaryDTO dto = summaryMap.get(productId);
            if (dto == null) {
                dto = new FranchiseProductSummaryDTO(
                        productId,
                        product.getProductName(),
                        product.getProductCode(),
                        product.getProductCategory().getCategoryName(),
                        0,
                        0,
                        0
                );
                summaryMap.put(productId, dto);
            }

            // Total quantity = all PSNs
            dto.setTotalQuantity(dto.getTotalQuantity() + 1);

            // Valid = franchise has received, but merchant is still null
            if (psn.getReceivedDateByFranchise() != null && psn.getMerchant() == null) {
                dto.setValid(dto.getValid() + 1);
            }

            // In transit = franchise has received and merchant assigned, but merchant hasn't received physically
            if (psn.getReceivedDateByFranchise() != null && psn.getMerchant() != null && psn.getReceivedDate() == null) {
                dto.setInTransit(dto.getInTransit() + 1);
            }
        }

        return new ArrayList<>(summaryMap.values());
    }



    public FranchiseMerchantStatsDTO getStats() {
        FranchiseMerchantStatsDTO dto = new FranchiseMerchantStatsDTO();

        dto.totalFranchises = franchiseRepository.count();
        dto.totalMerchants = merchantRepository.count();
        dto.totalDirectMerchants = merchantRepository.countDirectMerchants();
        dto.totalFranchiseMerchants = merchantRepository.countFranchiseMerchants();

        dto.totalFranchiseWalletBalance = franchiseWalletRepository.getTotalFranchiseWalletBalance();
        dto.totalDirectMerchantWalletBalance = merchantWalletRepository.getTotalDirectMerchantWalletBalance();
        dto.totalFranchiseMerchantWalletBalance = merchantWalletRepository.getTotalFranchiseMerchantWalletBalance();

        dto.merchantsPerFranchise = merchantRepository.countByFranchise().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        return dto;
    }

    public BigDecimal getWalletBalance(Long franchiseId) {
        return franchiseWalletRepository.findByFranchiseId(franchiseId)
                .map(FranchiseWallet::getAvailableBalance)
                .orElse(BigDecimal.ZERO); // if wallet row not present yet
    }



}