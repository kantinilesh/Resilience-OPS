package com.resilienceops.inventory.controller;

import com.resilienceops.inventory.model.*;
import com.resilienceops.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        ReservationResponse response = inventoryService.reserveStock(request);
        if ("RESERVED".equals(response.status())) {
            return ResponseEntity.ok(response);
        } else if ("INSUFFICIENT_STOCK".equals(response.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @PostMapping("/release")
    public ResponseEntity<ReservationResponse> release(@Valid @RequestBody ReservationRequest request) {
        ReservationResponse response = inventoryService.releaseReservation(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/restock")
    public ResponseEntity<InventoryItem> restock(@Valid @RequestBody RestockRequest request) {
        InventoryItem item = inventoryService.restock(request);
        return ResponseEntity.ok(item);
    }

    @GetMapping("/{sku}")
    public ResponseEntity<InventoryItem> getItem(@PathVariable String sku) {
        return inventoryService.getItem(sku)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<InventoryItem>> listAll() {
        return ResponseEntity.ok(inventoryService.getAllItems());
    }
}
