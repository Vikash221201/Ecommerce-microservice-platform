package com.ecommerce.cartservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class JwtValidator {

    @Value("${jwt.secret:change-this-super-secret-key-min-32-chars-long}")
    private String secret;

    public JwtValidator() {}

    public JwtValidator(String secret) {
        this.secret = secret;
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public UserContext validateAndExtract(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String userId = claims.get("userId", String.class);
        String email = claims.get("email", String.class);
        if (email == null) {
            email = claims.getSubject();
        }
        String role = claims.get("role", String.class);
        if (role == null) {
            role = "ROLE_CUSTOMER";
        }

        return UserContext.builder()
                .userId(userId)
                .email(email)
                .role(role)
                .build();
    }
}
