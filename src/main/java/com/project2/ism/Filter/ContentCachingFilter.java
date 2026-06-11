package com.project2.ism.Filter;



import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
@Order(1)
public class ContentCachingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip caching for static resources
        String requestUri = httpRequest.getRequestURI();
        if (shouldSkipCaching(requestUri)) {
            chain.doFilter(request, response);
            return;
        }

        // Wrap request and response to cache content
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            // Important: copy the cached body to the original response
            wrappedResponse.copyBodyToResponse();
        }
    }

    private boolean shouldSkipCaching(String uri) {
        return uri.matches(".*(\\.(css|js|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot))") ||
                uri.contains("/actuator/") ||
                uri.contains("/health") ||
                uri.contains("/swagger") ||
                uri.contains("/api-docs");
    }
}
