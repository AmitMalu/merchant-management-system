package com.project2.ism.DTO.AdminDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreateAdminRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email invalid format")
    private String email;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password; // Optional - if null, auto-generates and sends via email

    private List<String> permissionNames;

    // Constructors
    public CreateAdminRequest() {}

    public CreateAdminRequest(String email, String password, List<String> permissionNames) {
        this.email = email;
        this.password = password;
        this.permissionNames = permissionNames;
    }

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getPermissionNames() {
        return permissionNames;
    }

    public void setPermissionNames(List<String> permissionNames) {
        this.permissionNames = permissionNames;
    }
}