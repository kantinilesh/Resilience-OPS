package com.resilienceops.remediation.model;

import java.util.Map;

public record AlertItem(
        String status,
        Map<String, String> labels,
        Map<String, String> annotations,
        String startsAt,
        String endsAt
) {
    public String getAlertName() {
        return labels != null ? labels.getOrDefault("alertname", "UnknownAlert") : "UnknownAlert";
    }

    public String getService() {
        return labels != null ? labels.getOrDefault("service", "unknown-service") : "unknown-service";
    }

    public String getNamespace() {
        return labels != null ? labels.getOrDefault("namespace", "resilienceops-apps") : "resilienceops-apps";
    }

    public String getSeverity() {
        return labels != null ? labels.getOrDefault("severity", "warning") : "warning";
    }
}
