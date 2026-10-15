package com.ecommerce.apigateway.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalErrorExceptionHandlerTest {

    private GlobalErrorExceptionHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new GlobalErrorExceptionHandler(objectMapper);
    }

    @Test
    void shouldHandleTimeoutExceptionWith504() throws IOException {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products/123").build()
        );

        TimeoutException ex = new TimeoutException("Connection timed out");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.getResponse().getStatusCode());

        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        JsonNode json = objectMapper.readTree(body);
        assertEquals(504, json.get("status").asInt());
        assertEquals("Gateway Timeout", json.get("error").asText());
        assertEquals("Gateway timeout while waiting for downstream response", json.get("message").asText());
        assertEquals("/api/products/123", json.get("path").asText());
        assertTrue(json.has("timestamp"));
    }

    @Test
    void shouldHandleConnectExceptionWith503() throws IOException {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders/order-1").build()
        );

        ConnectException ex = new ConnectException("Connection refused: localhost/127.0.0.1:8084");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());

        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        JsonNode json = objectMapper.readTree(body);
        assertEquals(503, json.get("status").asInt());
        assertEquals("Service Unavailable", json.get("error").asText());
        assertEquals("Downstream service unavailable", json.get("message").asText());
        assertEquals("/api/orders/order-1", json.get("path").asText());
    }

    @Test
    void shouldHandleNestedConnectExceptionWith503() throws IOException {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cart").build()
        );

        RuntimeException wrapperEx = new RuntimeException("Routing failed", new ConnectException("Connection refused"));

        StepVerifier.create(handler.handle(exchange, wrapperEx))
                .verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());

        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        JsonNode json = objectMapper.readTree(body);
        assertEquals(503, json.get("status").asInt());
    }

    @Test
    void shouldHandleResponseStatusException() throws IOException {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/profile").build()
        );

        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());

        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        JsonNode json = objectMapper.readTree(body);
        assertEquals(404, json.get("status").asInt());
        assertEquals("Not Found", json.get("error").asText());
        assertEquals("User profile not found", json.get("message").asText());
    }

    @Test
    void shouldHandleGenericExceptionWith502() throws IOException {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/unknown").build()
        );

        RuntimeException ex = new RuntimeException("Unexpected gateway processing failure");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());

        String body = exchange.getResponse().getBodyAsString().block();
        assertNotNull(body);
        JsonNode json = objectMapper.readTree(body);
        assertEquals(502, json.get("status").asInt());
        assertEquals("Bad Gateway", json.get("error").asText());
        assertTrue(json.get("message").asText().contains("Unexpected gateway processing failure"));
    }
}

