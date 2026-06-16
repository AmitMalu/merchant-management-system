package com.project2.ism.Security;

import com.project2.ism.Model.Users.User;
import com.project2.ism.Repository.UserRepository;
import com.project2.ism.Service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Order(1) // VERY IMPORTANT: runs before JwtAuthFilter
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;



    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    private static final Set<String> ALLOWED_IPS = Set.of(
            "178.79.181.249", "35.154.115.241"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String uri = request.getRequestURI();
        final String method = request.getMethod();

        logger.debug("Incoming request [{} {}]", method, uri);

        try {

            // Apply ONLY to Razorpay notification endpoint
            if (uri.startsWith("/razorpay/notification")) {

                String clientIp = getClientIp(request);
                logger.info("Razorpay notification request from IP: {}", clientIp);

                if (!ALLOWED_IPS.contains(clientIp)) {
                    logger.warn("Blocked Razorpay notification from IP: {}", clientIp);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("Forbidden");
                    return;
                }
                // Apply ONLY to Mosambee notification endpoint
            } else if (uri.startsWith("/mosambee/notification")) {

                    String clientIp = getClientIp(request);
                    logger.info("Mosambee notification request from IP: {}", clientIp);

                    if (!ALLOWED_IPS.contains(clientIp)) {
                        logger.warn("Blocked Mosambee notification from IP: {}", clientIp);
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.getWriter().write("Forbidden");
                        return;
                    }
                }

            String token = extractTokenFromRequest(request);

            if (!StringUtils.hasText(token)) {
                logger.debug("No JWT token found in request [{} {}], proceeding without authentication",
                        method, uri);
                filterChain.doFilter(request, response);
                return;
            }

            logger.debug("JWT token found for request [{} {}]", method, uri);

            String email = jwtService.extractEmail(token);
            logger.debug("Extracted email from token: {}", email);

            if (StringUtils.hasText(email)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                Optional<User> userOpt = userRepository.findByEmail(email);

                if (userOpt.isEmpty()) {
                    logger.warn("User not found for email [{}] from JWT | request [{} {}]",
                            email, method, uri);
                    filterChain.doFilter(request, response);
                    return;
                }

                User user = userOpt.get();

                if (!jwtService.validateToken(token, user.getEmail())) {
                    logger.warn("JWT validation failed for user [{}] | request [{} {}]",
                            email, method, uri);
                    filterChain.doFilter(request, response);
                    return;
                }

                String role = jwtService.extractRole(token);
                String normalizedRole = normalizeRole(role);

                logger.info("Authenticating request [{} {}] | user={} | role={}",
                        method, uri, email, normalizedRole);

                List<GrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority(normalizedRole));

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(email, null, authorities);
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);

                logger.info("Authentication SUCCESS | user={} | authorities={} | request [{} {}]",
                        email, authorities, method, uri);
            }

            filterChain.doFilter(request, response);

        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            logger.warn("JWT EXPIRED | request [{} {}]", method, uri);
            handleJwtException(response, request,
                    "JWT token has expired. Please login again.",
                    HttpServletResponse.SC_UNAUTHORIZED);

        } catch (io.jsonwebtoken.SignatureException ex) {
            logger.error("JWT SIGNATURE INVALID | request [{} {}]", method, uri);
            handleJwtException(response, request,
                    "Invalid token signature.",
                    HttpServletResponse.SC_UNAUTHORIZED);

        } catch (io.jsonwebtoken.MalformedJwtException ex) {
            logger.error("JWT MALFORMED | request [{} {}]", method, uri);
            handleJwtException(response, request,
                    "Malformed token.",
                    HttpServletResponse.SC_UNAUTHORIZED);

        } catch (Exception ex) {
            logger.error("JWT FILTER ERROR | request [{} {}]",
                    method, uri, ex);
            handleJwtException(response, request,
                    "Authentication error occurred.",
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "ROLE_USER";
        }

        String upperRole = role.toUpperCase();
        return upperRole.startsWith("ROLE_") ? upperRole : "ROLE_" + upperRole;
    }

    private void handleJwtException(HttpServletResponse response,
                                    HttpServletRequest request,
                                    String message,
                                    int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        // Set CORS headers
//        response.setHeader("Access-Control-Allow-Origin", allowedOrigin);
//        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
//        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
//        response.setHeader("Access-Control-Allow-Credentials", "true");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", status);
        errorResponse.put("error", status == 401 ? "Unauthorized" : "Internal Server Error");
        errorResponse.put("message", message);
        errorResponse.put("path", request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        response.getWriter().flush();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        logger.info("shouldNotFilter called for URI: {}", request.getRequestURI());

        String path = request.getRequestURI();
        return path.equals("/users/login") ||
                path.equals("/users/signup") ||
                path.startsWith("/actuator/health") ||
                path.equals("/razorpay/notification") ||
                request.getMethod().equals("OPTIONS");
    }
}