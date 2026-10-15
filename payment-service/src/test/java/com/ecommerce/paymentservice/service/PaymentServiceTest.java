package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.client.OrderServiceClient;
import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.dto.OrderResponseDto;
import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.model.PaymentMethod;
import com.ecommerce.paymentservice.model.PaymentStatus;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.security.UserContext;
import com.ecommerce.paymentservice.security.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    @InjectMocks
    private PaymentService paymentService;

    private final UUID orderId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final String userId = "user-123";

    @BeforeEach
    void setUp() {
        UserContextHolder.set(UserContext.builder()
                .userId(userId)
                .email("user@example.com")
                .role("ROLE_CUSTOMER")
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void processPayment_Success() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(new BigDecimal("99.99"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .build();

        OrderResponseDto orderDto = OrderResponseDto.builder()
                .id(orderId)
                .userId(userId)
                .totalAmount(new BigDecimal("99.99"))
                .status("PENDING")
                .build();

        when(paymentRepository.existsByOrderId(orderId)).thenReturn(false);
        when(orderServiceClient.getOrder(orderId)).thenReturn(orderDto);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(paymentId);
            return p;
        });

        Payment result = paymentService.processPayment(request);

        assertNotNull(result);
        assertEquals(paymentId, result.getId());
        assertEquals(orderId, result.getOrderId());
        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        assertEquals(PaymentMethod.TEST_SUCCESS, result.getPaymentMethod());
        assertNotNull(result.getTransactionReference());
        assertTrue(result.getTransactionReference().startsWith("TXN-"));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void processPayment_TestFailure_ProducesFailed() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(new BigDecimal("45.00"))
                .paymentMethod(PaymentMethod.TEST_FAILURE)
                .build();

        OrderResponseDto orderDto = OrderResponseDto.builder()
                .id(orderId)
                .userId(userId)
                .totalAmount(new BigDecimal("45.00"))
                .status("PENDING")
                .build();

        when(paymentRepository.existsByOrderId(orderId)).thenReturn(false);
        when(orderServiceClient.getOrder(orderId)).thenReturn(orderDto);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.processPayment(request);

        assertEquals(PaymentStatus.FAILED, result.getStatus());
        assertEquals(PaymentMethod.TEST_FAILURE, result.getPaymentMethod());
    }

    @Test
    void processPayment_DuplicateOrder_ThrowsConflict() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .amount(new BigDecimal("99.99"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .build();

        when(paymentRepository.existsByOrderId(orderId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.processPayment(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_AmountMismatch_ThrowsBadRequest() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(new BigDecimal("50.00"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .build();

        OrderResponseDto orderDto = OrderResponseDto.builder()
                .id(orderId)
                .userId(userId)
                .totalAmount(new BigDecimal("100.00"))
                .status("PENDING")
                .build();

        when(paymentRepository.existsByOrderId(orderId)).thenReturn(false);
        when(orderServiceClient.getOrder(orderId)).thenReturn(orderDto);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.processPayment(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_NonOwner_ThrowsForbidden() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .orderId(orderId)
                .userId("other-user")
                .amount(new BigDecimal("50.00"))
                .paymentMethod(PaymentMethod.TEST_SUCCESS)
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.processPayment(request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getById_Success() {
        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .orderId(orderId)
                .amount(new BigDecimal("99.99"))
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        Payment result = paymentService.getById(paymentId);
        assertNotNull(result);
        assertEquals(paymentId, result.getId());
    }

    @Test
    void getById_NotFound_ThrowsNotFound() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.getById(paymentId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void refundPayment_Success() {
        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .orderId(orderId)
                .amount(new BigDecimal("99.99"))
                .status(PaymentStatus.SUCCESS)
                .transactionReference("TXN-1234567890ABCDEF")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.refundPayment(paymentId);

        assertEquals(PaymentStatus.REFUNDED, result.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void refundPayment_AlreadyRefunded_ThrowsBadRequest() {
        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .orderId(orderId)
                .status(PaymentStatus.REFUNDED)
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.refundPayment(paymentId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundPayment_FailedStatus_ThrowsBadRequest() {
        Payment payment = Payment.builder()
                .id(paymentId)
                .userId(userId)
                .orderId(orderId)
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.refundPayment(paymentId));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(paymentRepository, never()).save(any());
    }
}

