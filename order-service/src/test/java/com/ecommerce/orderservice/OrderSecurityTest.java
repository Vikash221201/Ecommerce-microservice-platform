package com.ecommerce.orderservice;

import com.ecommerce.orderservice.controller.OrderController;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.security.JwtValidator;
import com.ecommerce.orderservice.security.SecurityFilter;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityFilter.class, JwtValidator.class})
@AutoConfigureMockMvc(addFilters = true)
class OrderSecurityTest {

    private static final String SECRET = "change-this-super-secret-key-min-32-chars-long";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

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
    void unauthenticatedOrderCreateReturns401() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId("user-1");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotCreateOrderForAnotherUser() throws Exception {
        String token = generateToken("user-1", "user1@example.com", "ROLE_CUSTOMER");

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId("user-2"); // Trying to spoof user-2

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCanCreateOrderForThemselves() throws Exception {
        String token = generateToken("user-1", "user1@example.com", "ROLE_CUSTOMER");

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId("user-1");

        Order order = new Order();
        UUID orderId = UUID.randomUUID();
        order.setId(orderId);
        order.setUserId("user-1");
        order.setTotalAmount(new BigDecimal("99.99"));

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(order);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void userAccessingAnotherUsersOrderReturns403() throws Exception {
        String token = generateToken("user-1", "user1@example.com", "ROLE_CUSTOMER");

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setUserId("user-2"); // Belongs to user-2

        when(orderService.getById(orderId)).thenReturn(order);

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void userAccessingOwnOrderReturns200() throws Exception {
        String token = generateToken("user-1", "user1@example.com", "ROLE_CUSTOMER");

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setUserId("user-1");

        when(orderService.getById(orderId)).thenReturn(order);

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void adminAccessingAnyOrderReturns200() throws Exception {
        String token = generateToken("admin-1", "admin@store.com", "ROLE_ADMIN");

        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setUserId("user-2");

        when(orderService.getById(orderId)).thenReturn(order);

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    @Test
    void nonAdminUpdatingStatusReturns403() throws Exception {
        String token = generateToken("user-1", "user1@example.com", "ROLE_CUSTOMER");
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .param("status", "CONFIRMED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdatingStatusReturns200() throws Exception {
        String token = generateToken("admin-1", "admin@store.com", "ROLE_ADMIN");
        UUID orderId = UUID.randomUUID();

        Order order = new Order();
        order.setId(orderId);
        order.setStatus(Order.OrderStatus.CONFIRMED);

        when(orderService.updateStatus(eq(orderId), eq(Order.OrderStatus.CONFIRMED))).thenReturn(order);

        mockMvc.perform(put("/api/orders/" + orderId + "/status")
                        .param("status", "CONFIRMED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }
}
