package com.ecommerce.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {
    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private String orderId;
    private String userId;
    private BigDecimal totalAmount;
}
