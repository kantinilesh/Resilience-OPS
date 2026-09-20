package com.resilienceops.order.service;

import com.resilienceops.order.model.*;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String INVENTORY_SERVICE = "inventoryService";

    private final Map<String, Order> orderRepository = new ConcurrentHashMap<>();
    private final Counter ordersCreatedCounter;
    private final Counter degradedOrdersCounter;
    private final Timer orderProcessingTimer;

    public OrderService(MeterRegistry meterRegistry) {
        this.ordersCreatedCounter = Counter.builder("orders_created_total")
                .description("Total number of orders successfully created")
                .tag("status", "CONFIRMED")
                .register(meterRegistry);

        this.degradedOrdersCounter = Counter.builder("orders_created_total")
                .description("Total number of orders created in degraded tentative mode")
                .tag("status", "TENTATIVE")
                .register(meterRegistry);

        this.orderProcessingTimer = Timer.builder("order_processing_duration_seconds")
                .description("Time taken to process and confirm order creation")
                .register(meterRegistry);
    }

    @CircuitBreaker(name = INVENTORY_SERVICE, fallbackMethod = "createOrderFallback")
    @Bulkhead(name = INVENTORY_SERVICE)
    @Retry(name = INVENTORY_SERVICE)
    public Order createOrder(CreateOrderRequest request) {
        return orderProcessingTimer.record(() -> {
            String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("Initiating order creation: orderId={}, customerId={}, itemCount={}",
                    orderId, request.customerId(), request.items().size());

            // Compute total amount
            BigDecimal total = request.items().stream()
                    .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Simulating call to inventory-service
            callInventoryService(request.items());

            Order order = new Order(
                    orderId,
                    request.customerId(),
                    request.items(),
                    total,
                    OrderStatus.CONFIRMED,
                    "Order placed and inventory reserved successfully.",
                    false,
                    Instant.now()
            );

            orderRepository.put(orderId, order);
            ordersCreatedCounter.increment();
            log.info("Order successfully confirmed: orderId={}, total={}", orderId, total);
            return order;
        });
    }

    /**
     * Fallback method called when Resilience4j circuit breaker trips, bulkheads saturate, or calls time out.
     */
    public Order createOrderFallback(CreateOrderRequest request, Throwable throwable) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.warn("Downstream inventory call failed or circuit breaker active ({}). Gracefully falling back to Tentative Order Mode.",
                throwable.getMessage());

        BigDecimal total = request.items().stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order degradedOrder = new Order(
                orderId,
                request.customerId(),
                request.items(),
                total,
                OrderStatus.TENTATIVE_PENDING_INVENTORY,
                "Order accepted in tentative mode. Inventory reservation queued asynchronously.",
                true,
                Instant.now()
        );

        orderRepository.put(orderId, degradedOrder);
        degradedOrdersCounter.increment();
        return degradedOrder;
    }

    public Optional<Order> getOrderById(String orderId) {
        return Optional.ofNullable(orderRepository.get(orderId));
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orderRepository.values());
    }

    protected void callInventoryService(List<OrderItem> items) {
        // Inter-service call placeholder or HTTP client call
        // In unit tests or mock simulations, this can be spied or hooked
        for (OrderItem item : items) {
            if ("SKU-TRIGGER-FAIL".equalsIgnoreCase(item.sku())) {
                throw new RuntimeException("Simulated downstream inventory outage for SKU-TRIGGER-FAIL");
            }
        }
    }
}
