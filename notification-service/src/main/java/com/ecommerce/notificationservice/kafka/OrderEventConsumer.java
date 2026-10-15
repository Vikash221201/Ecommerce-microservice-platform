package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.dto.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class OrderEventConsumer {

    // Idempotency guard: tracks processed event IDs to prevent duplicate actions on redelivery
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    @KafkaListener(topics = "order-events", groupId = "notification-service-group")
    public void consume(OrderEvent event) {
        if (event.getEventId() != null && !processedEventIds.add(event.getEventId())) {
            log.warn("Duplicate event {} detected for order {}, skipping notification.",
                    event.getEventId(), event.getOrderId());
            return;
        }

        // In production this would invoke a notification provider (email, SMS, push)
        log.info("Notification triggered [Event: {}]: order {} for user {} -> {} (total: {})",
                event.getEventId(), event.getOrderId(), event.getUserId(), event.getEventType(), event.getTotalAmount());
    }

    public boolean isProcessed(String eventId) {
        return eventId != null && processedEventIds.contains(eventId);
    }
}
