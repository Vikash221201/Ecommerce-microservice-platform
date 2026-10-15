package com.ecommerce.userservice.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt hashed

    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role = Role.CUSTOMER;

    public enum Role {
        CUSTOMER, ADMIN;

        @com.fasterxml.jackson.annotation.JsonCreator
        public static Role fromString(String value) {
            if (value == null) return CUSTOMER;
            String normalized = value.trim().toUpperCase();
            if (normalized.contains("ADMIN")) {
                return ADMIN;
            }
            return CUSTOMER;
        }
    }
}
