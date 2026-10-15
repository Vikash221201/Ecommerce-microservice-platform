package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.dto.OrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventConsumerTest {

    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer();
    }

    @Test
    void consumesOrderEventSuccessfully() {
        String eventId = UUID.randomUUID().toString();
        OrderEvent event = OrderEvent.builder()
                .eventId(eventId)
                .orderId(UUID.randomUUID().toString())
                .userId("jane@example.com")
                .eventType("ORDER_CREATED")
                .totalAmount(new BigDecimal("99.95"))
                .occurredAt(Instant.now())
                .build();

        consumer.consume(event);

        assertThat(consumer.isProcessed(eventId)).isTrue();
    }

    @Test
    void handlesDuplicateEventsIdempotently() {
        String eventId = UUID.randomUUID().toString();
        OrderEvent event = OrderEvent.builder()
                .eventId(eventId)
                .orderId(UUID.randomUUID().toString())
                .userId("jane@example.com")
                .eventType("ORDER_CREATED")
                .totalAmount(new BigDecimal("99.95"))
                .occurredAt(Instant.now())
                .build();

        // First delivery
        consumer.consume(event);
        assertThat(consumer.isProcessed(eventId)).isTrue();

        // Repeated delivery with same eventId
        consumer.consume(event);
        assertThat(consumer.isProcessed(eventId)).isTrue();
    }

    @Test
    void consumesDifferentEventTypesSuccessfully() {
        String confirmedId = UUID.randomUUID().toString();
        OrderEvent confirmedEvent = OrderEvent.builder()
                .eventId(confirmedId)
                .orderId(UUID.randomUUID().toString())
                .userId("bob@example.com")
                .eventType("ORDER_CONFIRMED")
                .totalAmount(new BigDecimal("150.00"))
                .occurredAt(Instant.now())
                .build();

        consumer.consume(confirmedEvent);
        assertThat(consumer.isProcessed(confirmedId)).isTrue();

        String cancelledId = UUID.randomUUID().toString();
        OrderEvent cancelledEvent = OrderEvent.builder()
                .eventId(cancelledId)
                .orderId(UUID.randomUUID().toString())
                .userId("bob@example.com")
                .eventType("ORDER_CANCELLED")
                .totalAmount(new BigDecimal("150.00"))
                .occurredAt(Instant.now())
                .build();

        consumer.consume(cancelledEvent);
        assertThat(consumer.isProcessed(cancelledId)).isTrue();
    }

    @Test
    void handlesNullEventIdSafely() {
        OrderEvent nullIdEvent = OrderEvent.builder()
                .eventId(null)
                .orderId(UUID.randomUUID().toString())
                .userId("anonymous@example.com")
                .eventType("ORDER_CREATED")
                .totalAmount(new BigDecimal("29.99"))
                .occurredAt(Instant.now())
                .build();

        // Should not throw NullPointerException
        consumer.consume(nullIdEvent);
        assertThat(consumer.isProcessed(null)).isFalse();
    }
}
