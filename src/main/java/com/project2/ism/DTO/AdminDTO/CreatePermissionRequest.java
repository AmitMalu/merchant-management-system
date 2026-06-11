package com.project2.ism.DTO.AdminDTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatePermissionRequest {
    @NotBlank(message = "Permission name is required")
    private String name;

    private String description;

    private Long parent_id;

    // Constructors, getters, setters
    public CreatePermissionRequest() {}
    public CreatePermissionRequest(String name, String description, Long parent_id) {
        this.name = name;
        this.description = description;
        this.parent_id = parent_id;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getParent_id() {
        return parent_id;
    }

    public void setParent_id(Long parent_id) {
        this.parent_id = parent_id;
    }
}

