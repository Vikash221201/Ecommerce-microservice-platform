package com.ecommerce.orderservice.kafka;

import com.ecommerce.orderservice.dto.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private static final String TOPIC = "order-events";

    public void publish(OrderEvent event) {
        log.info("Publishing {} event {} for order {} to Kafka topic {}",
                event.getEventType(), event.getEventId(), event.getOrderId(), TOPIC);
        kafkaTemplate.send(TOPIC, event.getOrderId(), event);
    }
}
