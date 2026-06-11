package com.project2.ism.Repository;

import com.project2.ism.Model.Users.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {


    Optional<User> findByEmail(String email);
    Optional<User> findByResetToken(String resetToken);

    List<User> findByEmailIn(List<String> emails);

    // NEW: Find users by role
    List<User> findByRole(String role);

//    @Query("SELECT u.email FROM User u WHERE u.role = :role")
//    List<String> findEmailByRole(@Param("role") String role);

    // Add this method to your UserRepository interface

    @Query("SELECT u FROM User u JOIN u.permissions p WHERE p.id = :permissionId")
    List<User> findUsersWithPermission(@Param("permissionId") Long permissionId);

    // Alternative approach using @Query with JPQL
    @Query("SELECT DISTINCT u FROM User u WHERE EXISTS (SELECT 1 FROM u.permissions p WHERE p.id = :permissionId)")
    List<User> findUsersWithPermissionAlternative(@Param("permissionId") Long permissionId);

    // If you prefer native SQL (add this to UserRepository)
    @Query(value = "SELECT u.* FROM users u " +
            "INNER JOIN user_permissions up ON u.id = up.user_id " +
            "WHERE up.permission_id = :permissionId",
            nativeQuery = true)
    List<User> findUsersWithPermissionNative(@Param("permissionId") Long permissionId);
}
