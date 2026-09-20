package com.resilienceops.order.controller;

import com.resilienceops.order.model.*;
import com.resilienceops.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private OrderController orderController;

    @BeforeEach
    void setUp() {
        orderController = new OrderController(orderService);
    }

    @Test
    void shouldReturn201WhenOrderCreatedNormally() {
        Order mockOrder = new Order(
                "ORD-TEST-01",
                "CUST-001",
                List.of(new OrderItem("SKU-1", 1, BigDecimal.TEN)),
                BigDecimal.TEN,
                OrderStatus.CONFIRMED,
                "OK",
                false,
                Instant.now()
        );

        when(orderService.createOrder(any())).thenReturn(mockOrder);

        CreateOrderRequest request = new CreateOrderRequest("CUST-001", List.of(new OrderItem("SKU-1", 1, BigDecimal.TEN)));
        ResponseEntity<OrderResponse> response = orderController.createOrder(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ORD-TEST-01", response.getBody().orderId());
        assertFalse(response.getBody().degradedMode());
    }

    @Test
    void shouldReturn202AcceptedWhenOrderCreatedInDegradedMode() {
        Order degradedOrder = new Order(
                "ORD-TEST-02",
                "CUST-002",
                List.of(new OrderItem("SKU-2", 1, BigDecimal.valueOf(50))),
                BigDecimal.valueOf(50),
                OrderStatus.TENTATIVE_PENDING_INVENTORY,
                "Tentative",
                true,
                Instant.now()
        );

        when(orderService.createOrder(any())).thenReturn(degradedOrder);

        CreateOrderRequest request = new CreateOrderRequest("CUST-002", List.of(new OrderItem("SKU-2", 1, BigDecimal.valueOf(50))));
        ResponseEntity<OrderResponse> response = orderController.createOrder(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().degradedMode());
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {
        when(orderService.getOrderById("NONEXISTENT")).thenReturn(Optional.empty());
        ResponseEntity<OrderResponse> response = orderController.getOrder("NONEXISTENT");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
