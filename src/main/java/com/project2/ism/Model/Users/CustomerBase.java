package com.project2.ism.Model.Users;

import com.project2.ism.Model.ContactPerson;
import com.project2.ism.Model.UploadDocuments;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@MappedSuperclass
public abstract class CustomerBase {

    @NotBlank(message = "Legal name is required")
    private String legalName;

    @NotBlank(message = "Business type is required")
    private String businessType;

    @Column(nullable = true)
    @Pattern(regexp = "\\d{2}[A-Z]{5}\\d{4}[A-Z]{1}[A-Z\\d]{1}Z[A-Z\\d]{1}",
            message = "Invalid GST number format")
    private String gstNumber;

    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}",
            message = "Invalid PAN number format")
    private String panNumber;

    @Column(name = "pan_verified")
    private Boolean panVerified = false;

    private String registrationNumber;

    @NotBlank(message = "Address is required")
    private String address;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "contact_person_name")),
            @AttributeOverride(name = "email", column = @Column(name = "contact_person_email")),
            @AttributeOverride(name = "phoneNumber", column = @Column(name = "contact_person_phone")),
            @AttributeOverride(name = "alternatePhoneNum", column = @Column(name = "contact_person_alt_phone")),
            @AttributeOverride(name = "LandlineNumber", column = @Column(name = "contact_person_landline"))
    })
    @NotNull(message = "Contact person details are required")
    private ContactPerson contactPerson;

    @Embedded
    private BankDetails bankDetails;

    @Embedded
    private UploadDocuments uploadDocuments;


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

    public Boolean getPanVerified() { return panVerified; }

    public void setPanVerified(Boolean panVerified) { this.panVerified = panVerified; }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public ContactPerson getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(ContactPerson contactPerson) {
        this.contactPerson = contactPerson;
    }

    public BankDetails getBankDetails() {
        return bankDetails;
    }

    public void setBankDetails(BankDetails bankDetails) {
        this.bankDetails = bankDetails;
    }

    public UploadDocuments getUploadDocuments() {
        return uploadDocuments;
    }

    public void setUploadDocuments(UploadDocuments uploadDocuments) {
        this.uploadDocuments = uploadDocuments;
    }
}
