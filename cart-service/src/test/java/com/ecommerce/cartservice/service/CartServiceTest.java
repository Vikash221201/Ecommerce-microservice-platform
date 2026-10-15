package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.model.Cart;
import com.ecommerce.cartservice.model.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private RedisTemplate<String, Cart> redisTemplate;

    @Mock
    private ValueOperations<String, Cart> valueOperations;

    @InjectMocks
    private CartService cartService;

    private static final String USER_ID = "cust-42";
    private static final String REDIS_KEY = "cart:cust-42";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("getCart returns existing cart when found in Redis")
    void testGetCart_Existing() {
        Cart cart = new Cart();
        cart.setUserId(USER_ID);
        cart.getItems().add(new CartItem("prod-1", "Mechanical Keyboard", new BigDecimal("89.99"), 1));

        when(valueOperations.get(REDIS_KEY)).thenReturn(cart);

        Cart result = cartService.getCart(USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getItems()).hasSize(1);
        verify(valueOperations, times(1)).get(REDIS_KEY);
    }

    @Test
    @DisplayName("getCart returns empty initialized cart when absent in Redis")
    void testGetCart_New() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);

        Cart result = cartService.getCart(USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getItems()).isEmpty();
        verify(valueOperations, times(1)).get(REDIS_KEY);
    }

    @Test
    @DisplayName("addItem adds new item to cart and persists to Redis with TTL")
    void testAddItem_New() {
        Cart emptyCart = new Cart();
        emptyCart.setUserId(USER_ID);
        when(valueOperations.get(REDIS_KEY)).thenReturn(emptyCart);

        CartItem item = new CartItem("prod-1", "Mechanical Keyboard", new BigDecimal("89.99"), 2);
        Cart updated = cartService.addItem(USER_ID, item);

        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getProductId()).isEqualTo("prod-1");
        assertThat(updated.getItems().get(0).getQuantity()).isEqualTo(2);

        verify(valueOperations, times(1)).set(eq(REDIS_KEY), eq(updated), any(Duration.class));
    }

    @Test
    @DisplayName("addItem replaces existing product entry when adding updated item")
    void testAddItem_UpdateExisting() {
        Cart existingCart = new Cart();
        existingCart.setUserId(USER_ID);
        existingCart.getItems().add(new CartItem("prod-1", "Mechanical Keyboard", new BigDecimal("89.99"), 1));
        when(valueOperations.get(REDIS_KEY)).thenReturn(existingCart);

        CartItem updatedItem = new CartItem("prod-1", "Mechanical Keyboard", new BigDecimal("89.99"), 3);
        Cart updated = cartService.addItem(USER_ID, updatedItem);

        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getQuantity()).isEqualTo(3);

        verify(valueOperations, times(1)).set(eq(REDIS_KEY), eq(updated), any(Duration.class));
    }

    @Test
    @DisplayName("removeItem deletes specified product from cart and persists")
    void testRemoveItem() {
        Cart existingCart = new Cart();
        existingCart.setUserId(USER_ID);
        existingCart.getItems().add(new CartItem("prod-1", "Keyboard", new BigDecimal("89.99"), 1));
        existingCart.getItems().add(new CartItem("prod-2", "Mouse", new BigDecimal("49.99"), 1));
        when(valueOperations.get(REDIS_KEY)).thenReturn(existingCart);

        Cart updated = cartService.removeItem(USER_ID, "prod-1");

        assertThat(updated.getItems()).hasSize(1);
        assertThat(updated.getItems().get(0).getProductId()).isEqualTo("prod-2");

        verify(valueOperations, times(1)).set(eq(REDIS_KEY), eq(updated), any(Duration.class));
    }

    @Test
    @DisplayName("clearCart calls redisTemplate.delete for the user key")
    void testClearCart() {
        cartService.clearCart(USER_ID);

        verify(redisTemplate, times(1)).delete(REDIS_KEY);
    }
}
