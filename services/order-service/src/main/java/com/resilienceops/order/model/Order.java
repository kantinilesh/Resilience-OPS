package com.resilienceops.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Order(
        String orderId,
        String customerId,
        List<OrderItem> items,
        BigDecimal totalAmount,
        OrderStatus status,
        String statusMessage,
        boolean degradedMode,
        Instant createdAt
) {}
