package com.resilienceops.remediation.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/v1/admin/chaos")
public class RemediationChaosController {

    private static final Logger log = LoggerFactory.getLogger(RemediationChaosController.class);
    private static final List<byte[]> MEMORY_LEAK_STORE = new CopyOnWriteArrayList<>();
    private final ChaosHealthIndicator healthIndicator;

    public RemediationChaosController(ChaosHealthIndicator healthIndicator) {
        this.healthIndicator = healthIndicator;
    }

    @PostMapping("/simulate-failure")
    public ResponseEntity<Map<String, Object>> simulateFailure(@RequestBody Map<String, Object> request) {
        String action = (String) request.getOrDefault("action", "latency");
        int durationSeconds = (int) request.getOrDefault("durationSeconds", 10);

        log.warn("REMEDIATION CHAOS HOOK INVOKED: action={}, durationSeconds={}", action, durationSeconds);

        switch (action.toLowerCase()) {
            case "cpu-spike":
                triggerCpuSpike(durationSeconds);
                return ResponseEntity.ok(Map.of(
                        "action", "cpu-spike",
                        "status", "RUNNING",
                        "durationSeconds", durationSeconds,
                        "message", "CPU stress workers spawned on remediation-service"
                ));

            case "memory-leak":
                int megabytes = (int) request.getOrDefault("megabytes", 100);
                for (int i = 0; i < megabytes; i++) {
                    MEMORY_LEAK_STORE.add(new byte[1024 * 1024]);
                }
                return ResponseEntity.ok(Map.of(
                        "action", "memory-leak",
                        "status", "ALLOCATED",
                        "allocatedMB", megabytes,
                        "totalBuffers", MEMORY_LEAK_STORE.size()
                ));

            case "latency":
                int latencyMs = (int) request.getOrDefault("latencyMs", 3000);
                try {
                    Thread.sleep(latencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ResponseEntity.ok(Map.of(
                        "action", "latency",
                        "status", "COMPLETED",
                        "inducedLatencyMs", latencyMs
                ));

            case "liveness-fail":
                healthIndicator.tripLiveness("Simulated webhook consumer deadlock via /admin/chaos");
                return ResponseEntity.ok(Map.of(
                        "action", "liveness-fail",
                        "status", "HEALTH_TRIPPED_DOWN",
                        "message", "Actuator liveness probe now returns 503 DOWN to trigger Kubelet restart"
                ));

            case "crash":
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}
                    log.error("CRITICAL CHAOS: System.exit(1) invoked deliberately on remediation-service.");
                    System.exit(1);
                });
                return ResponseEntity.ok(Map.of(
                        "action", "crash",
                        "status", "TERMINATING",
                        "message", "JVM termination scheduled in 500ms"
                ));

            case "reset":
                MEMORY_LEAK_STORE.clear();
                healthIndicator.restore();
                System.gc();
                return ResponseEntity.ok(Map.of(
                        "action", "reset",
                        "status", "RESTORED",
                        "message", "Chaos states cleared on remediation-service"
                ));

            default:
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Unknown chaos action: " + action,
                        "validActions", List.of("cpu-spike", "memory-leak", "latency", "liveness-fail", "crash", "reset")
                ));
        }
    }

    private void triggerCpuSpike(int durationSeconds) {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                while (System.currentTimeMillis() < endTime) {
                    Math.sqrt(ThreadLocalRandom.current().nextDouble());
                }
            });
        }
        executor.shutdown();
    }
}
