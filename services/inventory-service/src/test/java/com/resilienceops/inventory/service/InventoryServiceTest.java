package com.resilienceops.inventory.service;

import com.resilienceops.inventory.model.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(new SimpleMeterRegistry());
    }

    @Test
    void shouldReserveStockSuccessfullyWhenAvailable() {
        ReservationRequest request = new ReservationRequest("SKU-LAPTOP-01", 5, "ORD-101");
        ReservationResponse response = inventoryService.reserveStock(request);

        assertNotNull(response);
        assertEquals("RESERVED", response.status());
        assertEquals(5, response.requestedQuantity());
        assertEquals(95, response.remainingStock());
    }

    @Test
    void shouldRejectReservationWhenInsufficientStock() {
        ReservationRequest request = new ReservationRequest("SKU-LAPTOP-01", 9999, "ORD-102");
        ReservationResponse response = inventoryService.reserveStock(request);

        assertNotNull(response);
        assertEquals("INSUFFICIENT_STOCK", response.status());
        assertEquals(100, response.remainingStock());
    }

    @Test
    void shouldReturnNotFoundForUnknownSku() {
        ReservationRequest request = new ReservationRequest("SKU-UNKNOWN-99", 1, "ORD-103");
        ReservationResponse response = inventoryService.reserveStock(request);

        assertEquals("SKU_NOT_FOUND", response.status());
    }

    @Test
    void shouldReleaseReservedStockCorrectly() {
        // Reserve 10
        inventoryService.reserveStock(new ReservationRequest("SKU-LAPTOP-01", 10, "ORD-104"));
        
        // Release 5
        ReservationResponse releaseResponse = inventoryService.releaseReservation(
                new ReservationRequest("SKU-LAPTOP-01", 5, "ORD-104")
        );

        assertEquals("RELEASED", releaseResponse.status());
        assertEquals(95, releaseResponse.remainingStock());
    }

    @Test
    void shouldRestockSuccessfully() {
        RestockRequest request = new RestockRequest("SKU-LAPTOP-01", 50);
        InventoryItem item = inventoryService.restock(request);

        assertEquals(150, item.availableStock());
    }
}
