package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.security.UserContext;
import com.ecommerce.orderservice.security.UserContextHolder;
import com.ecommerce.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody CreateOrderRequest request) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to create orders");
        }
        String callerUserId = ctx.getUserId() != null ? ctx.getUserId() : ctx.getEmail();
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            request.setUserId(callerUserId);
        } else if (!ctx.isAdmin() && !request.getUserId().equalsIgnoreCase(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: cannot create order for another user");
        }
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @GetMapping("/{id}")
    public Order getById(@PathVariable UUID id) {
        Order order = orderService.getById(id);
        if (order != null) {
            UserContextHolder.checkOwnership(order.getUserId());
        }
        return order;
    }

    @GetMapping("/user/{userId}")
    public List<Order> getByUser(@PathVariable String userId) {
        UserContextHolder.checkOwnership(userId);
        return orderService.getByUser(userId);
    }

    @PutMapping("/{id}/status")
    public Order updateStatus(@PathVariable UUID id, @RequestParam Order.OrderStatus status) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!ctx.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: ADMIN role required to update order status");
        }
        return orderService.updateStatus(id, status);
    }
}
