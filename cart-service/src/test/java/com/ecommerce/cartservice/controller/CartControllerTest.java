package com.ecommerce.cartservice.controller;

import com.ecommerce.cartservice.model.Cart;
import com.ecommerce.cartservice.model.CartItem;
import com.ecommerce.cartservice.security.JwtValidator;
import com.ecommerce.cartservice.security.SecurityFilter;
import com.ecommerce.cartservice.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@Import({SecurityFilter.class, JwtValidator.class})
@AutoConfigureMockMvc(addFilters = true)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_ID = "cust-42";
    private static final String USER_EMAIL = "cust42@example.com";
    private static final String USER_ROLE = "ROLE_CUSTOMER";

    private Cart sampleCart;

    @BeforeEach
    void setUp() {
        sampleCart = new Cart();
        sampleCart.setUserId(USER_ID);
        sampleCart.getItems().add(new CartItem("prod-1", "Mechanical Keyboard", new BigDecimal("89.99"), 1));
    }

    @Test
    @DisplayName("GET /api/cart/me returns authenticated user's cart")
    void testGetMyCart() throws Exception {
        when(cartService.getCart(USER_ID)).thenReturn(sampleCart);

        mockMvc.perform(get("/api/cart/me")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.items[0].productId").value("prod-1"));
    }

    @Test
    @DisplayName("GET /api/cart/{userId} returns cart for owner")
    void testGetCart() throws Exception {
        when(cartService.getCart(USER_ID)).thenReturn(sampleCart);

        mockMvc.perform(get("/api/cart/" + USER_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID));
    }

    @Test
    @DisplayName("POST /api/cart/{userId}/items adds item to cart")
    void testAddItem() throws Exception {
        CartItem newItem = new CartItem("prod-2", "Ergonomic Mouse", new BigDecimal("49.99"), 1);
        sampleCart.getItems().add(newItem);

        when(cartService.addItem(eq(USER_ID), any(CartItem.class))).thenReturn(sampleCart);

        mockMvc.perform(post("/api/cart/" + USER_ID + "/items")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("DELETE /api/cart/{userId}/items/{productId} removes item from cart")
    void testRemoveItem() throws Exception {
        Cart emptiedCart = new Cart();
        emptiedCart.setUserId(USER_ID);

        when(cartService.removeItem(USER_ID, "prod-1")).thenReturn(emptiedCart);

        mockMvc.perform(delete("/api/cart/" + USER_ID + "/items/prod-1")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("DELETE /api/cart/{userId} clears user's cart")
    void testClearCart() throws Exception {
        doNothing().when(cartService).clearCart(USER_ID);

        mockMvc.perform(delete("/api/cart/" + USER_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE))
                .andExpect(status().isOk());
    }
}
