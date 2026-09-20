package com.resilienceops.order.controller;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private static final String INVENTORY_SERVICE = "inventoryService";

    @PostMapping
    @CircuitBreaker(name = INVENTORY_SERVICE, fallbackMethod = "createOrderFallback")
    @Bulkhead(name = INVENTORY_SERVICE)
    @Retry(name = INVENTORY_SERVICE)
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> orderRequest) {
        log.info("Processing order request: {}", orderRequest);
        
        // Simulating inter-service inventory reservation call
        String orderId = UUID.randomUUID().toString();
        
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", "CONFIRMED",
                "message", "Order placed and inventory reserved successfully."
        ));
    }

    /**
     * Self-healing / graceful degradation fallback when inventory-service is unavailable or slow
     */
    public ResponseEntity<Map<String, Object>> createOrderFallback(Map<String, Object> orderRequest, Throwable throwable) {
        log.warn("Downstream inventory-service failure. Tripping circuit breaker or invoking fallback: {}", throwable.getMessage());
        
        String orderId = UUID.randomUUID().toString();
        return ResponseEntity.accepted().body(Map.of(
                "orderId", orderId,
                "status", "TENTATIVE_PENDING_INVENTORY",
                "message", "Order queued in tentative mode due to temporary service degradation. Autonomous recovery in progress.",
                "degradedMode", true
        ));
    }
}
