package com.project2.ism.Service.AdminServices;

import com.project2.ism.DTO.AdminDTO.AdminDTO;
import com.project2.ism.DTO.AdminDTO.CreateAdminRequest;
import com.project2.ism.DTO.AdminDTO.PermissionDTO;
import com.project2.ism.Model.Users.Permission;
import com.project2.ism.Model.Users.User;
import com.project2.ism.Repository.UserRepository;
import com.project2.ism.Service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.project2.ism.DTO.AdminDTO.PermissionDTO.fromEntity;

//@Service
//@Transactional
//public class AdminService {
//
//    private final UserRepository userRepository;
//    private final UserService userService;
//    private final PermissionService permissionService;
//
//    public AdminService(UserRepository userRepository, UserService userService, PermissionService permissionService) {
//        this.userRepository = userRepository;
//        this.userService = userService;
//        this.permissionService = permissionService;
//    }
//
//    /**
//     * Create admin - stores only the explicitly granted permissions
//     */
//    public User createAdmin(CreateAdminRequest request) {
//        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
//            throw new IllegalArgumentException("User already exists with email: " + request.getEmail());
//        }
//
//        // Validate all permissions exist
//        List<Permission> requestedPermissions = permissionService.getPermissionsByNames(request.getPermissionNames());
//        if (requestedPermissions.size() != request.getPermissionNames().size()) {
//            throw new IllegalArgumentException("Some permissions not found");
//        }
//
//        String plainPassword = (request.getPassword() != null && !request.getPassword().trim().isEmpty())
//                ? request.getPassword()
//                : null;
//
//        userService.createAndSendCredentials(request.getEmail(), "ADMIN", plainPassword);
//        User admin = userRepository.findByEmail(request.getEmail()).orElseThrow();
//
//        // Store only the requested permissions
//        admin.getPermissions().clear();
//        admin.getPermissions().addAll(requestedPermissions);
//
//        return userRepository.save(admin);
//    }
//
//    /**
//     * Update admin permissions - stores only the explicitly granted permissions
//     */
//    public User updateAdminPermissions(Long userId, List<String> permissionNames) {
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//
//        if (!"ADMIN".equals(user.getRole()) && !"SUPER_ADMIN".equals(user.getRole())) {
//            throw new IllegalArgumentException("User is not an admin");
//        }
//
//        if ("SUPER_ADMIN".equals(user.getRole())) {
//            throw new IllegalArgumentException("Cannot modify SUPER_ADMIN permissions");
//        }
//
//        List<Permission> requestedPermissions = permissionService.getPermissionsByNames(permissionNames);
//        if (requestedPermissions.size() != permissionNames.size()) {
//            throw new IllegalArgumentException("Some permissions not found");
//        }
//
//        // Store only the requested permissions
//        user.getPermissions().clear();
//        user.getPermissions().addAll(requestedPermissions);
//
//        return userRepository.save(user);
//    }
//
//    /**
//     * Delete admin
//     */
//    public void deleteAdmin(Long userId) {
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//
//        if ("SUPER_ADMIN".equals(user.getRole())) {
//            throw new IllegalArgumentException("Cannot delete SUPER_ADMIN");
//        }
//
//        if (!"ADMIN".equals(user.getRole())) {
//            throw new IllegalArgumentException("User is not an admin");
//        }
//
//        userRepository.delete(user);
//    }
//
//    public List<AdminDTO> getAllAdmins() {
//        List<User> admins = userRepository.findByRole("ADMIN");
//
//        return admins.stream()
//                .map(this::buildDisplayAdmin)
//                .collect(Collectors.toList());
//    }
//
//    private AdminDTO buildDisplayAdmin(User user) {
//        AdminDTO dto = new AdminDTO();
//        dto.setId(user.getId());
//        dto.setEmail(user.getEmail());
//        dto.setRole(user.getRole());
//        dto.setCreatedAt(user.getCreatedAt());
//        dto.setUpdatedAt(user.getUpdatedAt());
//
//        // build hierarchical view
//        Set<Permission> effectivePermissions = buildHierarchicalView(user.getPermissions());
//
//        dto.setPermissions(
//                effectivePermissions.stream()
//                        .filter(p -> p.getParent() == null) // only root nodes
//                        .map(p -> PermissionDTO.fromEntity(p, effectivePermissions))
//                        .collect(Collectors.toList())
//        );
//
//        return dto;
//    }
//
//    /**
//     * Ensures parents are included when child permissions are present
//     */
//    private Set<Permission> buildHierarchicalView(Set<Permission> storedPermissions) {
//        Set<Permission> hierarchicalView = new HashSet<>();
//
//        for (Permission permission : storedPermissions) {
//            hierarchicalView.add(permission);
//            Permission parent = permission.getParent();
//            while (parent != null) {
//                hierarchicalView.add(parent);
//                parent = parent.getParent();
//            }
//        }
//        return hierarchicalView;
//    }
//
//    /**
//     * Check if user has a specific permission (including inheritance)
//     * Use this for authorization checks
//     */
//    public boolean hasPermission(User user, String permissionName) {
//        // Check direct permissions first
//        boolean hasDirect = user.getPermissions().stream()
//                .anyMatch(p -> p.getName().equals(permissionName));
//
//        if (hasDirect) {
//            return true;
//        }
//
//        // Check if user has a parent permission that grants this
//        Permission requestedPermission = permissionService.findByName(permissionName);
//        if (requestedPermission == null) {
//            return false;
//        }
//
//        return user.getPermissions().stream()
//                .anyMatch(userPerm -> isParentOfPermission(userPerm, requestedPermission));
//    }
//
//    /**
//     * Check if a user permission is a parent of the requested permission
//     */
//    private boolean isParentOfPermission(Permission userPermission, Permission requestedPermission) {
//        Permission current = requestedPermission.getParent();
//        while (current != null) {
//            if (current.getId().equals(userPermission.getId())) {
//                return true;
//            }
//            current = current.getParent();
//        }
//        return false;
//    }
//
//    /**
//     * Get user's effective permissions (entities + inherited from parents)
//     * Use this for building PermissionDTO hierarchy
//     */
//    public Set<Permission> getEffectivePermissions(User user) {
//        Set<Permission> effectivePermissions = new HashSet<>();
//
//        for (Permission permission : user.getPermissions()) {
//            effectivePermissions.add(permission);
//            addChildPermissions(permission, effectivePermissions);
//        }
//
//        return effectivePermissions;
//    }
//
//    private void addChildPermissions(Permission parent, Set<Permission> collector) {
//        if (parent.getChildren() != null) {
//            for (Permission child : parent.getChildren()) {
//                collector.add(child);
//                addChildPermissions(child, collector);
//            }
//        }
//    }
//
//
//    /**
//     * Get current user's permissions for JWT/authentication
//     */
//    /**
//     * Get current user's effective permissions (hierarchical view)
//     */
//    public List<PermissionDTO> getCurrentUserPermissions(String userEmail) {
//        User user = userRepository.findByEmail(userEmail)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//
//        // calculate effective permissions (with inheritance/children)
//        Set<Permission> effectivePermissions = getEffectivePermissions(user);
//
//        // build hierarchical view but only keep permissions from effective set
//        return effectivePermissions.stream()
//                .filter(p -> p.getParent() == null) // only root-level
//                .map(p -> PermissionDTO.fromEntity(p, effectivePermissions)) // ✅ filtered hierarchy
//                .collect(Collectors.toList());
//    }
//
//
//}


