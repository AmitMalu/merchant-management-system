package com.project2.ism.Service.AdminServices;

import com.project2.ism.DTO.AdminDTO.CreatePermissionRequest;
import com.project2.ism.Model.Users.Permission;
import com.project2.ism.Model.Users.User;
import com.project2.ism.Repository.PermissionRepository;
import com.project2.ism.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PermissionService {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Get all permissions (flat list)
     */
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * Get all permissions in hierarchical structure (root permissions with their children loaded)
     */
    public List<Permission> getAllPermissionsHierarchical() {
        return getRootPermissions(); // Children will be loaded via @JsonManagedReference
    }

    /**
     * Find permission by name - needed for authorization checks
     */
    public Permission findByName(String name) {
        return permissionRepository.findByName(name).orElse(null);
    }

    /**
     * Get permissions by list of names - needed for user permission assignment
     */
    public List<Permission> getPermissionsByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }
        return permissionRepository.findByNameIn(names);
    }

    /**
     * Create new permission with proper hierarchy handling
     */
    public Permission createPermission(CreatePermissionRequest request) {
        // Check if permission already exists
        if (permissionRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException("Permission already exists: " + request.getName());
        }

        Permission permission = new Permission(request.getName(), request.getDescription());

        // Handle parent relationship
        if (request.getParent_id() != null) {
            Permission parent = permissionRepository.findById(request.getParent_id())
                    .orElseThrow(() -> new RuntimeException("Parent permission not found"));
            permission.setParent(parent);
            parent.getChildren().add(permission);
        }

        return permissionRepository.save(permission);
    }

    /**
     * Update permission with hierarchy management
     */
    public Permission updatePermission(Long id, CreatePermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Permission not found"));

        // Check if new name conflicts with existing permission (excluding current one)
        Optional<Permission> existing = permissionRepository.findByName(request.getName());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new IllegalArgumentException("Permission name already exists: " + request.getName());
        }

        // Update basic fields
        permission.setName(request.getName());
        permission.setDescription(request.getDescription());

        // Handle parent relationship changes
        if (request.getParent_id() != null) {
            Permission newParent = permissionRepository.findById(request.getParent_id())
                    .orElseThrow(() -> new RuntimeException("Parent permission not found"));

            // Prevent setting a child as its own parent or creating circular references
            if (isCircularReference(permission, newParent)) {
                throw new IllegalArgumentException("Cannot create circular reference in permission hierarchy");
            }

            // Remove from old parent if exists
            if (permission.getParent() != null) {
                permission.getParent().getChildren().remove(permission);
            }

            // Set new parent
            permission.setParent(newParent);
            newParent.getChildren().add(permission);
        } else {
            // Remove from parent if setting to null
            if (permission.getParent() != null) {
                permission.getParent().getChildren().remove(permission);
                permission.setParent(null);
            }
        }

        return permissionRepository.save(permission);
    }

    /**
     * Delete permission with proper validation
     */
    public void deletePermission(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Permission not found"));

        // Check if permission has children - prevent deletion of parent permissions
        if (!permission.getChildren().isEmpty()) {
            String childNames = permission.getChildren().stream()
                    .map(Permission::getName)
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Cannot delete permission with children: " + childNames +
                    ". Delete or reassign children first.");
        }

        // FIXED: Check if permission is being used by any user (using clean permissions field)
        List<User> usersUsingPermission = userRepository.findUsersWithPermission(id);

        if (!usersUsingPermission.isEmpty()) {
            String userEmails = usersUsingPermission.stream()
                    .map(User::getEmail)
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Cannot delete permission. It's being used by users: " + userEmails);
        }

        // Remove from parent's children list
        if (permission.getParent() != null) {
            permission.getParent().getChildren().remove(permission);
        }

        permissionRepository.delete(permission);
    }

    /**
     * Get root permissions (those without parents)
     */
    public List<Permission> getRootPermissions() {
        return permissionRepository.findAll().stream()
                .filter(permission -> permission.getParent() == null)
                .collect(Collectors.toList());
    }

    /**
     * Get direct children of a parent permission
     */
    public List<Permission> getChildrenByParentId(Long parentId) {
        Permission parent = permissionRepository.findById(parentId)
                .orElseThrow(() -> new RuntimeException("Parent permission not found"));
        return parent.getChildren();
    }

    /**
     * Check if user has any permissions that would be affected by permission changes
     * Used for validation before permission modifications
     */
    public boolean isPermissionInUse(Long permissionId) {
        List<User> users = userRepository.findUsersWithPermission(permissionId);
        return !users.isEmpty();
    }

    /**
     * Get all descendants of a permission (children, grandchildren, etc.)
     */
    public List<Permission> getAllDescendants(Permission permission) {
        List<Permission> descendants = new ArrayList<>();
        collectDescendants(permission, descendants);
        return descendants;
    }

    /**
     * Get all ancestors of a permission (parent, grandparent, etc.)
     */
    public List<Permission> getAllAncestors(Permission permission) {
        List<Permission> ancestors = new ArrayList<>();
        Permission current = permission.getParent();
        while (current != null) {
            ancestors.add(current);
            current = current.getParent();
        }
        return ancestors;
    }

    /**
     * Check if permission A is an ancestor of permission B
     */
    public boolean isAncestorOf(Permission ancestor, Permission descendant) {
        Permission current = descendant.getParent();
        while (current != null) {
            if (current.getId().equals(ancestor.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Check if permission A is a descendant of permission B
     */
    public boolean isDescendantOf(Permission descendant, Permission ancestor) {
        return isAncestorOf(ancestor, descendant);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Helper method to check for circular references
     */
    private boolean isCircularReference(Permission child, Permission potentialParent) {
        Permission current = potentialParent;
        while (current != null) {
            if (current.getId().equals(child.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * Recursively collect all descendants of a permission
     */
    private void collectDescendants(Permission permission, List<Permission> descendants) {
        if (permission.getChildren() != null) {
            for (Permission child : permission.getChildren()) {
                descendants.add(child);
                collectDescendants(child, descendants);
            }
        }
    }

    /**
     * Create permission if it doesn't exist - used for initialization
     */
    private Permission createPermissionIfNotExists(String name, String description, Long parentId) {
        Optional<Permission> existing = permissionRepository.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }

        Permission permission = new Permission(name, description);
        if (parentId != null) {
            Permission parent = permissionRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("Parent permission not found"));
            permission.setParent(parent);
            parent.getChildren().add(permission);
        }

        return permissionRepository.save(permission);
    }

    // ==================== INITIALIZATION METHODS ====================

    /**
     * Initialize predefined permissions (call this on startup if needed)
     * Currently commented as mentioned it was already used, kept for future reference
     */
    /*
    public void initializePredefinedPermissions() {
        if (permissionRepository.count() > 0) {
            return; // Already initialized
        }

        // Create Vendors and its children
        Permission vendors = createPermissionIfNotExists("Vendors", "Manage vendors, add products, vendor rates", null);
        createPermissionIfNotExists("Vendor List", "View and manage vendor list", vendors.getId());
        createPermissionIfNotExists("Vendor Rates", "Manage vendor pricing rates", vendors.getId());
        createPermissionIfNotExists("Product List", "View vendor products", vendors.getId());

        // Create Inventory and its children
        Permission inventory = createPermissionIfNotExists("Inventory", "Price scheme of products, inventory management - inward/outward/return", null);
        createPermissionIfNotExists("Pricing Scheme", "Manage product pricing schemes", inventory.getId());
        createPermissionIfNotExists("Product Scheme Assign", "Assign schemes to products", inventory.getId());

        // Create Customers and its children
        Permission customers = createPermissionIfNotExists("Customers", "Customer onboarding, approval, distribution", null);
        createPermissionIfNotExists("Customer List", "View customer list", customers.getId());
        createPermissionIfNotExists("Onboard Customer", "Add new customers", customers.getId());
        createPermissionIfNotExists("Merchant Approval", "Approve merchant applications", customers.getId());
        createPermissionIfNotExists("Products Distribution", "Manage product distribution", customers.getId());

        // Create Other and its children
        Permission other = createPermissionIfNotExists("Other", "Miscellaneous operations", null);
        createPermissionIfNotExists("File Upload", "Upload files", other.getId());
        createPermissionIfNotExists("Charge Calculation", "Calculate charges", other.getId());
        createPermissionIfNotExists("Batch Status", "View batch status", other.getId());

        // Create Reports and its children
        Permission reports = createPermissionIfNotExists("Reports", "Fetch and export reports", null);
        createPermissionIfNotExists("Franchise Reports", "Generate franchise reports", reports.getId());
    }
    */
}