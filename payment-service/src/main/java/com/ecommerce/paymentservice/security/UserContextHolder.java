package com.ecommerce.paymentservice.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {}

    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    public static UserContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static void checkOwnership(String requestedUserId) {
        UserContext ctx = get();
        if (ctx == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (ctx.isAdmin()) {
            return;
        }
        boolean matchesId = ctx.getUserId() != null && ctx.getUserId().equalsIgnoreCase(requestedUserId);
        boolean matchesEmail = ctx.getEmail() != null && ctx.getEmail().equalsIgnoreCase(requestedUserId);
        if (!matchesId && !matchesEmail) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: cannot access another user's payment resources");
        }
    }
}

