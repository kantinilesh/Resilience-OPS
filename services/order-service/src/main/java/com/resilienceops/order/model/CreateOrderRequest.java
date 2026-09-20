package com.resilienceops.order.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "customerId is required")
        String customerId,

        @NotEmpty(message = "Order must contain at least one item")
        List<OrderItem> items
) {}
