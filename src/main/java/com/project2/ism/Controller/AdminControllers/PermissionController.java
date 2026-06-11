package com.project2.ism.Controller.AdminControllers;

import com.project2.ism.DTO.AdminDTO.CreatePermissionRequest;
import com.project2.ism.Model.Users.Permission;
import com.project2.ism.Service.AdminServices.PermissionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/permissions")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

//    @GetMapping
//    public ResponseEntity<List<Permission>> getAllPermissions() {
//        return ResponseEntity.ok(permissionService.getAllPermissions());
//    }
//
//    @PostMapping
//    public ResponseEntity<Permission> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
//        Permission permission = permissionService.createPermission(request);
//        return ResponseEntity.status(HttpStatus.CREATED).body(permission);
//    }
//
//    @PutMapping("/{id}")
//    public ResponseEntity<Permission> updatePermission(
//            @PathVariable Long id,
//            @Valid @RequestBody CreatePermissionRequest request) {
//        Permission permission = permissionService.updatePermission(id, request);
//        return ResponseEntity.ok(permission);
//    }
//
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
//        permissionService.deletePermission(id);
//        return ResponseEntity.noContent().build();
//    }

    @GetMapping
    public ResponseEntity<List<Permission>> getAllPermissions() {
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }

    @GetMapping("/hierarchical")
    public ResponseEntity<List<Permission>> getAllPermissionsHierarchical() {
        return ResponseEntity.ok(permissionService.getAllPermissionsHierarchical());
    }

    @GetMapping("/roots")
    public ResponseEntity<List<Permission>> getRootPermissions() {
        return ResponseEntity.ok(permissionService.getRootPermissions());
    }

    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<Permission>> getChildrenByParentId(@PathVariable Long parentId) {
        return ResponseEntity.ok(permissionService.getChildrenByParentId(parentId));
    }

    @PostMapping
    public ResponseEntity<Permission> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        Permission permission = permissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(permission);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Permission> updatePermission(
            @PathVariable Long id,
            @Valid @RequestBody CreatePermissionRequest request) {
        Permission permission = permissionService.updatePermission(id, request);
        return ResponseEntity.ok(permission);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.noContent().build();
    }

//    @PostMapping("/initialize")
//    public ResponseEntity<String> initializePredefinedPermissions() {
//        permissionService.initializePredefinedPermissions();
//        return ResponseEntity.ok("Predefined permissions initialized successfully");
//    }
}