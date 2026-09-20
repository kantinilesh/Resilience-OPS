package com.resilienceops.remediation.controller;

import com.resilienceops.remediation.service.RemediationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhook")
public class AlertmanagerWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AlertmanagerWebhookController.class);
    private final RemediationEngine remediationEngine;

    public AlertmanagerWebhookController(RemediationEngine remediationEngine) {
        this.remediationEngine = remediationEngine;
    }

    @PostMapping("/alertmanager")
    public ResponseEntity<Map<String, Object>> handleAlertmanagerWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Received Prometheus Alertmanager webhook event: status={}", payload.get("status"));
        
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) payload.getOrDefault("alerts", List.of());
        int actionsTriggered = 0;

        for (Map<String, Object> alert : alerts) {
            String status = (String) alert.get("status");
            if ("firing".equalsIgnoreCase(status)) {
                Map<String, String> labels = (Map<String, String>) alert.getOrDefault("labels", Map.of());
                remediationEngine.processAlert(labels);
                actionsTriggered++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "PROCESSED",
                "firingAlertsReceived", alerts.size(),
                "remediationsExecuted", actionsTriggered
        ));
    }
}
