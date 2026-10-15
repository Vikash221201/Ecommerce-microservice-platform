package com.ecommerce.apigateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtAuthenticationGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationGatewayFilter.class);

    private final String secret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationGatewayFilter(@Value("${jwt.secret:change-this-super-secret-key-min-32-chars-long}") String secret) {
        this.secret = secret;
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        // Whitelisted public endpoints
        if (isPublicEndpoint(path, method)) {
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    Claims claims = parseClaims(token);
                    ServerHttpRequest mutated = enrichRequest(request, claims);
                    return chain.filter(exchange.mutate().request(mutated).build());
                } catch (Exception ignored) {
                    // Permit public route even if token parse fails
                }
            }
            return chain.filter(exchange);
        }

        // Protected endpoint: check for Bearer token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (Exception e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT token");
        }

        String role = claims.get("role", String.class);
        if (role == null) {
            role = "ROLE_CUSTOMER";
        }

        // Role-based access control: Product modification requires ADMIN
        if (path.startsWith("/api/products") && !HttpMethod.GET.equals(method)) {
            if (!isAdminRole(role)) {
                return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "Access denied: ADMIN role required");
            }
        }

        // Enrich request with verified identity headers
        ServerHttpRequest mutated = enrichRequest(request, claims);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isPublicEndpoint(String path, HttpMethod method) {
        if (path.startsWith("/api/auth/") || path.equals("/api/auth")) {
            return true;
        }
        if (path.startsWith("/actuator/")) {
            return true;
        }
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && path.startsWith("/api/products")) {
            return true;
        }
        return false;
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private ServerHttpRequest enrichRequest(ServerHttpRequest request, Claims claims) {
        String userId = claims.get("userId", String.class);
        String email = claims.get("email", String.class);
        if (email == null) {
            email = claims.getSubject();
        }
        String role = claims.get("role", String.class);
        if (role == null) {
            role = "ROLE_CUSTOMER";
        }

        var builder = request.mutate();
        if (userId != null) {
            builder.header("X-User-Id", userId);
        }
        if (email != null) {
            builder.header("X-User-Email", email);
        }
        builder.header("X-User-Role", role);
        return builder.build();
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("status", status.value());
        errorAttributes.put("error", status.getReasonPhrase());
        errorAttributes.put("message", message);
        errorAttributes.put("path", exchange.getRequest().getPath().value());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorAttributes);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}").getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
