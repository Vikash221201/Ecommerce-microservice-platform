package com.ecommerce.orderservice.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderItemRequest {
    private String productId;
    private String productName;
    private BigDecimal price;
    private int quantity;
}
