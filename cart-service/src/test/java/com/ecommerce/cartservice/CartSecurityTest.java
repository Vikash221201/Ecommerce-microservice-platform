package com.ecommerce.cartservice;

import com.ecommerce.cartservice.controller.CartController;
import com.ecommerce.cartservice.model.Cart;
import com.ecommerce.cartservice.security.JwtValidator;
import com.ecommerce.cartservice.security.SecurityFilter;
import com.ecommerce.cartservice.service.CartService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@Import({SecurityFilter.class, JwtValidator.class})
@AutoConfigureMockMvc(addFilters = true)
class CartSecurityTest {

    private static final String SECRET = "change-this-super-secret-key-min-32-chars-long";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    private String generateToken(String userId, String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    void unauthenticatedCartAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/cart/user-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userAccessingAnotherUsersCartReturns403() throws Exception {
        String token = generateToken("user-1", "user1@example.com", "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/cart/user-2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void userAccessingOwnCartReturns200() throws Exception {
        String token = generateToken("user-1", "user1@example.com", "ROLE_CUSTOMER");
        Cart cart = new Cart("user-1");
        when(cartService.getCart("user-1")).thenReturn(cart);

        mockMvc.perform(get("/api/cart/user-1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void adminAccessingAnyUsersCartReturns200() throws Exception {
        String token = generateToken("admin-1", "admin@example.com", "ROLE_ADMIN");
        Cart cart = new Cart("user-2");
        when(cartService.getCart("user-2")).thenReturn(cart);

        mockMvc.perform(get("/api/cart/user-2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-2"));
    }

    @Test
    void getMyCartDerivesUserIdFromToken() throws Exception {
        String token = generateToken("user-1", "user1@example.com", "ROLE_CUSTOMER");
        Cart cart = new Cart("user-1");
        when(cartService.getCart("user-1")).thenReturn(cart);

        mockMvc.perform(get("/api/cart/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void gatewayHeadersEnforceOwnership() throws Exception {
        Cart cart = new Cart("user-3");
        when(cartService.getCart("user-3")).thenReturn(cart);

        mockMvc.perform(get("/api/cart/user-3")
                        .header("X-User-Id", "user-3")
                        .header("X-User-Email", "user3@example.com")
                        .header("X-User-Role", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-3"));
    }
}
