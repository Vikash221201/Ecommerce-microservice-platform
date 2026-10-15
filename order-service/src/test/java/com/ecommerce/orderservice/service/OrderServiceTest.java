package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderEvent;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.kafka.OrderEventProducer;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventProducer eventProducer;

    @InjectMocks
    private OrderService orderService;

    private static final String USER_ID = "user-99";
    private UUID sampleOrderId;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrderId = UUID.randomUUID();
        sampleOrder = new Order();
        sampleOrder.setId(sampleOrderId);
        sampleOrder.setUserId(USER_ID);
        sampleOrder.setStatus(Order.OrderStatus.PENDING);
        sampleOrder.setTotalAmount(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("createOrder computes total amount across multiple items, saves order, and emits Kafka event")
    void testCreateOrder() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(USER_ID);

        OrderItemRequest item1 = new OrderItemRequest();
        item1.setProductId("p1");
        item1.setProductName("Laptop Sleeve");
        item1.setPrice(new BigDecimal("25.00"));
        item1.setQuantity(2); // $50.00

        OrderItemRequest item2 = new OrderItemRequest();
        item2.setProductId("p2");
        item2.setProductName("USB-C Dock");
        item2.setPrice(new BigDecimal("100.00"));
        item2.setQuantity(1); // $100.00

        request.setItems(List.of(item1, item2));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(sampleOrderId);
            return o;
        });

        Order created = orderService.createOrder(request);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(sampleOrderId);
        assertThat(created.getUserId()).isEqualTo(USER_ID);
        assertThat(created.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
        assertThat(created.getTotalAmount()).isEqualByComparingTo("150.00");
        assertThat(created.getItems()).hasSize(2);

        // Verify Kafka event published with correct properties
        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(eventProducer, times(1)).publish(eventCaptor.capture());
        OrderEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getOrderId()).isEqualTo(sampleOrderId.toString());
        assertThat(publishedEvent.getUserId()).isEqualTo(USER_ID);
        assertThat(publishedEvent.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(publishedEvent.getTotalAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("getById returns order when found in repository")
    void testGetById_Found() {
        when(orderRepository.findById(sampleOrderId)).thenReturn(Optional.of(sampleOrder));

        Order result = orderService.getById(sampleOrderId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(sampleOrderId);
        verify(orderRepository, times(1)).findById(sampleOrderId);
    }

    @Test
    @DisplayName("getById throws IllegalArgumentException when order not found")
    void testGetById_NotFound() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");

        verify(orderRepository, times(1)).findById(unknownId);
    }

    @Test
    @DisplayName("getByUser returns list of orders placed by given user")
    void testGetByUser() {
        when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of(sampleOrder));

        List<Order> orders = orderService.getByUser(USER_ID);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getUserId()).isEqualTo(USER_ID);
        verify(orderRepository, times(1)).findByUserId(USER_ID);
    }

    @Test
    @DisplayName("updateStatus updates order status and publishes Kafka event for the transition")
    void testUpdateStatus() {
        when(orderRepository.findById(sampleOrderId)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order updated = orderService.updateStatus(sampleOrderId, Order.OrderStatus.CONFIRMED);

        assertThat(updated.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        verify(orderRepository, times(1)).save(sampleOrder);

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(eventProducer, times(1)).publish(eventCaptor.capture());
        OrderEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(event.getOrderId()).isEqualTo(sampleOrderId.toString());
    }
}
