package com.resilienceops.remediation.service;

import com.resilienceops.remediation.model.AlertItem;
import com.resilienceops.remediation.model.RemediationAuditRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RemediationEngineTest {

    private RemediationEngine remediationEngine;

    @BeforeEach
    void setUp() {
        remediationEngine = new RemediationEngine(new SimpleMeterRegistry());
    }

    @Test
    void shouldExecuteHelmRollbackOnMicroserviceHighErrorRate() {
        AlertItem alert = new AlertItem(
                "firing",
                Map.of("alertname", "MicroserviceHighErrorRate", "service", "order-service", "namespace", "resilienceops-apps"),
                Map.of("summary", "5xx spike detected"),
                "2026-09-21T00:00:00Z",
                ""
        );

        RemediationAuditRecord record = remediationEngine.remediate(alert);

        assertNotNull(record);
        assertTrue(record.actionId().startsWith("REM-"));
        assertEquals("HELM_ROLLBACK", record.executedAction());
        assertEquals("order-service", record.targetService());
        assertEquals("resilienceops-apps", record.targetNamespace());
        assertEquals("SUCCESS", record.status());
        assertEquals(1, remediationEngine.getAuditTrail().size());
    }

    @Test
    void shouldExecuteRollingRestartOnJvmMemorySaturation() {
        AlertItem alert = new AlertItem(
                "firing",
                Map.of("alertname", "JvmMemorySaturation", "service", "inventory-service", "namespace", "resilienceops-apps"),
                Map.of("summary", "Heap > 92%"),
                "2026-09-21T00:00:00Z",
                ""
        );

        RemediationAuditRecord record = remediationEngine.remediate(alert);

        assertNotNull(record);
        assertEquals("ROLLING_RESTART", record.executedAction());
        assertEquals("inventory-service", record.targetService());
    }

    @Test
    void shouldEscalateToSreWhenAlertUnknown() {
        AlertItem alert = new AlertItem(
                "firing",
                Map.of("alertname", "CustomUnmappedAlert", "service", "some-service"),
                Map.of(),
                "2026-09-21T00:00:00Z",
                ""
        );

        RemediationAuditRecord record = remediationEngine.remediate(alert);

        assertNotNull(record);
        assertEquals("NOOP_ESCALATE_SRE", record.executedAction());
    }
}
