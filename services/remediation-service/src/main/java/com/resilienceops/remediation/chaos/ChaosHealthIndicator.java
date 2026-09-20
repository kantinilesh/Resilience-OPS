package com.resilienceops.remediation.chaos;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ChaosHealthIndicator implements HealthIndicator {

    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private String failureReason = "Operational";

    @Override
    public Health health() {
        if (healthy.get()) {
            return Health.up().withDetail("chaosState", "Normal").build();
        }
        return Health.down()
                .withDetail("chaosState", "InjectedFailure")
                .withDetail("reason", failureReason)
                .build();
    }

    public void tripLiveness(String reason) {
        this.failureReason = reason;
        this.healthy.set(false);
    }

    public void restore() {
        this.failureReason = "Operational";
        this.healthy.set(true);
    }

    public boolean isHealthy() {
        return healthy.get();
    }
}
