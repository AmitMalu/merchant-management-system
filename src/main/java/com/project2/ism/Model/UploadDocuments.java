package com.project2.ism.Model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;

@Embeddable
public class UploadDocuments {

    @NotBlank(message = "pan proof required")
    private String panProof;

    @NotBlank(message = "adhar proof required")
    private String adharProof;

    private String gstCertificateProof;

    @NotBlank(message = "address proof required")
    private String addressProof;

    @NotBlank(message = "bank account proof required")
    private String bankAccountProof;

    private String other1;

    private String other2;

    private String other3;

    public UploadDocuments() {
    }


    public String getPanProof() {
        return panProof;
    }

    public void setPanProof(String panProof) {
        this.panProof = panProof;
    }

    public String getAdharProof() {
        return adharProof;
    }

    public void setAdharProof(String adharProof) {
        this.adharProof = adharProof;
    }

    public String getGstCertificateProof() {
        return gstCertificateProof;
    }

    public void setGstCertificateProof(String gstCertificateProof) {
        this.gstCertificateProof = gstCertificateProof;
    }

    public String getBankAccountProof() {
        return bankAccountProof;
    }

    public void setBankAccountProof(String bankAccountProof) {
        this.bankAccountProof = bankAccountProof;
    }

    public String getAddressProof() {
        return addressProof;
    }

    public void setAddressProof(String addressProof) {
        this.addressProof = addressProof;
    }

    public String getOther1() {
        return other1;
    }

    public void setOther1(String other1) {
        this.other1 = other1;
    }

    public String getOther2() {
        return other2;
    }

    public void setOther2(String other2) {
        this.other2 = other2;
    }

    public String getOther3() {
        return other3;
    }

    public void setOther3(String other3) {
        this.other3 = other3;
    }
}
