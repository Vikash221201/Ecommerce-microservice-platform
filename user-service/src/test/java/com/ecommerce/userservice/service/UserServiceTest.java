package com.ecommerce.userservice.service;

import com.ecommerce.userservice.dto.AuthResponse;
import com.ecommerce.userservice.dto.LoginRequest;
import com.ecommerce.userservice.dto.RegisterRequest;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private UUID sampleUserId;

    @BeforeEach
    void setUp() {
        sampleUserId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setId(sampleUserId);
        sampleUser.setEmail("jane@example.com");
        sampleUser.setFullName("Jane Doe");
        sampleUser.setPassword("encoded-password");
        sampleUser.setRole(User.Role.CUSTOMER);
    }

    @Test
    void registerShouldSucceedForNewEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("jane@example.com")
                .fullName("Jane Doe")
                .password("plain-password")
                .build();

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtUtil.generateToken(any(), eq("jane@example.com"), eq("CUSTOMER"))).thenReturn("mock-jwt-token");

        AuthResponse response = userService.register(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("jane@example.com", response.getEmail());
        assertEquals(sampleUserId.toString(), response.getUserId());
        assertEquals("CUSTOMER", response.getRole());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerShouldThrowWhenEmailExists() {
        RegisterRequest request = RegisterRequest.builder()
                .email("jane@example.com")
                .fullName("Jane Doe")
                .password("plain-password")
                .build();

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginShouldSucceedWithValidCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("plain-password");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken(any(), eq("jane@example.com"), eq("CUSTOMER"))).thenReturn("mock-jwt-token");

        AuthResponse response = userService.login(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("jane@example.com", response.getEmail());
    }

    @Test
    void loginShouldThrowWithInvalidPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("wrong-password");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> userService.login(request));
    }

    @Test
    void loginShouldThrowWhenUserNotFound() {
        LoginRequest request = new LoginRequest();
        request.setEmail("unknown@example.com");
        request.setPassword("some-password");

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.login(request));
    }
}
