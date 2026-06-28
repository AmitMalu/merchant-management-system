package com.project2.ism.Service;

import com.project2.ism.Controller.UserController;
import com.project2.ism.Exception.ResourceNotFoundException;
import com.project2.ism.Model.Users.User;
import com.project2.ism.Repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    public static final String TOKEN = UUID.randomUUID().toString();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Value("${security.password.expiry-days:90}")  // Inject here in the service
    private int passwordExpiryDays;

    public UserService(UserRepository userRepository, MailService mailService) {
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // Add new enum for login status
    public enum LoginStatus {
        SUCCESS,
        INVALID_CREDENTIALS,
        PASSWORD_EXPIRED
    }

    // Updated login method to check password expiry
    public LoginResult loginUser(String email, String password) {
        Optional<User> userFromDb = userRepository.findByEmail(email);
        if (userFromDb.isEmpty()) {
            return new LoginResult(LoginStatus.INVALID_CREDENTIALS, null);
        }

        User user = userFromDb.get();

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return new LoginResult(LoginStatus.INVALID_CREDENTIALS, null);
        }

        // Check if password has expired
        // Skip password-expiry check for RAZORPAY user
        if (!"RAZORPAY".equalsIgnoreCase(user.getRole())
                && user.isPasswordExpired()) {
            return new LoginResult(LoginStatus.PASSWORD_EXPIRED, user);
        }

        return new LoginResult(LoginStatus.SUCCESS, user);
    }

    // Helper class to return both status and user
    public static class LoginResult {
        private final LoginStatus status;
        private final User user;

        public LoginResult(LoginStatus status, User user) {
            this.status = status;
            this.user = user;
        }

        public LoginStatus getStatus() {
            return status;
        }

        public User getUser() {
            return user;
        }
    }

    public Optional<User> signUpUser(User user) {
        Optional<User> existUser = userRepository.findByEmail(user.getEmail());

        if (existUser.isPresent()) {
            return Optional.empty();
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Set password expiry for new users
        user.setPasswordLastChangedAt(LocalDateTime.now());
        user.setPasswordExpiryDate(LocalDateTime.now().plusDays(passwordExpiryDays));

        User savedUser = userRepository.save(user);
        return Optional.of(savedUser);
    }

    @Transactional
    public User createSystemUser(String email, String rawPassword, String role) {

        // Check if user already exists
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            throw new RuntimeException("User with email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setPassword(passwordEncoder.encode(rawPassword));


        // System users never expire
        user.setFirstLogin(false);
        user.setPasswordExpiryDate(null);
        user.setPasswordLastChangedAt(LocalDateTime.now());

        return userRepository.save(user);
    }


    @Transactional
    public void createAndSendCredentials(String email, String role, String plainPassword) {
        String rawPassword = (plainPassword != null && !plainPassword.isBlank())
                ? plainPassword
                : generateRandomPassword(10);

        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setPassword(passwordEncoder.encode(rawPassword));

        // Set password expiry for admin-created users
        user.setPasswordLastChangedAt(LocalDateTime.now());
        user.setPasswordExpiryDate(LocalDateTime.now().plusDays(passwordExpiryDays));

        userRepository.save(user);

        //String loginUrl = "https://portal.ashwamlearning.co.in:5173/login";
        String loginUrl = "https://portal.utsabpay.com/login";
        String htmlMessage = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;">
                    <div style="background:#1a237e;padding:24px;text-align:center;">
                        <h2 style="color:#ffffff;margin:0;font-size:22px;">Welcome to UtsabPay</h2>
                    </div>
                    <div style="padding:28px 32px;background:#ffffff;">
                        <p style="color:#333;font-size:15px;">Hello,</p>
                        <p style="color:#555;font-size:14px;">Your account has been created successfully. Below are your login credentials:</p>
                        <table style="width:100%%;margin:20px 0;border-collapse:collapse;">
                            <tr>
                                <td style="padding:10px;background:#f5f5f5;border:1px solid #e0e0e0;font-weight:bold;width:35%%;">Email</td>
                                <td style="padding:10px;border:1px solid #e0e0e0;">%s</td>
                            </tr>
                            <tr>
                                <td style="padding:10px;background:#f5f5f5;border:1px solid #e0e0e0;font-weight:bold;">Password</td>
                                <td style="padding:10px;border:1px solid #e0e0e0;font-family:monospace;">%s</td>
                            </tr>
                        </table>
                        <p style="color:#e65100;font-size:13px;">&#9888; Your password will expire in <strong>%d days</strong>. Please change it after your first login.</p>
                        <div style="text-align:center;margin:24px 0;">
                            <a href="%s" style="background:#1a237e;color:#fff;padding:12px 28px;border-radius:6px;text-decoration:none;font-size:15px;display:inline-block;">Login to UtsabPay</a>
                        </div>
                    </div>
                    <div style="background:#f5f5f5;padding:14px;text-align:center;">
                        <p style="color:#888;font-size:12px;margin:0;">This is an automated email. Please do not reply to this message.</p>
                        <p style="color:#888;font-size:12px;margin:4px 0 0;">&copy; UtsabPay. All rights reserved.</p>
                    </div>
                </div>
                """.formatted(email, rawPassword, passwordExpiryDays, loginUrl);

        mailService.sendHtmlEmail(
                List.of(email),
                "Your UtsabPay Account Credentials",
                htmlMessage
        );
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public void generateResetToken(String email) {

        log.info("Generating reset token for email: {}", email);

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {

            User user = userOptional.get();

            log.info("User found. UserId: {}, Email: {}", user.getId(), user.getEmail());

            String token = UUID.randomUUID().toString();

            log.debug("Generated reset token for user {}: {}", user.getId(), token);

            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10));

            userRepository.save(user);

            log.info("Reset token saved successfully for userId: {}", user.getId());

            String resetLink = "https://portal.utsabpay.com/reset-password?token=" + token;

            log.debug("Reset link generated: {}", resetLink);

            String htmlMessage = """
                    <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;">
                        <div style="background:#b71c1c;padding:24px;text-align:center;">
                            <h2 style="color:#ffffff;margin:0;font-size:22px;">Password Reset Request</h2>
                        </div>
                        <div style="padding:28px 32px;background:#ffffff;">
                            <p style="color:#333;font-size:15px;">Hello,</p>
                            <p style="color:#555;font-size:14px;">We received a request to reset the password for your UtsabPay account associated with <strong>%s</strong>.</p>
                            <p style="color:#555;font-size:14px;">Click the button below to reset your password. This link is valid for <strong>10 minutes</strong>.</p>
                            <div style="text-align:center;margin:24px 0;">
                                <a href="%s" style="background:#b71c1c;color:#fff;padding:12px 28px;border-radius:6px;text-decoration:none;font-size:15px;display:inline-block;">Reset My Password</a>
                            </div>
                            <p style="color:#888;font-size:13px;">If you did not request a password reset, you can safely ignore this email. Your account remains secure.</p>
                        </div>
                        <div style="background:#f5f5f5;padding:14px;text-align:center;">
                            <p style="color:#888;font-size:12px;margin:0;">This is an automated email. Please do not reply to this message.</p>
                            <p style="color:#888;font-size:12px;margin:4px 0 0;">&copy; UtsabPay. All rights reserved.</p>
                        </div>
                    </div>
                    """.formatted(email, resetLink);

            log.info("Sending password reset email to {}", email);

            mailService.sendHtmlEmail(
                    List.of(email),
                    "UtsabPay - Password Reset Request",
                    htmlMessage
            );

            log.info("Password reset email request completed for {}", email);

        } else {

            log.warn("No user found with email: {}", email);

            throw new ResourceNotFoundException("No user found with this email");
        }
    }

//    public void generateResetToken(String email) {
//        Optional<User> userOptional = userRepository.findByEmail(email);
//        if (userOptional.isPresent()) {
//            User user = userOptional.get();
//            String token = UUID.randomUUID().toString();
//
//            user.setResetToken(TOKEN);
//            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10));
//            userRepository.save(user);
//
//            String resetLink = "https://portal.utsabpay.com/reset-password?token=" + TOKEN;
//            String htmlMessage = """
//                    <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;">
//                        <div style="background:#b71c1c;padding:24px;text-align:center;">
//                            <h2 style="color:#ffffff;margin:0;font-size:22px;">Password Reset Request</h2>
//                        </div>
//                        <div style="padding:28px 32px;background:#ffffff;">
//                            <p style="color:#333;font-size:15px;">Hello,</p>
//                            <p style="color:#555;font-size:14px;">We received a request to reset the password for your UtsabPay account associated with <strong>%s</strong>.</p>
//                            <p style="color:#555;font-size:14px;">Click the button below to reset your password. This link is valid for <strong>10 minutes</strong>.</p>
//                            <div style="text-align:center;margin:24px 0;">
//                                <a href="%s" style="background:#b71c1c;color:#fff;padding:12px 28px;border-radius:6px;text-decoration:none;font-size:15px;display:inline-block;">Reset My Password</a>
//                            </div>
//                            <p style="color:#888;font-size:13px;">If you did not request a password reset, you can safely ignore this email. Your account remains secure.</p>
//                        </div>
//                        <div style="background:#f5f5f5;padding:14px;text-align:center;">
//                            <p style="color:#888;font-size:12px;margin:0;">This is an automated email. Please do not reply to this message.</p>
//                            <p style="color:#888;font-size:12px;margin:4px 0 0;">&copy; UtsabPay. All rights reserved.</p>
//                        </div>
//                    </div>
//                    """.formatted(email, resetLink);
//            mailService.sendHtmlEmail(
//                    List.of(email),
//                    "UtsabPay - Password Reset Request",
//                    htmlMessage
//            );
//        } else {
//            throw new ResourceNotFoundException("No user found with this email");
//        }
//    }

    public enum ResetStatus {
        SUCCESS,
        EXPIRED,
        INVALID
    }

    @Transactional
    public ResetStatus resetPassword(String token, String newPassword) {

        log.info("Starting password reset for token: {}", token);

        Optional<User> userOptional = userRepository.findByResetToken(token);

        if (userOptional.isEmpty()) {
            log.warn("Invalid reset token: {}", token);
            return ResetStatus.INVALID;
        }

        User user = userOptional.get();

        log.info("User found with email: {}", user.getEmail());

        if (user.getResetTokenExpiry() == null) {
            log.warn("Reset token expiry is null for user: {}", user.getEmail());
            return ResetStatus.EXPIRED;
        }

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            log.warn("Reset token expired for user: {}", user.getEmail());
            return ResetStatus.EXPIRED;
        }

        try {

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setResetToken(null);
            user.setResetTokenExpiry(null);

            user.setPasswordLastChangedAt(LocalDateTime.now());
            user.setPasswordExpiryDate(
                    LocalDateTime.now().plusDays(passwordExpiryDays));

            userRepository.save(user);

            log.info("Password reset successful for user: {}", user.getEmail());

            return ResetStatus.SUCCESS;

        } catch (Exception ex) {

            log.error("Failed to reset password for user: {}", user.getEmail(), ex);

            throw ex;
        }
    }

    // Add new enum values
    public enum ChangePasswordStatus {
        SUCCESS,
        USER_NOT_FOUND,
        INVALID_CURRENT_PASSWORD,
        SAME_PASSWORD,
        NOT_FIRST_LOGIN,
        PASSWORD_NOT_EXPIRED  // New: for when isFirstLogin=true but password isn't actually expired
    }

    @Transactional
    public ChangePasswordStatus changePassword(String email, String currentPassword, String newPassword, boolean isFirstLogin) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return ChangePasswordStatus.USER_NOT_FOUND;
        }

        User user = userOptional.get();

        // ✅ Determine the actual scenario
        boolean isActualFirstLogin = user.isFirstLogin();
        boolean isPasswordExpired = user.isPasswordExpired();

        // ✅ SCENARIO 1: Actual First Login (admin-created account)
        if (isFirstLogin && isActualFirstLogin) {
            // No current password needed
            // Check if new password is same as temporary password
            if (passwordEncoder.matches(newPassword, user.getPassword())) {
                return ChangePasswordStatus.SAME_PASSWORD;
            }

            // Update password
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordLastChangedAt(LocalDateTime.now());
            user.setPasswordExpiryDate(LocalDateTime.now().plusDays(passwordExpiryDays));
            user.setFirstLogin(false);

            userRepository.save(user);
            return ChangePasswordStatus.SUCCESS;
        }

        // ✅ SCENARIO 2: Password Expired (forced reset, no current password needed)
        if (isFirstLogin && isPasswordExpired && !isActualFirstLogin) {
            // This is an expired password reset
            // No current password needed (user can't login anyway)

            // Check if new password is same as old password
            if (passwordEncoder.matches(newPassword, user.getPassword())) {
                return ChangePasswordStatus.SAME_PASSWORD;
            }

            // Update password
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordLastChangedAt(LocalDateTime.now());
            user.setPasswordExpiryDate(LocalDateTime.now().plusDays(passwordExpiryDays));

            userRepository.save(user);
            return ChangePasswordStatus.SUCCESS;
        }

        // ✅ SCENARIO 3: Invalid request (isFirstLogin=true but neither condition met)
        if (isFirstLogin && !isActualFirstLogin && !isPasswordExpired) {
            return ChangePasswordStatus.PASSWORD_NOT_EXPIRED;
        }

        // ✅ SCENARIO 4: Normal password change (requires current password)
        if (!isFirstLogin) {
            // Validate current password
            if (currentPassword == null || currentPassword.isBlank()) {
                return ChangePasswordStatus.INVALID_CURRENT_PASSWORD;
            }

            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                return ChangePasswordStatus.INVALID_CURRENT_PASSWORD;
            }

            // Check if new password is same as current password
            if (passwordEncoder.matches(newPassword, user.getPassword())) {
                return ChangePasswordStatus.SAME_PASSWORD;
            }

            // Update password
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setPasswordLastChangedAt(LocalDateTime.now());
            user.setPasswordExpiryDate(LocalDateTime.now().plusDays(passwordExpiryDays));

            userRepository.save(user);
            return ChangePasswordStatus.SUCCESS;
        }

        // Should never reach here, but just in case
        return ChangePasswordStatus.INVALID_CURRENT_PASSWORD;
    }
}