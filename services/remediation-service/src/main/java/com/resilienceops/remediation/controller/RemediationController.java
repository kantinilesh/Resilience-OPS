package com.resilienceops.remediation.controller;

import com.resilienceops.remediation.model.AlertItem;
import com.resilienceops.remediation.model.AlertmanagerPayload;
import com.resilienceops.remediation.model.RemediationAuditRecord;
import com.resilienceops.remediation.service.RemediationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/remediation")
public class RemediationController {

    private static final Logger log = LoggerFactory.getLogger(RemediationController.class);
    private final RemediationEngine remediationEngine;

    public RemediationController(RemediationEngine remediationEngine) {
        this.remediationEngine = remediationEngine;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody AlertmanagerPayload payload) {
        log.info("Received Prometheus Alertmanager webhook: status={}, alertCount={}",
                payload.status(), payload.alerts() != null ? payload.alerts().size() : 0);

        List<RemediationAuditRecord> executedRemediations = new ArrayList<>();

        if (payload.alerts() != null) {
            for (AlertItem alert : payload.alerts()) {
                if ("firing".equalsIgnoreCase(alert.status())) {
                    RemediationAuditRecord record = remediationEngine.remediate(alert);
                    executedRemediations.add(record);
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "PROCESSED",
                "firingAlerts", executedRemediations.size(),
                "records", executedRemediations
        ));
    }

    @GetMapping("/history")
    public ResponseEntity<List<RemediationAuditRecord>> getHistory() {
        return ResponseEntity.ok(remediationEngine.getAuditTrail());
    }

    @GetMapping("/rules")
    public ResponseEntity<Map<String, String>> getRules() {
        return ResponseEntity.ok(Map.of(
                "MicroserviceHighErrorRate", "HELM_ROLLBACK (revert canary to last stable revision)",
                "JvmMemorySaturation", "ROLLING_RESTART (zero-downtime rolling restart)",
                "HikariConnectionPoolExhausted", "SCALE_AND_FLUSH_POOL (traffic shed & pod scale)",
                "PodCrashLoopBackOff", "PURGE_AND_RESTART_POD (evict failing pod, pull attested image)"
        ));
    }
}
