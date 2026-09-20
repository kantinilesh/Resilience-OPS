package com.resilienceops.inventory.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RestockRequest(
        @NotBlank(message = "sku is required")
        String sku,

        @Min(value = 1, message = "quantity must be at least 1")
        int quantity
) {}