@Service
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PermissionService permissionService;

    public AdminService(UserRepository userRepository, UserService userService, PermissionService permissionService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.permissionService = permissionService;
    }

    /**
     * Create admin - now returns AdminDTO instead of User entity
     */
    public AdminDTO createAdmin(CreateAdminRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("User already exists with email: " + request.getEmail());
        }

        // Validate all permissions exist
        List<Permission> requestedPermissions = permissionService.getPermissionsByNames(request.getPermissionNames());
        if (requestedPermissions.size() != request.getPermissionNames().size()) {
            throw new IllegalArgumentException("Some permissions not found");
        }

        String plainPassword = (request.getPassword() != null && !request.getPassword().trim().isEmpty())
                ? request.getPassword()
                : null;

        userService.createAndSendCredentials(request.getEmail(), "ADMIN", plainPassword);
        User admin = userRepository.findByEmail(request.getEmail()).orElseThrow();

        // Store only the requested permissions
        admin.getPermissions().clear();
        admin.getPermissions().addAll(requestedPermissions);

        User savedAdmin = userRepository.save(admin);

        // ✅ Convert to DTO before returning
        return buildDisplayAdmin(savedAdmin);
    }

    /**
     * Update admin permissions - now returns AdminDTO instead of User entity
     */
    public AdminDTO updateAdminPermissions(Long userId, List<String> permissionNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!"ADMIN".equals(user.getRole()) && !"SUPER_ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("User is not an admin");
        }

        if ("SUPER_ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("Cannot modify SUPER_ADMIN permissions");
        }

        List<Permission> requestedPermissions = permissionService.getPermissionsByNames(permissionNames);
        if (requestedPermissions.size() != permissionNames.size()) {
            throw new IllegalArgumentException("Some permissions not found");
        }

        // Store only the requested permissions
        user.getPermissions().clear();
        user.getPermissions().addAll(requestedPermissions);

        User savedUser = userRepository.save(user);

        // ✅ Convert to DTO before returning
        return buildDisplayAdmin(savedUser);
    }

    /**
     * Delete admin
     */
    public void deleteAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("SUPER_ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("Cannot delete SUPER_ADMIN");
        }

        if (!"ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("User is not an admin");
        }

        userRepository.delete(user);
    }

    public List<AdminDTO> getAllAdmins() {
        List<User> admins = userRepository.findByRole("ADMIN");
        return admins.stream()
                .map(this::buildDisplayAdmin)
                .collect(Collectors.toList());
    }

    // ✅ Make this method accessible for reuse
    private AdminDTO buildDisplayAdmin(User user) {
        AdminDTO dto = new AdminDTO();
        dto.setId((long) user.getId());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        // Build hierarchical view
        Set<Permission> effectivePermissions = buildHierarchicalView(user.getPermissions());

        dto.setPermissions(
                effectivePermissions.stream()
                        .filter(p -> p.getParent() == null) // only root nodes
                        .map(p -> fromEntity(p, effectivePermissions))
                        .collect(Collectors.toList())
        );

        return dto;
    }

    /**
     * Ensures parents are included when child permissions are present
     */
    private Set<Permission> buildHierarchicalView(Set<Permission> storedPermissions) {
        Set<Permission> hierarchicalView = new HashSet<>();

        for (Permission permission : storedPermissions) {
            hierarchicalView.add(permission);
            Permission parent = permission.getParent();
            while (parent != null) {
                hierarchicalView.add(parent);
                parent = parent.getParent();
            }
        }
        return hierarchicalView;
    }

    /**
     * Check if user has a specific permission (including inheritance)
     * Use this for authorization checks
     */
    public boolean hasPermission(User user, String permissionName) {
        // Check direct permissions first
        boolean hasDirect = user.getPermissions().stream()
                .anyMatch(p -> p.getName().equals(permissionName));

        if (hasDirect) {
            return true;
        }

        // Check if user has a parent permission that grants this
        Permission requestedPermission = permissionService.findByName(permissionName);
        if (requestedPermission == null) {
            return false;
        }

        return user.getPermissions().stream()
                .anyMatch(userPerm -> isParentOfPermission(userPerm, requestedPermission));
    }

    /**
     * Check if a user permission is a parent of the requested permission
     */
    private boolean isParentOfPermission(Permission userPermission, Permission requestedPermission) {
        Permission current = requestedPermission.getParent();
        while (current != null) {
            if (current.getId().equals(userPermission.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

//    /**
//     * Get user's effective permissions (entities + inherited from parents)
//     * Use this for building PermissionDTO hierarchy
//     */
//    public Set<Permission> getEffectivePermissions(User user) {
//        Set<Permission> effectivePermissions = new HashSet<>();
//
//        for (Permission permission : user.getPermissions()) {
//            effectivePermissions.add(permission);
//            addChildPermissions(permission, effectivePermissions);
//        }
//
//        return effectivePermissions;
//    }
//
//    private void addChildPermissions(Permission parent, Set<Permission> collector) {
//        if (parent.getChildren() != null) {
//            for (Permission child : parent.getChildren()) {
//                collector.add(child);
//                addChildPermissions(child, collector);
//            }
//        }
//    }
//
//    /**
//     * Get current user's effective permissions (hierarchical view)
//     */
//    public List<PermissionDTO> getCurrentUserPermissions(String userEmail) {
//        User user = userRepository.findByEmail(userEmail)
//                .orElseThrow(() -> new RuntimeException("User not found"));
//
//        // Calculate effective permissions (with inheritance/children)
//        Set<Permission> effectivePermissions = getEffectivePermissions(user);
//
//        // Build hierarchical view but only keep permissions from effective set
//        return effectivePermissions.stream()
//                .filter(p -> p.getParent() == null) // only root-level
//                .map(p -> PermissionDTO.fromEntity(p, effectivePermissions))
//                .collect(Collectors.toList());
//    }

    public Set<Permission> getEffectivePermissions(User user) {
        Set<Permission> effectivePermissions = new HashSet<>();
        System.out.println("User permissions from DB: " + user.getPermissions()); // debug

        for (Permission permission : user.getPermissions()) {
            System.out.println("Adding permission: " + permission.getName()); // debug
            effectivePermissions.add(permission);
            addChildPermissions(permission, effectivePermissions);
        }

        System.out.println("Effective permissions (after adding children): " +
                effectivePermissions.stream().map(Permission::getName).toList()); // debug

        return effectivePermissions;
    }

    private void addChildPermissions(Permission parent, Set<Permission> collector) {
        if (parent.getChildren() != null) {
            for (Permission child : parent.getChildren()) {
                System.out.println("Adding child permission: " + child.getName() + " of parent: " + parent.getName()); // debug
                collector.add(child);
                addChildPermissions(child, collector);
            }
        }
    }

    /**
     * Get current user's effective permissions (hierarchical view)
     */
    public List<PermissionDTO> getCurrentUserPermissions(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Permission> effectivePermissions = user.getPermissions(); // only explicit permissions

        // Map of parent ID -> PermissionDTO
        Map<Permission, PermissionDTO> parentMap = new HashMap<>();

        for (Permission perm : effectivePermissions) {
            Permission parent = perm.getParent();
            if (parent != null) {
                // Add parent if not yet in map
                parentMap.computeIfAbsent(parent, p -> {
                    PermissionDTO dto = new PermissionDTO();
                    dto.setName(p.getName());
                    dto.setDescription(p.getDescription());
                    return dto;
                });
                // Add this permission as child
                parentMap.get(parent).getChildren().add(fromEntity(perm, effectivePermissions));
            } else {
                // Root-level permission without parent
                parentMap.putIfAbsent(perm, fromEntity(perm, effectivePermissions));
            }
        }

        return new ArrayList<>(parentMap.values());
    }


}