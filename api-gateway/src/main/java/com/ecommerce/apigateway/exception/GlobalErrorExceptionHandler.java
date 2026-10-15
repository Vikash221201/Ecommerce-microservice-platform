package com.ecommerce.apigateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GlobalErrorExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GlobalErrorExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public GlobalErrorExceptionHandler() {
        this(new ObjectMapper());
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            HttpStatusCode statusCode = rse.getStatusCode();
            status = HttpStatus.resolve(statusCode.value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            message = rse.getReason() != null ? rse.getReason() : ex.getMessage();
        } else if (hasCause(ex, TimeoutException.class, io.netty.handler.timeout.ReadTimeoutException.class)
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout"))) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            message = "Gateway timeout while waiting for downstream response";
        } else if (hasCause(ex, ConnectException.class)
                || (ex.getMessage() != null && (ex.getMessage().toLowerCase().contains("connection refused")
                || ex.getMessage().toLowerCase().contains("finishconnect")))) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Downstream service unavailable";
        } else {
            status = HttpStatus.BAD_GATEWAY;
            message = "Error routing request through gateway: " + ex.getMessage();
        }

        log.error("Gateway error [{}] for path {}: {}", status, exchange.getRequest().getPath(), message);

        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("status", status.value());
        errorAttributes.put("error", status.getReasonPhrase());
        errorAttributes.put("message", message);
        errorAttributes.put("path", exchange.getRequest().getPath().value());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorAttributes);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}").getBytes();
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private boolean hasCause(Throwable ex, Class<?>... classes) {
        Throwable current = ex;
        while (current != null) {
            for (Class<?> clazz : classes) {
                if (clazz.isInstance(current)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}

