package com.ecommerce.orderservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateOrderRequest {
    private String userId;
    private List<OrderItemRequest> items;
}
