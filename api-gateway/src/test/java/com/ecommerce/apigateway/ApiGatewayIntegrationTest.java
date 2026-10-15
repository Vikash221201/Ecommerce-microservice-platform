package com.ecommerce.apigateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayIntegrationTest {

    private static final String SECRET = "change-this-super-secret-key-min-32-chars-long";

    @Autowired
    private WebTestClient webTestClient;

    private String createTestToken(String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .subject(email)
                .claim("userId", UUID.randomUUID().toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    void healthEndpointShouldReturnUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void corsPreflightRequestShouldBeHandled() {
        webTestClient.options()
                .uri("/api/products")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, methods -> {
                    org.junit.jupiter.api.Assertions.assertTrue(methods.contains("GET"));
                });
    }

    @Test
    void routeToUnavailableDownstreamReturnsStructuredError() {
        webTestClient.get()
                .uri("/api/products/non-existent-product")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").exists()
                .jsonPath("$.error").exists()
                .jsonPath("$.message").exists()
                .jsonPath("$.path").isEqualTo("/api/products/non-existent-product")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void unauthenticatedProtectedRequestShouldReturn401() {
        webTestClient.get()
                .uri("/api/cart/user-123")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.message").isEqualTo("Missing or invalid Authorization header");
    }

    @Test
    void invalidTokenOnProtectedRequestShouldReturn401() {
        webTestClient.get()
                .uri("/api/cart/user-123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer totally-invalid-token")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.message").isEqualTo("Invalid or expired JWT token");
    }

    @Test
    void customerTokenAttemptingAdminActionShouldReturn403() {
        String token = createTestToken("customer@example.com", "ROLE_CUSTOMER");

        webTestClient.post()
                .uri("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Laptop\",\"price\":999.99}")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("Forbidden")
                .jsonPath("$.message").isEqualTo("Access denied: ADMIN role required");
    }

    @Test
    void adminTokenAttemptingAdminActionPassesGatewayAuth() {
        String token = createTestToken("admin@example.com", "ROLE_ADMIN");

        webTestClient.post()
                .uri("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Laptop\",\"price\":999.99}")
                .exchange()
                // Gateway authentication passes, then attempts downstream routing to product-service (which is unavailable in test, returning 5xx)
                .expectStatus().is5xxServerError()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").value(status -> {
                    org.junit.jupiter.api.Assertions.assertTrue((Integer) status >= 500);
                });
    }
}
