package com.resilienceops.inventory.controller;

import com.resilienceops.inventory.model.ReservationRequest;
import com.resilienceops.inventory.model.ReservationResponse;
import com.resilienceops.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    private InventoryController inventoryController;

    @BeforeEach
    void setUp() {
        inventoryController = new InventoryController(inventoryService);
    }

    @Test
    void shouldReturn200WhenReservationSuccessful() {
        ReservationResponse mockResponse = new ReservationResponse(
                "SKU-LAPTOP-01", "ORD-1", 2, 98, "RESERVED", "OK", Instant.now()
        );
        when(inventoryService.reserveStock(any())).thenReturn(mockResponse);

        ReservationRequest request = new ReservationRequest("SKU-LAPTOP-01", 2, "ORD-1");
        ResponseEntity<ReservationResponse> response = inventoryController.reserve(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RESERVED", response.getBody().status());
    }

    @Test
    void shouldReturn409WhenInsufficientStock() {
        ReservationResponse mockResponse = new ReservationResponse(
                "SKU-LAPTOP-01", "ORD-1", 1000, 10, "INSUFFICIENT_STOCK", "Out of stock", Instant.now()
        );
        when(inventoryService.reserveStock(any())).thenReturn(mockResponse);

        ReservationRequest request = new ReservationRequest("SKU-LAPTOP-01", 1000, "ORD-1");
        ResponseEntity<ReservationResponse> response = inventoryController.reserve(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INSUFFICIENT_STOCK", response.getBody().status());
    }
}
