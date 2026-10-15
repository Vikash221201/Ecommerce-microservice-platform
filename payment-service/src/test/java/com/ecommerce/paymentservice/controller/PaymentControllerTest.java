package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.model.PaymentMethod;
import com.ecommerce.paymentservice.model.PaymentStatus;
import com.ecommerce.paymentservice.security.JwtValidator;
import com.ecommerce.paymentservice.security.SecurityFilter;
import com.ecommerce.paymentservice.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityFilter.class, JwtValidator.class})
@AutoConfigureMockMvc(addFilters = true)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_ID = "user-123";
    private static final String USER_EMAIL = "user@example.com";
    private static final String USER_ROLE = "ROLE_CUSTOMER";

    private UUID paymentId;
    private UUID orderId;
    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        samplePayment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .userId(USER_ID)
                .amount(new BigDecimal("89.50"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .status(PaymentStatus.SUCCESS)
                .transactionReference("TXN-ABC123456789DEF0")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createPayment_Success() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .userId(USER_ID)
                .amount(new BigDecimal("89.50"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .build();

        when(paymentService.processPayment(any(CreatePaymentRequest.class))).thenReturn(samplePayment);

        mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentMethod").value("TEST_SUCCESS"))
                .andExpect(jsonPath("$.transactionReference").value("TXN-ABC123456789DEF0"));
    }

    @Test
    void createPayment_ValidationError_MissingOrderId() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .userId(USER_ID)
                .amount(new BigDecimal("89.50"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .build();

        mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed for request arguments"));
    }

    @Test
    void getPaymentById_Success() throws Exception {
        when(paymentService.getById(paymentId)).thenReturn(samplePayment);

        mockMvc.perform(get("/api/payments/" + paymentId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void getPaymentByOrderId_Success() throws Exception {
        when(paymentService.getByOrderId(orderId)).thenReturn(samplePayment);

        mockMvc.perform(get("/api/payments/order/" + orderId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void refundPayment_Success() throws Exception {
        Payment refundedPayment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .userId(USER_ID)
                .amount(new BigDecimal("89.50"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .status(PaymentStatus.REFUNDED)
                .transactionReference("TXN-ABC123456789DEF0")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(paymentService.refundPayment(paymentId)).thenReturn(refundedPayment);

        mockMvc.perform(post("/api/payments/" + paymentId + "/refund")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Role", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }
}

