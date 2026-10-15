package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.security.JwtValidator;
import com.ecommerce.orderservice.security.SecurityFilter;
import com.ecommerce.orderservice.service.OrderService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityFilter.class, JwtValidator.class})
@AutoConfigureMockMvc(addFilters = true)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_ID = "cust-10";
    private static final String USER_EMAIL = "cust10@example.com";
    private static final String USER_ROLE = "ROLE_CUSTOMER";

    private UUID sampleOrderId;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrderId = UUID.randomUUID();
        sampleOrder = new Order();
        sampleOrder.setId(sampleOrderId);
        sampleOrder.setUserId(USER_ID);
        sampleOrder.setStatus(Order.OrderStatus.PENDING);
        sampleOrder.setTotalAmount(new BigDecimal("99.99"));
    }

    @Test
    @DisplayName("POST /api/orders creates order for authenticated customer")
    void testCreateOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(USER_ID);

        OrderItemRequest item = new OrderItemRequest();
        item.setProductId("p1");
        item.setProductName("Gaming Mouse");
        item.setPrice(new BigDecimal("99.99"));
        item.setQuantity(1);
        request.setItems(List.of(item));

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(sampleOrder);

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleOrderId.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} returns order details for owner")
    void testGetOrderById() throws Exception {
        when(orderService.getById(sampleOrderId)).thenReturn(sampleOrder);

        mockMvc.perform(get("/api/orders/" + sampleOrderId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleOrderId.toString()))
                .andExpect(jsonPath("$.totalAmount").value(99.99));
    }

    @Test
    @DisplayName("GET /api/orders/user/{userId} returns orders list for user")
    void testGetOrdersByUser() throws Exception {
        when(orderService.getByUser(USER_ID)).thenReturn(List.of(sampleOrder));

        mockMvc.perform(get("/api/orders/user/" + USER_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(sampleOrderId.toString()));
    }

    @Test
    @DisplayName("PUT /api/orders/{id}/status succeeds when invoked by administrator")
    void testUpdateStatus_Admin() throws Exception {
        sampleOrder.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderService.updateStatus(eq(sampleOrderId), eq(Order.OrderStatus.CONFIRMED)))
                .thenReturn(sampleOrder);

        mockMvc.perform(put("/api/orders/" + sampleOrderId + "/status")
                        .param("status", "CONFIRMED")
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Email", "admin@ecommerce.com")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
