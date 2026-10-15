package com.ecommerce.userservice.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.Principal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements Principal {
    private UUID userId;
    private String email;
    private String role;

    @Override
    public String getName() {
        return email;
    }
}
