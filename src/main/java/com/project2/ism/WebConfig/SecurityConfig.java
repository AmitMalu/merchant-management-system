

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

                        .requestMatchers("/payment-vimo/**").permitAll()

                        .requestMatchers("/vidual-pay/credit-card/**").permitAll()   //remove this later

                        .requestMatchers("/update-transaction/**").permitAll()      //remove this later

                        .requestMatchers("/billpay/config/**").permitAll()      //remove this later

                        .requestMatchers(
                                "/payment-payout/callback",
                                "/tools/encrypt/**"
                        ).permitAll()

                        .requestMatchers(
                                "/vendors/**",
                                "/vendor-rates/**"
                        ).hasAnyRole("ADMIN", "SUPER_ADMIN")

                        .requestMatchers("/products/**")
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