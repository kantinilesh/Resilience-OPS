package com.resilienceops.inventory.model;

import java.time.Instant;

public record InventoryItem(
        String sku,
        String productName,
        int availableStock,
        int reservedStock,
        long version,
        Instant updatedAt
) {
    public InventoryItem withReserved(int qty) {
        return new InventoryItem(
                this.sku,
                this.productName,
                this.availableStock - qty,
                this.reservedStock + qty,
                this.version + 1,
                Instant.now()
        );
    }

    public InventoryItem withReleased(int qty) {
        return new InventoryItem(
                this.sku,
                this.productName,
                this.availableStock + qty,
                this.reservedStock - qty,
                this.version + 1,
                Instant.now()
        );
    }

    public InventoryItem withRestocked(int qty) {
        return new InventoryItem(
                this.sku,
                this.productName,
                this.availableStock + qty,
                this.reservedStock,
                this.version + 1,
                Instant.now()
        );
    }
}
