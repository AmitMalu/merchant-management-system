package com.project2.ism.DTO;

import org.springframework.web.multipart.MultipartFile;

public class MerchantFormDTO {

    private Long franchiseId;
    private String businessName;
    private String legalName;
    private String businessType;
    private String gstNumber;
    private String panNumber;

    private Boolean panVerified;

    private String registrationNumber;
    private String businessAddress;

    private String primaryContactName;
    private String primaryContactMobile;
    private String alternateContactMobile;
    private String primaryContactEmail;
    private String landlineNumber;

    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;
    private String branchName;
    private String accountType;

    private Boolean bankVerified;

    // Files
    private MultipartFile panCardDocument;
    private MultipartFile gstCertificate;
    private MultipartFile addressProof;
    private MultipartFile bankProof;
    private MultipartFile franchiseAgreement;

    //private boolean isApproved;

    //constructor
    public MerchantFormDTO(
            Long franchiseId,
            String businessName,
            String legalName,
            String businessType,
            String gstNumber,
            String panNumber,
            Boolean panVerified,
            String registrationNumber,
            String businessAddress,
            String primaryContactName,
            String primaryContactMobile,
            String alternateContactMobile,
            String primaryContactEmail,
            String landlineNumber,
            String bankName,
            String accountHolderName,
            String accountNumber,
            String ifscCode,
            String branchName,
            String accountType,
            Boolean bankVerified,
            MultipartFile panCardDocument,
            MultipartFile gstCertificate,
            MultipartFile addressProof,
            MultipartFile bankProof,
            MultipartFile franchiseAgreement
    ) {

        this.franchiseId = franchiseId;
        this.businessName = businessName;
        this.legalName = legalName;
        this.businessType = businessType;
        this.gstNumber = gstNumber;
        this.panNumber = panNumber;
        this.panVerified = panVerified;

        this.registrationNumber = registrationNumber;
        this.businessAddress = businessAddress;

        this.primaryContactName = primaryContactName;
        this.primaryContactMobile = primaryContactMobile;
        this.alternateContactMobile = alternateContactMobile;
        this.primaryContactEmail = primaryContactEmail;
        this.landlineNumber = landlineNumber;

        this.bankName = bankName;
        this.accountHolderName = accountHolderName;
        this.accountNumber = accountNumber;
        this.ifscCode = ifscCode;
        this.branchName = branchName;
        this.accountType = accountType;
        this.bankVerified = bankVerified;

        this.panCardDocument = panCardDocument;
        this.gstCertificate = gstCertificate;
        this.addressProof = addressProof;
        this.bankProof = bankProof;
        this.franchiseAgreement = franchiseAgreement;
    }

    //setter getter

    //public boolean isApproved() {
    //    return isApproved;
    //}

    //public void setApproved(boolean approved) {
      //  isApproved = approved;
    //}

    public Long getFranchiseId() {
        return franchiseId;
    }

    public void setFranchiseId(Long franchiseId) {
        this.franchiseId = franchiseId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public void setPanNumber(String panNumber) {
        this.panNumber = panNumber;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getBusinessAddress() {
        return businessAddress;
    }

    public void setBusinessAddress(String businessAddress) {
        this.businessAddress = businessAddress;
    }

    public String getPrimaryContactName() {
        return primaryContactName;
    }

    public void setPrimaryContactName(String primaryContactName) {
        this.primaryContactName = primaryContactName;
    }

    public String getPrimaryContactMobile() {
        return primaryContactMobile;
    }

    public void setPrimaryContactMobile(String primaryContactMobile) {
        this.primaryContactMobile = primaryContactMobile;
    }

    public String getAlternateContactMobile() {
        return alternateContactMobile;
    }

    public void setAlternateContactMobile(String alternateContactMobile) {
        this.alternateContactMobile = alternateContactMobile;
    }

    public String getPrimaryContactEmail() {
        return primaryContactEmail;
    }

    public void setPrimaryContactEmail(String primaryContactEmail) {
        this.primaryContactEmail = primaryContactEmail;
    }

    public String getLandlineNumber() {
        return landlineNumber;
    }

    public void setLandlineNumber(String landlineNumber) {
        this.landlineNumber = landlineNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public void setIfscCode(String ifscCode) {
        this.ifscCode = ifscCode;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public MultipartFile getPanCardDocument() {
        return panCardDocument;
    }

    public void setPanCardDocument(MultipartFile panCardDocument) {
        this.panCardDocument = panCardDocument;
    }

    public MultipartFile getGstCertificate() {
        return gstCertificate;
    }

    public void setGstCertificate(MultipartFile gstCertificate) {
        this.gstCertificate = gstCertificate;
    }

    public MultipartFile getAddressProof() {
        return addressProof;
    }

    public void setAddressProof(MultipartFile addressProof) {
        this.addressProof = addressProof;
    }

    public MultipartFile getBankProof() {
        return bankProof;
    }

    public void setBankProof(MultipartFile bankProof) {
        this.bankProof = bankProof;
    }

    public MultipartFile getFranchiseAgreement() {
        return franchiseAgreement;
    }

    public void setFranchiseAgreement(MultipartFile franchiseAgreement) {
        this.franchiseAgreement = franchiseAgreement;
    }

    public Boolean getPanVerified() {
        return panVerified;
    }

    public Boolean getBankVerified() {
        return bankVerified;
    }

    public void setPanVerified(Boolean panVerified) {
        this.panVerified = panVerified;
    }

    public void setBankVerified(Boolean bankVerified) {
        this.bankVerified = bankVerified;
    }
}