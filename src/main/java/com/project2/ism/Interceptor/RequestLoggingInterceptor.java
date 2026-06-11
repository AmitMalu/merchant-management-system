package com.project2.ism.Interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project2.ism.Model.RequestLog;
import com.project2.ism.Service.JwtService;
import com.project2.ism.Service.LogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "startTime";
    private static final String REQUEST_LOG_ATTR = "requestLog";


    private final LogService logService;
    private final JwtService jwtUtil;
    private final ObjectMapper objectMapper;

    public RequestLoggingInterceptor(LogService logService, JwtService jwtUtil, ObjectMapper objectMapper) {
        this.logService = logService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTR, startTime);

        // Skip logging for static resources
        String requestUri = request.getRequestURI();
        if (shouldSkipLogging(requestUri)) {
            return true;
        }

        // Create request log entry
        RequestLog requestLog = new RequestLog();

        // Extract user information from JWT token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String email = jwtUtil.extractEmail(token);
                String role = jwtUtil.extractRole(token);
                requestLog.setUserId(email); // Using email as userId
                requestLog.setUsername(email + " (" + role + ")"); // Using email + role as username
            } catch (Exception e) {
                // Invalid token, continue without user info
                requestLog.setUserId("anonymous");
                requestLog.setUsername("anonymous");
            }
        } else {
            requestLog.setUserId("anonymous");
            requestLog.setUsername("anonymous");
        }

        // Set request details
        requestLog.setRequestMethod(request.getMethod());
        requestLog.setRequestUrl(getFullURL(request));
        requestLog.setIpAddress(getClientIpAddress(request));
        requestLog.setUserAgent(request.getHeader("User-Agent"));
        requestLog.setSessionId(request.getSession(false) != null ? request.getSession().getId() : null);

        // Set request headers
        requestLog.setRequestHeaders(getHeadersAsString(request));

//        // Set request body if available
//        if (request instanceof ContentCachingRequestWrapper) {
//            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
//            byte[] content = wrapper.getContentAsByteArray();
//            if (content.length > 0) {
//                String requestBody = new String(content, StandardCharsets.UTF_8);
//                // Limit body size to prevent database issues
//                if (requestBody.length() > 5000) {
//                    requestBody = requestBody.substring(0, 5000) + "... [truncated]";
//                }
//                requestLog.setRequestBody(requestBody);
//            }
//        }

        request.setAttribute(REQUEST_LOG_ATTR, requestLog);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {

        if (shouldSkipLogging(request.getRequestURI())) {
            return;
        }

        RequestLog requestLog = (RequestLog) request.getAttribute(REQUEST_LOG_ATTR);
        if (requestLog == null) {
            return;
        }

        // Calculate execution time
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        if (startTime != null) {
            long executionTime = System.currentTimeMillis() - startTime;
            requestLog.setExecutionTimeMs(executionTime);
        }

        // Set response details
        requestLog.setResponseStatus(response.getStatus());

        // Set response headers
        requestLog.setResponseHeaders(getResponseHeadersAsString(response));

        // Set request body if available
        if (request instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
            byte[] content = wrapper.getContentAsByteArray();
            if (content.length > 0) {
                String requestBody = new String(content, StandardCharsets.UTF_8);
                // Limit body size to prevent database issues
                if (requestBody.length() > 5000) {
                    requestBody = requestBody.substring(0, 5000) + "... [truncated]";
                }
                if (request.getRequestURI().contains("/login")) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode node = mapper.readTree(requestBody);
                        ObjectNode masked = mapper.createObjectNode();
                        masked.put("email", node.has("email") ? node.get("email").asText() : "[UNKNOWN]");
                        masked.put("password", "[PROTECTED]");
                        requestLog.setRequestBody(masked.toString());
                    } catch (Exception e) {
                        requestLog.setRequestBody("{\"password\":\"[PROTECTED]\"}");
                    }
                } else {
                    requestLog.setRequestBody(requestBody);
                }


            }
        }
        // Set response body if available
        if (response instanceof ContentCachingResponseWrapper) {
            ContentCachingResponseWrapper wrapper = (ContentCachingResponseWrapper) response;
            byte[] content = wrapper.getContentAsByteArray();
            if (content.length > 0) {
                String responseBody = new String(content, StandardCharsets.UTF_8);
                // Limit body size to prevent database issues
                if (responseBody.length() > 5000) {
                    responseBody = responseBody.substring(0, 5000) + "... [truncated]";
                }
                requestLog.setResponseBody(responseBody);
            }
        }

        // Set error message if exception occurred
        if (ex != null) {
            requestLog.setErrorMessage(ex.getMessage());
        }

        // Save the log asynchronously
        logService.saveRequestLogAsync(requestLog);
    }

    private boolean shouldSkipLogging(String uri) {
        // Skip logging for static resources and health checks
        return uri.matches(".*(\\.(css|js|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot))") ||
                uri.contains("/actuator/") ||
                uri.contains("/health") ||
                uri.contains("/swagger") ||
                uri.contains("/api-docs") ||
                uri.contains("/admin-logs");
    }

    private String getFullURL(HttpServletRequest request) {
        StringBuffer requestURL = request.getRequestURL();
        String queryString = request.getQueryString();

        if (queryString == null) {
            return requestURL.toString();
        } else {
            return requestURL.append('?').append(queryString).toString();
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0];
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty() && !"unknown".equalsIgnoreCase(xRealIP)) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }

    private String getHeadersAsString(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            // Skip sensitive headers
            if (!isSensitiveHeader(headerName)) {
                headers.put(headerName, request.getHeader(headerName));
            }
        }

        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String getResponseHeadersAsString(HttpServletResponse response) {
        Map<String, String> headers = new HashMap<>();

        for (String headerName : response.getHeaderNames()) {
            if (!isSensitiveHeader(headerName)) {
                headers.put(headerName, response.getHeader(headerName));
            }
        }

        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean isSensitiveHeader(String headerName) {
        String lowerCaseName = headerName.toLowerCase();
        return lowerCaseName.contains("authorization") ||
                lowerCaseName.contains("password") ||
                lowerCaseName.contains("token") ||
                lowerCaseName.contains("cookie") ||
                lowerCaseName.contains("session");
    }
}