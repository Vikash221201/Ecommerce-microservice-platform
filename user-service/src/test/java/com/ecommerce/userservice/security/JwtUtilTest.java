package com.ecommerce.userservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "test-secret-key-that-is-at-least-32-characters-long-for-hmac";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(secret, 3600000);
    }

    @Test
    void shouldGenerateAndValidateTokenWithClaims() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String role = "CUSTOMER";

        String token = jwtUtil.generateToken(userId, email, role);

        assertNotNull(token);
        assertTrue(jwtUtil.isTokenValid(token));
        assertEquals(email, jwtUtil.extractEmail(token));
        assertEquals(userId.toString(), jwtUtil.extractUserId(token));
        assertEquals("ROLE_CUSTOMER", jwtUtil.extractRole(token));
    }

    @Test
    void shouldRejectInvalidToken() {
        assertFalse(jwtUtil.isTokenValid("totally-invalid.jwt.token"));
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtUtil expiredJwtUtil = new JwtUtil(secret, -1000); // Already expired
        String token = expiredJwtUtil.generateToken(UUID.randomUUID(), "expired@example.com", "ADMIN");

        assertFalse(jwtUtil.isTokenValid(token));
    }
}
