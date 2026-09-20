# Core Microservices Specification

This document details the functional responsibilities, resilience architectures, API contracts, and self-healing integration points for the three core Spring Boot services powering **ResilienceOps**.

---

## 1. `order-service` (Order Orchestration & Transaction Coordinator)

### 1.1 Responsibilities
- Serves as the primary public-facing entry point for checkout transactions.
- Coordinates distributed checkout operations by invoking downstream services (e.g., reserving inventory in `inventory-service`).
- Enforces data integrity using the **Transactional Outbox Pattern** to prevent partial order states during network partitions.
- Shields users from downstream delays or outages via **Resilience4j Circuit Breakers, Retries, and Fallbacks**.

### 1.2 Resilience Architecture & Design Patterns
- **Resilience4j Circuit Breaker**: Wraps all outbound HTTP calls to `inventory-service`. If failure rate exceeds 50% or slow calls (>1.5s) exceed 60%, the circuit opens instantly to prevent thread pool exhaustion.
- **Fallback Strategy**: When the circuit is open, the service gracefully switches to **Tentative Order Mode** (accepting the order asynchronously and queuing inventory reservation for background retry) rather than failing user requests.
- **Bulkhead Pattern**: Allocates an isolated thread pool (max 20 concurrent threads) dedicated exclusively to inventory calls, guaranteeing that an inventory outage cannot starve the primary order processing thread pool.

### 1.3 Resilience4j Configuration Snippet (`application.yml`)
```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 20
        minimumNumberOfCalls: 10
        failureRateThreshold: 50.0
        slowCallRateThreshold: 60.0
        slowCallDurationThreshold: 1500ms
        waitDurationInOpenState: 10000ms
        permittedNumberOfCallsInHalfOpenState: 5
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: true
  timelimiter:
    instances:
      inventoryService:
        timeoutDuration: 2000ms
  retry:
    instances:
      inventoryService:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
  bulkhead:
    instances:
      inventoryService:
        maxConcurrentCalls: 20
        maxWaitDuration: 200ms
```

### 1.4 REST API Endpoints
- `POST /api/v1/orders`: Submits a new customer order.
- `GET /api/v1/orders/{orderId}`: Retrieves order status.
- `GET /actuator/health/liveness`: Kubernetes Liveness Probe endpoint.
- `GET /actuator/health/readiness`: Kubernetes Readiness Probe endpoint (checks DB and Redis connectivity).
- `GET /actuator/prometheus`: Prometheus scraper endpoint.

---

## 2. `inventory-service` (High-Concurrency Inventory Ledger)

### 2.1 Responsibilities
- Maintains real-time product stock counts across fulfillment warehouses.
- Manages atomic stock reservations and rollbacks.
- Handles peak burst traffic without overselling using database optimistic locking (`@Version`) combined with Redis distributed locks.

### 2.2 Resilience Architecture & Design Patterns
- **Optimistic Locking with Exponential Jitter Retry**: Handles high concurrent write conflicts without holding database row locks for extended durations.
- **Multi-Level Caching (L1 + L2)**:
  - **L1 (In-Memory Caffeine Cache)**: High-speed read buffer for stock availability queries (TTL 5 seconds).
  - **L2 (Distributed Redis Cache)**: Shared state across all `inventory-service` pods.
- **Graceful Read Degradation**: If the Redis cache or database read replica is unreachable, the service serves read traffic from L1 cache with a response header `X-Cache-Degraded: true`.
- **Rate Limiting**: Configured with Resilience4j RateLimiter (max 1,000 requests/sec per pod) to prevent inventory denial-of-service.

### 2.3 REST API Endpoints
- `POST /api/v1/inventory/reserve`: Atomically reserves quantity for a specific SKU.
- `POST /api/v1/inventory/release`: Releases previously held reservation (compensation action).
- `GET /api/v1/inventory/{sku}/availability`: Checks stock availability.
- `GET /actuator/health/liveness` & `/actuator/health/readiness`
- `GET /actuator/prometheus`

---

## 3. `remediation-service` (Autonomous Self-Healing Agent)

### 3.1 Responsibilities
- Operates as the central autonomous remediation brain in the cluster.
- Ingests alert webhooks delivered by **Prometheus Alertmanager**.
- Verifies and enriches alert context by querying the Kubernetes API Server and metrics endpoints.
- Executes automated corrective actions:
  - Rolling restarts of pods displaying memory saturation or connection pool starvation.
  - Automatic rollback of failing canary deployments.
  - Automated drain and evict of pods on degraded Kubernetes nodes.
  - Scaling out deployment replicas ahead of standard HPA response times.
- Emits real-time incident audit logs to Slack, PagerDuty, and Grafana annotations.

### 3.2 Autonomous Remediation Decision Matrix

| Alert Trigger | Root Cause Indicator | Automated Remediation Action | Fallback Escalation |
| :--- | :--- | :--- | :--- |
| `MicroserviceHighErrorRate` (>2% 5xx for 1 min) | Bad canary release deployment | Triggers Helm rollback to previous stable release revision (`helm rollback <release>`). | If error rate persists > 2 min, scale down canary pods to 0 and page On-Call SRE. |
| `JvmMemorySaturation` (Heap > 92% for 2 min) | JVM GC pause deadlock or leak | Issues graceful pod restart (`kubectl rollout restart deployment/<name>`). | If CrashLoopBackOff occurs, capture heap dump to S3 volume and alert SRE. |
| `HikariConnectionPoolExhausted` | Database slow queries / connection lock | Evicts stalled pods sequentially; triggers Redis cache warming. | Alert DBA and trigger Aurora read-replica auto-scale. |
| `NodeNotReady` / Hardware Degradation | AWS EC2 hardware degradation | Issues `kubectl cordon` and `kubectl drain --ignore-daemonsets` on affected node. | AWS Auto Scaling terminates faulty EC2 and provisions replacement. |

### 3.3 Sample Webhook Ingestion Payload (`/api/v1/webhook/alertmanager`)
```json
{
  "version": "4",
  "groupKey": "{}:{alertname=\"MicroserviceHighErrorRate\"}",
  "status": "firing",
  "receiver": "remediation-webhook",
  "alerts": [
    {
      "status": "firing",
      "labels": {
        "alertname": "MicroserviceHighErrorRate",
        "severity": "critical",
        "service": "order-service",
        "namespace": "resilienceops-apps",
        "cluster": "prod-eks-cluster"
      },
      "annotations": {
        "summary": "order-service error rate is 4.8% over the last 60 seconds",
        "runbook_url": "https://github.com/kantinilesh/Resilience-OPS/docs/runbooks"
      },
      "startsAt": "2026-09-20T23:30:00Z"
    }
  ]
}
```

### 3.4 Security & RBAC Configuration
The `remediation-service` runs under a dedicated Kubernetes ServiceAccount with scoped, least-privilege `ClusterRole` permissions:
- Can `get`, `list`, `watch`, `update`, and `patch` Deployments, Pods, and ReplicaSets in `resilienceops-apps`.
- Cannot modify cluster-wide RBAC, Secrets, or nodes directly (node cordoning is delegated to Node Termination Handler).
