package com.resilienceops.inventory.controller;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);
    private static final Map<String, Integer> STOCK_DB = new ConcurrentHashMap<>();

    static {
        STOCK_DB.put("SKU-LAPTOP-01", 100);
        STOCK_DB.put("SKU-PHONE-02", 250);
    }

    @PostMapping("/reserve")
    @RateLimiter(name = "inventoryApi")
    @Bulkhead(name = "inventoryApi")
    public ResponseEntity<Map<String, Object>> reserveStock(@RequestBody Map<String, Object> request) {
        String sku = (String) request.getOrDefault("sku", "SKU-LAPTOP-01");
        int quantity = (int) request.getOrDefault("quantity", 1);

        log.info("Attempting to reserve {} items for SKU: {}", quantity, sku);
        int available = STOCK_DB.getOrDefault(sku, 0);

        if (available >= quantity) {
            STOCK_DB.put(sku, available - quantity);
            return ResponseEntity.ok(Map.of(
                    "sku", sku,
                    "reservedQuantity", quantity,
                    "remainingStock", STOCK_DB.get(sku),
                    "status", "RESERVED"
            ));
        }

        return ResponseEntity.status(409).body(Map.of(
                "sku", sku,
                "requestedQuantity", quantity,
                "availableStock", available,
                "status", "INSUFFICIENT_STOCK"
        ));
    }

    @GetMapping("/{sku}/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(@PathVariable String sku) {
        int available = STOCK_DB.getOrDefault(sku, 0);
        return ResponseEntity.ok(Map.of(
                "sku", sku,
                "availableStock", available
        ));
    }
}
