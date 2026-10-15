package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.model.Cart;
import com.ecommerce.cartservice.model.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CartService {

    private final RedisTemplate<String, Cart> redisTemplate;
    private static final String KEY_PREFIX = "cart:";
    private static final Duration TTL = Duration.ofDays(7);

    public Cart getCart(String userId) {
        Cart cart = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (cart == null) {
            cart = new Cart();
            cart.setUserId(userId);
        }
        return cart;
    }

    public Cart addItem(String userId, CartItem item) {
        Cart cart = getCart(userId);
        cart.getItems().removeIf(i -> i.getProductId().equals(item.getProductId()));
        cart.getItems().add(item);
        save(cart);
        return cart;
    }

    public Cart removeItem(String userId, String productId) {
        Cart cart = getCart(userId);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        save(cart);
        return cart;
    }

    public void clearCart(String userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }

    private void save(Cart cart) {
        redisTemplate.opsForValue().set(KEY_PREFIX + cart.getUserId(), cart, TTL);
    }
}
