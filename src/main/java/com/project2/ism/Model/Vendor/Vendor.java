package com.project2.ism.Model.Vendor;

import com.project2.ism.Model.ContactPerson;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Vendor {

    // ========== Basic Info ==========
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Vendor name is required")
    @Column(unique = true, nullable = false)
    private String name;

    @NotBlank(message = "Bank Type is required")
    @Column(nullable = false)
    private String bankType;

    @NotNull(message = "status is required")
    @Column(nullable = false)
    private Boolean status;

    // ========== Contact Person Information ==========

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "contact_person_name")),
            @AttributeOverride(name = "email", column = @Column(name = "contact_person_email")),
            @AttributeOverride(name = "phoneNumber", column = @Column(name = "contact_person_phone"))
    })
    @NotNull(message = "Contact person details are required")
    private ContactPerson contactPerson;

    // ========== Address Information ==========

    private String address;

    private String city;

    private String state;

    private String pinCode;

    // ========== Legal Information ==========

    @Pattern(
            regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
            message = "Invalid GSTIN format"
    )
    private String gstNumber;

    @Pattern(
            regexp = "^$|^[A-Z]{5}[0-9]{4}[A-Z]{1}",
            message = "Invalid PAN format"
    )
    private String pan;

    // ========== Agreement Terms ==========

    @NotNull(message = "Agreement start date is required")
    private LocalDate agreementStartDate;

    @NotNull(message = "Agreement end date is required")
    private LocalDate agreementEndDate;

    @NotNull(message = "Credit period is required")
    private Integer creditPeriodDays;

    private String paymentTerms;

    private String remarks;


    public Vendor() {
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBankType() {
        return bankType;
    }

    public void setBankType(String bankType) {
        this.bankType = bankType;
    }

    public ContactPerson getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(ContactPerson contactPerson) {
        this.contactPerson = contactPerson;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public void setGstNumber(String gstNumber) {
        this.gstNumber = gstNumber;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public LocalDate getAgreementStartDate() {
        return agreementStartDate;
    }

    public void setAgreementStartDate(LocalDate agreementStartDate) {
        this.agreementStartDate = agreementStartDate;
    }

    public LocalDate getAgreementEndDate() {
        return agreementEndDate;
    }

    public void setAgreementEndDate(LocalDate agreementEndDate) {
        this.agreementEndDate = agreementEndDate;
    }

    public Integer getCreditPeriodDays() {
        return creditPeriodDays;
    }

    public void setCreditPeriodDays(Integer creditPeriodDays) {
        this.creditPeriodDays = creditPeriodDays;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }


    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

}