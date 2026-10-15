package com.ecommerce.cartservice.controller;

import com.ecommerce.cartservice.model.Cart;
import com.ecommerce.cartservice.model.CartItem;
import com.ecommerce.cartservice.security.UserContext;
import com.ecommerce.cartservice.security.UserContextHolder;
import com.ecommerce.cartservice.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/me")
    public Cart getMyCart() {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String id = ctx.getUserId() != null ? ctx.getUserId() : ctx.getEmail();
        return cartService.getCart(id);
    }

    @GetMapping("/{userId}")
    public Cart getCart(@PathVariable String userId) {
        UserContextHolder.checkOwnership(userId);
        return cartService.getCart(userId);
    }

    @PostMapping("/{userId}/items")
    public Cart addItem(@PathVariable String userId, @RequestBody CartItem item) {
        UserContextHolder.checkOwnership(userId);
        return cartService.addItem(userId, item);
    }

    @DeleteMapping("/{userId}/items/{productId}")
    public Cart removeItem(@PathVariable String userId, @PathVariable String productId) {
        UserContextHolder.checkOwnership(userId);
        return cartService.removeItem(userId, productId);
    }

    @DeleteMapping("/{userId}")
    public void clearCart(@PathVariable String userId) {
        UserContextHolder.checkOwnership(userId);
        cartService.clearCart(userId);
    }
}
