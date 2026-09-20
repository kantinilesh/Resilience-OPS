package com.resilienceops.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        String customerId,
        List<OrderItem> items,
        BigDecimal totalAmount,
        OrderStatus status,
        String message,
        boolean degradedMode,
        Instant timestamp
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.orderId(),
                order.customerId(),
                order.items(),
                order.totalAmount(),
                order.status(),
                order.statusMessage(),
                order.degradedMode(),
                order.createdAt()
        );
    }
}
