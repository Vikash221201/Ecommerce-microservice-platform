package com.ecommerce.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Published to Kafka topic "order-events" — consumed by notification-service
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {
    private String eventId;
    private String eventType; // ORDER_CREATED, ORDER_CONFIRMED, ORDER_CANCELLED
    private Instant occurredAt;
    private String orderId;
    private String userId;
    private BigDecimal totalAmount;

    public OrderEvent(String orderId, String userId, String eventType, BigDecimal totalAmount) {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.orderId = orderId;
        this.userId = userId;
        this.eventType = eventType;
        this.totalAmount = totalAmount;
    }
}
