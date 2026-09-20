package com.resilienceops.order.model;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    TENTATIVE_PENDING_INVENTORY,
    FAILED,
    CANCELLED
}
