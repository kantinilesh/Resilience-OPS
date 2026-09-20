package com.resilienceops.inventory.service;

import com.resilienceops.inventory.model.*;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final Map<String, InventoryItem> inventoryDb = new ConcurrentHashMap<>();

    private final Counter reservationSuccessCounter;
    private final Counter outOfStockCounter;

    public InventoryService(MeterRegistry meterRegistry) {
        this.reservationSuccessCounter = Counter.builder("inventory_reservations_total")
                .description("Total successful stock reservations")
                .tag("result", "SUCCESS")
                .register(meterRegistry);

        this.outOfStockCounter = Counter.builder("inventory_reservations_total")
                .description("Total stock reservations rejected due to insufficient inventory")
                .tag("result", "INSUFFICIENT_STOCK")
                .register(meterRegistry);

        // Seed initial warehouse catalog
        initializeCatalog();
    }

    private void initializeCatalog() {
        inventoryDb.put("SKU-LAPTOP-01", new InventoryItem("SKU-LAPTOP-01", "Enterprise Laptop 15-inch", 100, 0, 1L, Instant.now()));
        inventoryDb.put("SKU-PHONE-02", new InventoryItem("SKU-PHONE-02", "Pro Smartphone 5G", 250, 0, 1L, Instant.now()));
        inventoryDb.put("SKU-KEYBOARD-03", new InventoryItem("SKU-KEYBOARD-03", "Wireless Mechanical Keyboard", 80, 0, 1L, Instant.now()));
        inventoryDb.put("SKU-MOUSE-02", new InventoryItem("SKU-MOUSE-02", "Precision Ergonomic Mouse", 150, 0, 1L, Instant.now()));
    }

    @RateLimiter(name = "inventoryApi")
    @Bulkhead(name = "inventoryApi")
    public synchronized ReservationResponse reserveStock(ReservationRequest request) {
        log.info("Processing reservation request: sku={}, qty={}, orderId={}",
                request.sku(), request.quantity(), request.orderId());

        InventoryItem item = inventoryDb.get(request.sku());
        if (item == null) {
            log.warn("SKU not found in catalog: {}", request.sku());
            outOfStockCounter.increment();
            return new ReservationResponse(
                    request.sku(),
                    request.orderId(),
                    request.quantity(),
                    0,
                    "SKU_NOT_FOUND",
                    "Product SKU does not exist in inventory catalog",
                    Instant.now()
            );
        }

        if (item.availableStock() >= request.quantity()) {
            InventoryItem updated = item.withReserved(request.quantity());
            inventoryDb.put(request.sku(), updated);
            reservationSuccessCounter.increment();
            log.info("Stock reserved successfully: sku={}, remaining={}", request.sku(), updated.availableStock());

            return new ReservationResponse(
                    request.sku(),
                    request.orderId(),
                    request.quantity(),
                    updated.availableStock(),
                    "RESERVED",
                    "Stock successfully reserved",
                    Instant.now()
            );
        } else {
            outOfStockCounter.increment();
            log.warn("Insufficient stock for SKU: {}. Requested: {}, Available: {}",
                    request.sku(), request.quantity(), item.availableStock());

            return new ReservationResponse(
                    request.sku(),
                    request.orderId(),
                    request.quantity(),
                    item.availableStock(),
                    "INSUFFICIENT_STOCK",
                    "Available stock is lower than requested quantity",
                    Instant.now()
            );
        }
    }

    public synchronized ReservationResponse releaseReservation(ReservationRequest request) {
        log.info("Releasing reservation for SKU: {}, qty={}, orderId={}", request.sku(), request.quantity(), request.orderId());
        InventoryItem item = inventoryDb.get(request.sku());
        if (item == null) {
            return new ReservationResponse(request.sku(), request.orderId(), request.quantity(), 0, "SKU_NOT_FOUND", "SKU not found", Instant.now());
        }

        int releaseQty = Math.min(request.quantity(), item.reservedStock());
        InventoryItem updated = item.withReleased(releaseQty);
        inventoryDb.put(request.sku(), updated);

        return new ReservationResponse(
                request.sku(),
                request.orderId(),
                releaseQty,
                updated.availableStock(),
                "RELEASED",
                "Reservation released back to available pool",
                Instant.now()
        );
    }

    public synchronized InventoryItem restock(RestockRequest request) {
        InventoryItem item = inventoryDb.compute(request.sku(), (sku, current) -> {
            if (current == null) {
                return new InventoryItem(sku, "Product " + sku, request.quantity(), 0, 1L, Instant.now());
            }
            return current.withRestocked(request.quantity());
        });
        log.info("SKU restocked: sku={}, newAvailableStock={}", request.sku(), item.availableStock());
        return item;
    }

    public Optional<InventoryItem> getItem(String sku) {
        return Optional.ofNullable(inventoryDb.get(sku));
    }

    public List<InventoryItem> getAllItems() {
        return new ArrayList<>(inventoryDb.values());
    }
}
