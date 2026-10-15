package com.ecommerce.orderservice.kafka;

import com.ecommerce.orderservice.dto.OrderEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @InjectMocks
    private OrderEventProducer producer;

    @Test
    void publishesOrderEventToKafkaTopic() {
        String orderId = UUID.randomUUID().toString();
        OrderEvent event = new OrderEvent(orderId, "user-123", "ORDER_CREATED", new BigDecimal("49.99"));

        producer.publish(event);

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate, times(1)).send(eq("order-events"), eq(orderId), eventCaptor.capture());

        OrderEvent captured = eventCaptor.getValue();
        assertThat(captured.getOrderId()).isEqualTo(orderId);
        assertThat(captured.getUserId()).isEqualTo("user-123");
        assertThat(captured.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(captured.getEventId()).isNotBlank();
        assertThat(captured.getOccurredAt()).isNotNull();
    }
}
