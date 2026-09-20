package com.resilienceops.remediation.model;

import java.time.Instant;

public record RemediationAuditRecord(
        String actionId,
        String alertName,
        String targetNamespace,
        String targetService,
        String executedAction,
        String status,
        String message,
        Instant timestamp
) {}
