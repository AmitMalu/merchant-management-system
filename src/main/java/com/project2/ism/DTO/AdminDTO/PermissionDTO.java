package com.project2.ism.DTO.AdminDTO;

import com.project2.ism.Model.Users.Permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PermissionDTO {
    private String name;
    private String description;
    private List<PermissionDTO> children = new ArrayList<>();

    public static PermissionDTO fromEntity(Permission permission, Set<Permission> effectivePermissions) {
        PermissionDTO dto = new PermissionDTO();
        dto.setName(permission.getName());
        dto.setDescription(permission.getDescription());

        if (permission.getChildren() != null) {
            for (Permission child : permission.getChildren()) {
                if (effectivePermissions.contains(child)) { // ✅ only add if effective
                    dto.getChildren().add(fromEntity(child, effectivePermissions));
                }
            }
        }

        return dto;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PermissionDTO> getChildren() {
        return children;
    }

    public void setChildren(List<PermissionDTO> children) {
        this.children = children;
    }
}
