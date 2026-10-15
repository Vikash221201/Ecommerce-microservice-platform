package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderEvent;
import com.ecommerce.orderservice.kafka.OrderEventProducer;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer eventProducer;

    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setUserId(request.getUserId());

        BigDecimal total = BigDecimal.ZERO;
        for (var itemReq : request.getItems()) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(itemReq.getProductId());
            item.setProductName(itemReq.getProductName());
            item.setPrice(itemReq.getPrice());
            item.setQuantity(itemReq.getQuantity());
            order.getItems().add(item);
            total = total.add(itemReq.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }
        order.setTotalAmount(total);
        order.setStatus(Order.OrderStatus.PENDING);

        Order saved = orderRepository.save(order);

        // Fire-and-forget event so notification-service (and inventory, etc.) can react asynchronously
        eventProducer.publish(new OrderEvent(
                saved.getId().toString(), saved.getUserId(), "ORDER_CREATED", saved.getTotalAmount()));

        return saved;
    }

    public Order getById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    public List<Order> getByUser(String userId) {
        return orderRepository.findByUserId(userId);
    }

    public Order updateStatus(UUID id, Order.OrderStatus status) {
        Order order = getById(id);
        order.setStatus(status);
        Order saved = orderRepository.save(order);
        eventProducer.publish(new OrderEvent(
                saved.getId().toString(), saved.getUserId(), "ORDER_" + status.name(), saved.getTotalAmount()));
        return saved;
    }
}
