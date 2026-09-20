package com.resilienceops.order.model;

import java.math.BigDecimal;

public record OrderItem(
        String sku,
        int quantity,
        BigDecimal unitPrice
) {}
