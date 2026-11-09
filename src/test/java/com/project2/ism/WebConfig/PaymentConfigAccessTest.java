package com.project2.ism.WebConfig;

import com.project2.ism.Security.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Payment configuration (vendor credentials, vendors, charges, modes, products, routing) is for admins
 * only. Runs the real SecurityConfig; nothing here has a controller, so a request that gets past the
 * access rule answers 404, and one that does not answers 403.
 */
@SpringJUnitWebConfig(classes = {SecurityConfig.class, PaymentConfigAccessTest.Beans.class})
@TestPropertySource(properties = "app.cors.allowed-origin=http://localhost:5173")
class PaymentConfigAccessTest {

    @Configuration
    @EnableWebMvc
    static class Beans {
        /** The JWT filter is not under test: let every request through to the access rules. */
        @Bean
        JwtAuthFilter jwtAuthFilter() throws Exception {
            JwtAuthFilter filter = mock(JwtAuthFilter.class);
            doAnswer(i -> {
                ((FilterChain) i.getArgument(2)).doFilter(i.getArgument(0), i.getArgument(1));
                return null;
            }).when(filter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
            return filter;
        }
    }

    private static final List<String> PAYMENT_CONFIG_PATHS = List.of(
            "/payment-vendor-credentials", "/payment-vendor-credentials/5",
            "/payment-vendors", "/payment-vendors/id-name", "/payment-vendors/5",
            "/payment-charges", "/payment-charges/stats", "/payment-charges/5",
            "/payment-modes", "/payment-modes/5",
            "/payment-products", "/payment-products/id-name",
            "/payment-vendor-routing", "/payment-vendor-routing/5");

    private static final List<HttpMethod> METHODS = List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE);

    @Autowired
    WebApplicationContext wac;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    private int status(HttpMethod method, String path, String role) throws Exception {
        var req = request(method, path);
        if (role != null) {
            req = req.with(user("someone").roles(role));
        }
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    void merchantsAndFranchises_cannotReadOrChangePaymentConfiguration() throws Exception {
        for (String role : new String[]{"MERCHANT", "FRANCHISE"}) {
            for (String path : PAYMENT_CONFIG_PATHS) {
                for (HttpMethod method : METHODS) {
                    assertEquals(403, status(method, path, role), role + " " + method + " " + path);
                }
            }
        }
    }

    @Test
    void notLoggedIn_cannotEither() throws Exception {
        for (String path : PAYMENT_CONFIG_PATHS) {
            for (HttpMethod method : METHODS) {
                int s = status(method, path, null);
                assertEquals(403, s, "anonymous " + method + " " + path);
            }
        }
    }

    @Test
    void adminAndSuperAdmin_getThrough() throws Exception {
        for (String role : new String[]{"ADMIN", "SUPER_ADMIN"}) {
            for (String path : PAYMENT_CONFIG_PATHS) {
                for (HttpMethod method : METHODS) {
                    int s = status(method, path, role);
                    // no controller in this test, so getting past the access rule means 404
                    assertEquals(404, s, role + " " + method + " " + path);
                }
            }
        }
    }

    @Test
    void otherEndpoints_areNotAffected() throws Exception {
        // payouts and credit cards stay open to merchants and franchises, as before
        assertNotEquals(403, status(HttpMethod.GET, "/payment-payout/history", "MERCHANT"));
        assertNotEquals(403, status(HttpMethod.POST, "/payment-payout/initiate", "FRANCHISE"));
        assertNotEquals(403, status(HttpMethod.GET, "/credit-card/billers", "MERCHANT"));
        assertNotEquals(403, status(HttpMethod.GET, "/reports/credit-card", "MERCHANT"));
        // the vendor's callback is still reachable without a login
        assertNotEquals(403, status(HttpMethod.POST, "/payment-payout/callback", null));
        assertNotEquals(403, status(HttpMethod.POST, "/paytm/notification", null));
    }

    @Test
    void similarlyNamedPaths_areNotSweptIntoTheRule() throws Exception {
        // the rule is for the payment configuration paths only, not anything that starts with "payment-"
        assertNotEquals(403, status(HttpMethod.GET, "/payment-payout/status", "MERCHANT"));
    }
}
