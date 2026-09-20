package com.resilienceops.remediation.model;

import java.util.List;
import java.util.Map;

public record AlertmanagerPayload(
        String receiver,
        String status,
        List<AlertItem> alerts,
        Map<String, String> groupLabels,
        Map<String, String> commonLabels,
        Map<String, String> commonAnnotations
) {}
