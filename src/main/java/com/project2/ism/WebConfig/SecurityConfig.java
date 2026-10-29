

package com.project2.ism.WebConfig;

import com.project2.ism.Security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Remove securityMatchers - this was limiting the scope
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/error"
                        ).permitAll()

                        .requestMatchers("/users/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                        .requestMatchers("/users/**").permitAll()

                        .requestMatchers("/actuator/health").permitAll()

                        .requestMatchers("/razorpay/notification").permitAll()

                        .requestMatchers("/mosambee/notification").permitAll()

                        // Temporary Paytm notification receiver (log only for now).
                        .requestMatchers("/paytm/notification").permitAll()

                        // Admin-only: these were open to the internet. /payment-vimo/**
                        // triggers vendor master-data syncs, /update-transaction/** edits
                        // transactions, and /tools/encrypt/** encrypts any JSON with the
                        // payout vendor's key — left public, that last one lets anyone
                        // forge a valid-looking vendor callback.
                        .requestMatchers(
                                "/payment-vimo/**",
                                "/update-transaction/**",
                                "/tools/encrypt/**"
                        ).hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // /vidual-pay/credit-card/** and /billpay/config/** used to be
                        // permitAll ("remove this later"); they now fall through to
                        // anyRequest().authenticated() — a valid login is required.

                        // Called by the payout vendor, not a logged-in user. Authenticity
                        // rests on the payload decrypting with the vendor key, and
                        // PayoutService refuses callbacks that conflict with a final status.
                        .requestMatchers("/payment-payout/callback").permitAll()

                        .requestMatchers(
                                "/vendors/**",
                                "/vendor-rates/**"
                        ).hasAnyRole("ADMIN", "SUPER_ADMIN")

                        .requestMatchers("/products/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")

                        .requestMatchers("/monitoring/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")

                        .requestMatchers("/franchise-wallet/**")
                        .hasRole("FRANCHISE")

                        // Admin dashboard logs (request / response data): admins only. These used to
                        // fall through to "any logged-in user".
                        .requestMatchers("/admin-logs/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // Credit card bill payment: merchants and franchises (and only if admin has
                        // switched it on for the account, checked in the service).
                        .requestMatchers("/credit-card/**")
                        .hasAnyRole("MERCHANT", "FRANCHISE")

                        // Credit card bill payment report: every role sees only its own scope
                        // (decided in CreditCardReportService from the logged-in user).
                        .requestMatchers("/reports/credit-card/**", "/reports/credit-card")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN", "MERCHANT", "FRANCHISE")

                        // Admin > Other > Notification URL: where payment notifications are forwarded.
                        .requestMatchers("/notification-callbacks/**", "/notification-callbacks")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")

                        // Payout report for franchises (its own payouts + its merchants').
                        .requestMatchers("/reports/payout/franchise/**", "/reports/payout/franchise")
                        .hasRole("FRANCHISE")

                        // The Payout / Credit card switches and the lien amount decide who may move
                        // money, so only admins may read or change them. These used to fall through
                        // to "any logged-in user".
                        .requestMatchers("/merchants/*/settings", "/franchise/*/settings")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")

                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        // Use environment-specific origins in production
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5175",
                "http://103.93.97.204:5173",
                "https://portal.ashwamlearning.co.in:5173",
                "https://portal.utsabpay.com",
                allowedOrigin
        ));
//        config.addAllowedOriginPattern("*");

        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        config.setExposedHeaders(List.of(
                "Authorization",
                "Content-Disposition"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}