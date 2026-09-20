package com.resilienceops.order.controller;

import com.resilienceops.order.model.CreateOrderRequest;
import com.resilienceops.order.model.Order;
import com.resilienceops.order.model.OrderResponse;
import com.resilienceops.order.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        HttpStatus status = order.degradedMode() ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        return orderService.getOrderById(orderId)
                .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> listOrders() {
        List<OrderResponse> orders = orderService.getAllOrders().stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(orders);
    }
}
