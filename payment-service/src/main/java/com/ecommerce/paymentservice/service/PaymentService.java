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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderServiceClient orderServiceClient;

    @Transactional
    public Payment processPayment(CreatePaymentRequest request) {
        UserContext ctx = UserContextHolder.get();
        if (ctx == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required to initiate payment");
        }

        String callerUserId = ctx.getUserId() != null ? ctx.getUserId() : ctx.getEmail();
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            request.setUserId(callerUserId);
        } else if (!ctx.isAdmin() && !request.getUserId().equalsIgnoreCase(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: cannot initiate payment for another user");
        }

        if (paymentRepository.existsByOrderId(request.getOrderId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A payment already exists for order " + request.getOrderId());
        }

        OrderResponseDto order = orderServiceClient.getOrder(request.getOrderId());
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with ID: " + request.getOrderId());
        }

        if (!ctx.isAdmin()) {
            boolean matchesOrderUser = (order.getUserId() != null && order.getUserId().equalsIgnoreCase(callerUserId))
                    || (ctx.getEmail() != null && ctx.getEmail().equalsIgnoreCase(order.getUserId()));
            if (!matchesOrderUser) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: order does not belong to the authenticated user");
            }
        }

        if (order.getTotalAmount() != null && request.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Payment amount (%.2f) does not match order total amount (%.2f)",
                            request.getAmount(), order.getTotalAmount()));
        }

        PaymentStatus status = simulatePayment(request.getPaymentMethod());
        String transactionReference = generateTransactionReference();

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(order.getUserId() != null ? order.getUserId() : callerUserId)
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .status(status)
                .transactionReference(transactionReference)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment processed: ID={}, orderId={}, status={}, method={}, ref={}",
                saved.getId(), saved.getOrderId(), saved.getStatus(), saved.getPaymentMethod(), saved.getTransactionReference());
        return saved;
    }

    public Payment getById(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with ID: " + paymentId));
        UserContextHolder.checkOwnership(payment.getUserId());
        return payment;
    }

    public Payment getByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found for order: " + orderId));
        UserContextHolder.checkOwnership(payment.getUserId());
        return payment;
    }

    @Transactional
    public Payment refundPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found with ID: " + paymentId));

        UserContextHolder.checkOwnership(payment.getUserId());

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment has already been refunded");
        }

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot refund payment with status: " + payment.getStatus() + ". Only SUCCESS payments can be refunded.");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(Instant.now());

        Payment refunded = paymentRepository.save(payment);
        log.info("Payment refunded: ID={}, orderId={}, ref={}", refunded.getId(), refunded.getOrderId(), refunded.getTransactionReference());
        return refunded;
    }

    private PaymentStatus simulatePayment(PaymentMethod method) {
        if (method == PaymentMethod.TEST_FAILURE) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.SUCCESS;
    }

    private String generateTransactionReference() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}

