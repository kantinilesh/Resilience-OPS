package com.resilienceops.inventory.chaos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InventoryChaosControllerTest {

    private ChaosHealthIndicator healthIndicator;
    private InventoryChaosController chaosController;

    @BeforeEach
    void setUp() {
        healthIndicator = new ChaosHealthIndicator();
        chaosController = new InventoryChaosController(healthIndicator);
    }

    @Test
    void shouldTripLivenessHealthIndicator() {
        assertTrue(healthIndicator.isHealthy());

        ResponseEntity<Map<String, Object>> response = chaosController.simulateFailure(
                Map.of("action", "liveness-fail")
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(healthIndicator.isHealthy());
        assertEquals("DOWN", healthIndicator.health().getStatus().getCode());
    }

    @Test
    void shouldRestoreHealthOnReset() {
        healthIndicator.tripLiveness("Testing");
        assertFalse(healthIndicator.isHealthy());

        ResponseEntity<Map<String, Object>> response = chaosController.simulateFailure(
                Map.of("action", "reset")
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(healthIndicator.isHealthy());
    }

    @Test
    void shouldAllocateMemoryLeak() {
        ResponseEntity<Map<String, Object>> response = chaosController.simulateFailure(
                Map.of("action", "memory-leak", "megabytes", 1)
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ALLOCATED", response.getBody().get("status"));
    }
}
