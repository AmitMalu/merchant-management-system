package com.project2.ism.Model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Embeddable
public class ContactPerson {

    @NotBlank(message = "name is required")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    private String name;

    @NotBlank(message = "phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone number must be 10 digits starting with 6-9")
    private String phoneNumber;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone number must be 10 digits starting with 6-9")
    private String alternatePhoneNum;

    @NotBlank
    @Email(message = "Invalid email format")
    private String email;

    @Pattern(
            regexp = "^[0-9]{3,5}[0-9]{6,8}$",
            message = "Landline number must contain STD code followed by 6–8 digits"
    )
    private String LandlineNumber;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAlternatePhoneNum() {
        return alternatePhoneNum;
    }

    public void setAlternatePhoneNum(String alternatePhonenum) {
        this.alternatePhoneNum = alternatePhonenum;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLandlineNumber() {
        return LandlineNumber;
    }

    public void setLandlineNumber(String landlineNumber) {
        LandlineNumber = landlineNumber;
    }
}
