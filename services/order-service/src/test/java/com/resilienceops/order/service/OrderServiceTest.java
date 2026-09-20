package com.resilienceops.order.service;

import com.resilienceops.order.model.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(new SimpleMeterRegistry());
    }

    @Test
    void shouldCreateConfirmedOrderSuccessfully() {
        OrderItem item1 = new OrderItem("SKU-LAPTOP-01", 1, new BigDecimal("1200.00"));
        OrderItem item2 = new OrderItem("SKU-MOUSE-02", 2, new BigDecimal("25.00"));
        CreateOrderRequest request = new CreateOrderRequest("CUST-1001", List.of(item1, item2));

        Order order = orderService.createOrder(request);

        assertNotNull(order);
        assertNotNull(order.orderId());
        assertTrue(order.orderId().startsWith("ORD-"));
        assertEquals("CUST-1001", order.customerId());
        assertEquals(new BigDecimal("1250.00"), order.totalAmount());
        assertEquals(OrderStatus.CONFIRMED, order.status());
        assertFalse(order.degradedMode());
    }

    @Test
    void shouldFallbackToTentativeOrderWhenExceptionOccurs() {
        OrderItem failingItem = new OrderItem("SKU-TRIGGER-FAIL", 1, new BigDecimal("500.00"));
        CreateOrderRequest request = new CreateOrderRequest("CUST-1002", List.of(failingItem));

        Order fallbackOrder = orderService.createOrderFallback(request, new RuntimeException("Inventory service down"));

        assertNotNull(fallbackOrder);
        assertEquals(OrderStatus.TENTATIVE_PENDING_INVENTORY, fallbackOrder.status());
        assertTrue(fallbackOrder.degradedMode());
        assertEquals(new BigDecimal("500.00"), fallbackOrder.totalAmount());
    }

    @Test
    void shouldRetrieveOrderById() {
        OrderItem item = new OrderItem("SKU-KEYBOARD-03", 1, new BigDecimal("75.00"));
        CreateOrderRequest request = new CreateOrderRequest("CUST-1003", List.of(item));
        Order created = orderService.createOrder(request);

        var retrieved = orderService.getOrderById(created.orderId());
        assertTrue(retrieved.isPresent());
        assertEquals(created.orderId(), retrieved.get().orderId());
    }
}
