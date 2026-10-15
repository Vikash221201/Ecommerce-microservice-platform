package com.ecommerce.paymentservice;

import com.ecommerce.paymentservice.controller.PaymentController;
import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.model.PaymentMethod;
import com.ecommerce.paymentservice.model.PaymentStatus;
import com.ecommerce.paymentservice.security.JwtValidator;
import com.ecommerce.paymentservice.security.SecurityFilter;
import com.ecommerce.paymentservice.service.PaymentService;
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
class PaymentSecurityTest {

    private static final String SECRET = "change-this-super-secret-key-min-32-chars-long";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

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
    void createPayment_WithValidBearerToken_Succeeds() throws Exception {
        String token = generateToken("cust-55", "cust55@example.com", "ROLE_CUSTOMER");
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .userId("cust-55")
                .amount(new BigDecimal("75.00"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .build();

        Payment payment = Payment.builder()
                .id(paymentId)
                .orderId(orderId)
                .userId("cust-55")
                .amount(new BigDecimal("75.00"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .status(PaymentStatus.SUCCESS)
                .transactionReference("TXN-VALIDTOKEN12345")
                .build();

        when(paymentService.processPayment(any())).thenReturn(payment);

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void getPayment_WithDirectHeaders_Succeeds() throws Exception {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .orderId(UUID.randomUUID())
                .userId("cust-55")
                .amount(new BigDecimal("75.00"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .status(PaymentStatus.SUCCESS)
                .transactionReference("TXN-DIRECTHEADERS")
                .build();

        when(paymentService.getById(paymentId)).thenReturn(payment);

        mockMvc.perform(get("/api/payments/" + paymentId)
                        .header("X-User-Id", "cust-55")
                        .header("X-User-Email", "cust55@example.com")
                        .header("X-User-Role", "ROLE_CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()));
    }
}

