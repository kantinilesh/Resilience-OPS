package com.resilienceops.inventory.model;

import java.time.Instant;

public record ReservationResponse(
        String sku,
        String orderId,
        int requestedQuantity,
        int remainingStock,
        String status,
        String message,
        Instant timestamp
) {}
