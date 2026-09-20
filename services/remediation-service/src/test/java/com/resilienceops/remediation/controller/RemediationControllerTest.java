package com.resilienceops.remediation.controller;

import com.resilienceops.remediation.model.*;
import com.resilienceops.remediation.service.RemediationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemediationControllerTest {

    @Mock
    private RemediationEngine remediationEngine;

    private RemediationController remediationController;

    @BeforeEach
    void setUp() {
        remediationController = new RemediationController(remediationEngine);
    }

    @Test
    void shouldProcessFiringAlertsFromWebhook() {
        RemediationAuditRecord mockRecord = new RemediationAuditRecord(
                "REM-001", "MicroserviceHighErrorRate", "resilienceops-apps", "order-service",
                "HELM_ROLLBACK", "SUCCESS", "Rolled back", Instant.now()
        );
        when(remediationEngine.remediate(any())).thenReturn(mockRecord);

        AlertItem alert = new AlertItem(
                "firing",
                Map.of("alertname", "MicroserviceHighErrorRate"),
                Map.of(),
                "2026-09-21T00:00:00Z",
                ""
        );

        AlertmanagerPayload payload = new AlertmanagerPayload(
                "webhook", "firing", List.of(alert), Map.of(), Map.of(), Map.of()
        );

        ResponseEntity<Map<String, Object>> response = remediationController.handleWebhook(payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PROCESSED", response.getBody().get("status"));
        assertEquals(1, response.getBody().get("firingAlerts"));
    }

    @Test
    void shouldReturnRemediationRules() {
        ResponseEntity<Map<String, String>> response = remediationController.getRules();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().containsKey("MicroserviceHighErrorRate"));
        assertTrue(response.getBody().containsKey("JvmMemorySaturation"));
    }
}
