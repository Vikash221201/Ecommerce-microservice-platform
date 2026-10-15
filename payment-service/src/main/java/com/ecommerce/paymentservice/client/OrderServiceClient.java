package com.ecommerce.paymentservice.client;

import com.ecommerce.paymentservice.dto.OrderResponseDto;
import com.ecommerce.paymentservice.security.UserContext;
import com.ecommerce.paymentservice.security.UserContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class OrderServiceClient {

    private final RestClient restClient;

    public OrderServiceClient(@Value("${services.order-service.url:http://order-service:8084}") String orderServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(orderServiceUrl)
                .build();
    }

    public OrderResponseDto getOrder(UUID orderId) {
        UserContext ctx = UserContextHolder.get();

        try {
            return restClient.get()
                    .uri("/api/orders/{id}", orderId)
                    .headers(headers -> {
                        if (ctx != null) {
                            if (ctx.getRawAuthHeader() != null) {
                                headers.set("Authorization", ctx.getRawAuthHeader());
                            }
                            if (ctx.getUserId() != null) {
                                headers.set("X-User-Id", ctx.getUserId());
                            }
                            if (ctx.getEmail() != null) {
                                headers.set("X-User-Email", ctx.getEmail());
                            }
                            if (ctx.getRole() != null) {
                                headers.set("X-User-Role", ctx.getRole());
                            }
                        }
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, resp) -> {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with ID: " + orderId);
                    })
                    .onStatus(status -> status.value() == 403, (req, resp) -> {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to order: " + orderId);
                    })
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw new ResponseStatusException(HttpStatus.valueOf(resp.getStatusCode().value()),
                                "Order verification failed: " + resp.getStatusText());
                    })
                    .body(OrderResponseDto.class);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Order service communication error: " + ex.getMessage(), ex);
        }
    }
}

