package com.resilienceops.inventory.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReservationRequest(
        @NotBlank(message = "sku is required")
        String sku,

        @Min(value = 1, message = "quantity must be at least 1")
        int quantity,

        @NotBlank(message = "orderId is required")
        String orderId
) {}
