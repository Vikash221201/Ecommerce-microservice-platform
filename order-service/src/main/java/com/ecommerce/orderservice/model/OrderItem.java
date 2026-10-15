package com.ecommerce.orderservice.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Data
public class OrderItem {

    @Id
    @GeneratedValue
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonBackReference
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    private String productId;
    private String productName;
    private BigDecimal price;
    private int quantity;
}
