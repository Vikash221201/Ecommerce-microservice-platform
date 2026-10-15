package com.ecommerce.paymentservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;

    public SecurityFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String userId = request.getHeader("X-User-Id");
            String email = request.getHeader("X-User-Email");
            String role = request.getHeader("X-User-Role");
            String authHeader = request.getHeader("Authorization");

            if (userId != null || email != null || role != null) {
                UserContext context = UserContext.builder()
                        .userId(userId)
                        .email(email)
                        .role(role != null ? role : "ROLE_CUSTOMER")
                        .rawAuthHeader(authHeader)
                        .build();
                UserContextHolder.set(context);
            } else if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    UserContext context = jwtValidator.validateAndExtract(token);
                    context.setRawAuthHeader(authHeader);
                    UserContextHolder.set(context);
                } catch (Exception ignored) {
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }
}

