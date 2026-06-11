package com.project2.ism.Controller;

import com.project2.ism.DTO.AdminDTO.PermissionDTO;
import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.Users.User;
import com.project2.ism.Service.AdminServices.AdminService;
import com.project2.ism.Service.JwtService;
import com.project2.ism.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    JwtService jwtService;

    @Autowired
    private UserService userService;
    @Autowired
    private AdminService adminService;

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody User user) {
        // ✅ Use updated login method that checks password expiry
        UserService.LoginResult loginResult = userService.loginUser(user.getEmail(), user.getPassword());

        return switch (loginResult.getStatus()) {
            case SUCCESS -> {
                User loggedInUser = loginResult.getUser();
                String token = jwtService.generateToken(loggedInUser.getEmail(), loggedInUser.getRole());

                Map<String, Object> response = new HashMap<>();
                response.put("email", loggedInUser.getEmail());
                response.put("role", loggedInUser.getRole());
                response.put("token", token);
                response.put("message", "Login successful");

                if (loggedInUser.isFirstLogin()) {
                    response.put("firstLogin", true);
                }

                // ✅ Check password expiry warning
                if (loggedInUser.getPasswordExpiryDate() != null) {
                    LocalDate today = LocalDate.now();
                    LocalDate expiryDate = loggedInUser.getPasswordExpiryDate().toLocalDate();
                    long daysLeft = ChronoUnit.DAYS.between(today, expiryDate);

                    if (daysLeft > 0 && daysLeft <= 15) {
                        response.put("passwordExpiryWarning",
                                "Your password will expire in " + daysLeft + " day" + (daysLeft > 1 ? "s" : "") +
                                        ". Please change it before expiry.");
                        response.put("daysLeftForPasswordExpiry", daysLeft);
                    }
                }

                // Only admins need hierarchical permissions
                if ("ADMIN".equalsIgnoreCase(loggedInUser.getRole())
                        || "SUPER_ADMIN".equalsIgnoreCase(loggedInUser.getRole())) {
                    List<PermissionDTO> permissions =
                            adminService.getCurrentUserPermissions(loggedInUser.getEmail());
                    response.put("permissions", permissions);
                }

                yield ResponseEntity.ok(response);
            }
            case PASSWORD_EXPIRED -> {
                // ✅ Return special response for expired password
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Password has expired");
                response.put("passwordExpired", true);
                response.put("email", loginResult.getUser().getEmail());
                response.put("message", "Your password has expired. Please reset your password.");

                yield ResponseEntity.status(428).body(response);
            }
            case INVALID_CREDENTIALS -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid Credentials"));
        };
    }


    @PostMapping("/signup")
    public ResponseEntity<?> signUser(@RequestBody User user) {
        Optional<User> result = userService.signUpUser(user);

        if (result.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "message", "User registered successfully",
                    "email", result.get().getEmail()
            ));
        } else {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "User already exists"));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        try {
            userService.generateResetToken(email);
            return ResponseEntity.ok("Password reset link sent to your email.");
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found with this email.");
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Something went wrong.");
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        try {
            UserService.ResetStatus status = userService.resetPassword(token, newPassword);

            return switch (status) {
                case SUCCESS -> ResponseEntity.ok("Password reset successful.");
                case EXPIRED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Reset token expired.");
                case INVALID -> ResponseEntity.badRequest().body("Invalid reset token.");
            };
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Something went wrong.");
        }
    }

//    @PostMapping("/change-password")
//    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
//        try {
//            String email = request.get("email");
//            String currentPassword = request.get("currentPassword");
//            String newPassword = request.get("newPassword");
//
//            // Validate input
//            if (email == null || email.isBlank() ||
//                    currentPassword == null || currentPassword.isBlank() ||
//                    newPassword == null || newPassword.isBlank()) {
//                return ResponseEntity.badRequest()
//                        .body(Map.of("error", "All fields are required"));
//            }
//
//            if (newPassword.length() < 8) {
//                return ResponseEntity.badRequest()
//                        .body(Map.of("error", "New password must be at least 8 characters "));
//            }
//
//            UserService.ChangePasswordStatus status =
//                    userService.changePassword(email, currentPassword, newPassword);
//
//            return switch (status) {
//                case SUCCESS -> ResponseEntity.ok(Map.of("message", "Password changed successfully"));
//                case USER_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
//                        .body(Map.of("error", "User not found"));
//                case INVALID_CURRENT_PASSWORD -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                        .body(Map.of("error", "Current password is incorrect"));
//                case SAME_PASSWORD -> ResponseEntity.badRequest()
//                        .body(Map.of("error", "New password must be different from current password"));
//            };
//        } catch (Exception ex) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(Map.of("error", "Something went wrong"));
//        }
//    }


    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String currentPassword = request.get("currentPassword");
            String newPassword = request.get("newPassword");
            Boolean isFirstLogin = request.get("isFirstLogin") != null
                    ? Boolean.parseBoolean(request.get("isFirstLogin"))
                    : false;

            // Validate email and new password
            if (email == null || email.isBlank() || newPassword == null || newPassword.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email and new password are required"));
            }

            // For non-first-login, current password is required
            if (!isFirstLogin && (currentPassword == null || currentPassword.isBlank())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Current password is required"));
            }

            if (newPassword.length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "New password must be at least 8 characters"));
            }

            UserService.ChangePasswordStatus status = userService.changePassword(
                    email,
                    currentPassword,
                    newPassword,
                    isFirstLogin
            );

            return switch (status) {
                case SUCCESS -> ResponseEntity.ok(Map.of(
                        "message", "Password changed successfully",
                        "firstLogin", false
                ));
                case USER_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "User not found"));
                case INVALID_CURRENT_PASSWORD -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Current password is incorrect"));
                case SAME_PASSWORD -> ResponseEntity.badRequest()
                        .body(Map.of("error", "New password must be different from current password"));
                case NOT_FIRST_LOGIN -> ResponseEntity.badRequest()
                        .body(Map.of("error", "This is not a first-time login. Current password is required"));
                case PASSWORD_NOT_EXPIRED -> ResponseEntity.badRequest()
                        .body(Map.of("error", "Password has not expired. Please use normal password change with current password"));
            };
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Something went wrong"));
        }
    }

    @PostMapping("/create-system-user")
    public ResponseEntity<?> createSystemUser(@RequestBody Map<String, String> request) {

        String email = request.get("email");
        String password = request.get("password");
        String role = request.get("role");

        if (email == null || password == null || role == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "email, password, and role are required"));
        }

        try {
            User user = userService.createSystemUser(email, password, role);

            return ResponseEntity.ok(Map.of(
                    "message", "System user created successfully",
                    "email", user.getEmail(),
                    "role", user.getRole()
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", ex.getMessage()));
        }
    }


}