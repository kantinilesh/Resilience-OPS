# Architecture Specification: ResilienceOps Platform

## 1. Executive Summary

**ResilienceOps** is a high-reliability, self-healing cloud platform engineered to deliver **99.9% availability** (three nines) and a maximum **5-minute Recovery Time Objective (RTO)** across infrastructure, container orchestration, microservices runtime, and deployment lifecycles.

### Availability & RTO Objectives

$$\text{Allowable Unplanned Downtime} = 365.25 \times 24 \times (1 - 0.999) \approx 8.76 \text{ hours/year} \ (43.8 \text{ min/month})$$

To achieve these targets without human intervention, ResilienceOps shifts from **passive fault tolerance** to **active autonomous self-healing** across two coordinated feedback loops:
1. **Fast Reactive Loop (Sub-30 seconds)**: Local Kubernetes kubelet probes, Horizontal Pod Autoscaler (HPA), and pod restarts.
2. **Deep Intelligent Loop (Sub-3 minutes)**: Prometheus SLO-based metric evaluation, Alertmanager webhook routing, and an automated `remediation-service` executing cluster-level mitigations, canary rollbacks, or traffic rerouting.

---

## 2. High-Level Architecture Diagram

The diagram below illustrates the end-to-end topology of ResilienceOps on AWS EKS, including ingress routing, microservice tiers, observability, self-healing automation, CI/CD, and chaos engineering.

```mermaid
graph TB
    subgraph Client_Tier ["1. Ingress & Traffic Management Tier"]
        Users(("External Clients"))
        Route53["AWS Route 53 (DNS / Health Checked)"]
        WAF["AWS WAF (DDoS / Rate Limiting)"]
        ALB["AWS Application Load Balancer (Multi-AZ)"]
        
        Users --> Route53 --> WAF --> ALB
    end

    subgraph AWS_EKS_Cluster ["2. AWS EKS Kubernetes Cluster (v1.30+)"]
        IngressController["AWS Load Balancer Controller (Ingress)"]
        ALB --> IngressController

        subgraph NS_Apps ["Namespace: resilienceops-apps (Multi-AZ Pod Spread)"]
            direction TB
            subgraph Order_Deployment ["order-service (Stateful Coordinator)"]
                OrderPod1["order-service Pod 1 (AZ-1a)"]
                OrderPod2["order-service Pod 2 (AZ-1b)"]
                OrderPod3["order-service Pod 3 (AZ-1c)"]
            end

            subgraph Inventory_Deployment ["inventory-service (Inventory Ledger)"]
                InvPod1["inventory-service Pod 1 (AZ-1a)"]
                InvPod2["inventory-service Pod 2 (AZ-1b)"]
            end

            subgraph Remediation_Deployment ["remediation-service (Self-Healing Agent)"]
                RemediationPod["remediation-service Pod (Leader-Elected)"]
            end

            Order_Deployment -->|Resilience4j Circuit Breaker + Retry| Inventory_Deployment
        end

        subgraph NS_Monitoring ["3. Monitoring & Observability Tier (Namespace: monitoring)"]
            Prometheus["Prometheus Operator (StatefulSet)"]
            Alertmanager["Alertmanager Cluster"]
            Grafana["Grafana (SLO / Golden Signals Dashboards)"]
            
            Prometheus --> Alertmanager
            Prometheus --> Grafana
        end

        subgraph NS_Chaos ["4. Chaos Testing Layer (Namespace: chaos-testing)"]
            ChaosMeshDaemon["Chaos Mesh DaemonSet"]
            ChaosController["Chaos Mesh Controller Manager"]
            ChaosSchedule["Scheduled Chaos Experiments"]

            ChaosSchedule --> ChaosController --> ChaosMeshDaemon
            ChaosMeshDaemon -.->|Fault Injection: Network Latency / Pod Kill| NS_Apps
        end

        IngressController --> Order_Deployment
        IngressController --> Inventory_Deployment

        KubeAPI["Kubernetes API Server (Control Plane)"]
        RemediationPod -->|RBAC: Drain / Evict / Scale / Restart| KubeAPI
        Prometheus -.->|Scrape /actuator/prometheus| Order_Deployment
        Prometheus -.->|Scrape /actuator/prometheus| Inventory_Deployment
        Prometheus -.->|Scrape /actuator/prometheus| Remediation_Deployment
        Alertmanager -->|Webhook: Trigger Self-Healing| RemediationPod
    end

    subgraph Data_Tier ["5. Managed Cloud Data Tier"]
        RDS_Primary[("Amazon Aurora PostgreSQL Primary (AZ-1a)")]
        RDS_Replica[("Amazon Aurora Read Replica (AZ-1b)")]
        Redis_Cluster[("ElastiCache Redis Cluster (Multi-AZ)")]

        RDS_Primary -.->|Async Replication| RDS_Replica
        Order_Deployment --> RDS_Primary
        Inventory_Deployment --> RDS_Primary
        Order_Deployment --> Redis_Cluster
    end

    subgraph CICD_Pipeline ["6. CI/CD & Governance Tier"]
        GitRepo["GitHub Repo (Resilience-OPS)"]
        Jenkins["Jenkins Primary Node (K8s Worker Agents)"]
        SonarQube["SonarQube (Quality Gate)"]
        Trivy["Trivy Container Scanner"]
        Harbor_ECR["AWS ECR / Container Registry"]
        HelmRegistry["Helm Chart Repository"]

        GitRepo -->|Push / PR Webhook| Jenkins
        Jenkins --> SonarQube
        Jenkins --> Trivy
        Jenkins --> Harbor_ECR
        Jenkins --> HelmRegistry
        Jenkins -->|Canary Deployment via Helm| IngressController
        Jenkins -->|Trigger Chaos Regression Gate| ChaosSchedule
    end
```

---

## 3. Core Component Breakdown

### 3.1 App Services (Spring Boot Microservices)
The platform executes containerized Java 21 Spring Boot 3.x microservices:
- **`order-service`**: Handles checkout transactions. Uses **Resilience4j Circuit Breaker, Bulkhead, and Rate Limiter** to isolate slow downstream calls. Implements the **Transactional Outbox Pattern** to prevent data inconsistency during transient network partitions.
- **`inventory-service`**: Manages product allocations with optimistic locking in Aurora PostgreSQL and distributed locking via Redis. Uses local in-memory fallback caches when the primary database undergoes automated failover.
- **`remediation-service`**: Acts as the autonomous brain. It listens to Prometheus Alertmanager alerts via webhook, correlates symptoms using the Kubernetes API, and triggers automated remediation runs (e.g., rolling restart, canary rollback, cache purge, or scaling up).

### 3.2 EKS Cluster Architecture
- **Multi-AZ Distribution**: Deployed across 3 AWS Availability Zones (`us-east-1a`, `us-east-1b`, `us-east-1c`).
- **Topology Spread Constraints & Pod Anti-Affinity**: Ensures microservice replicas are evenly distributed across failure domains:
  ```yaml
  topologySpreadConstraints:
    - maxSkew: 1
      topologyKey: topology.kubernetes.io/zone
      whenUnsatisfiable: DoNotSchedule
      labelSelector:
        matchLabels:
          app.kubernetes.io/name: order-service
  ```
- **Compute Sizing & Karpenter Autoscaling**:
  - Critical control services run on **On-Demand** node groups.
  - Scale-out workloads run on a diversified pool of **Spot Instances** with automated graceful pre-emption draining (using AWS Node Termination Handler).
  - Rapid scale-out from 0 to 100 pods in < 45 seconds via Karpenter.

### 3.3 Monitoring & Observability Stack
The platform uses the **Prometheus Operator** (`kube-prometheus-stack`):
- **Metrics Scraping**: Polls `/actuator/prometheus` endpoints every 10 seconds.
- **ServiceMonitors & PodMonitors**: Declarative CRDs capturing JVM GC pauses, HTTP response latency percentiles (p50, p95, p99), connection pool exhaustion, and error rates (5xx HTTP responses).
- **Alertmanager Routing**: Routes alerts based on severity:
  - `P1-Critical` (High 5xx rate, Pod CrashLoopBackOff > 3 restarts) $\rightarrow$ Pushes webhook to `remediation-service` for automatic intervention.
  - `P2-Warning` (Latency p99 > 800ms) $\rightarrow$ Pushes to Slack/PagerDuty and invokes HPA scale out.
- **Grafana Visualizations**: Curated dashboards tracking Golden Signals (Latency, Traffic, Errors, Saturation) and SLO burn rates.

### 3.4 CI/CD Pipeline (Jenkins & Helm)
- **Declarative Pipeline**:
  1. Code checkout & Maven build with parallelized unit tests.
  2. SonarQube static code analysis & Trivy container vulnerability scanning.
  3. Multi-architecture Docker image build and push to AWS ECR.
  4. Helm linting & packaging.
  5. Deployment to staging namespace.
  6. **Chaos Resilience Gate**: Automatic execution of a 3-minute Chaos Mesh test suite. If the system fails to recover or error rates exceed 0.1%, the pipeline aborts.
  7. Automated canary release to production using Helm and Argo Rollouts / Flagger.

### 3.5 Chaos Testing Layer (Chaos Mesh)
Automated chaos injection is scheduled both in the staging deployment pipeline and during off-peak hours in pre-production:
- **`PodChaos`**: Randomly terminates order and inventory pods to verify pod recreation under 15 seconds.
- **`NetworkChaos`**: Injects 200ms latency and 10% packet drop between `order-service` and `inventory-service` to prove Resilience4j circuit breaker trips and fallback mechanisms activate.
- **`StressChaos`**: Injects 90% CPU / Memory load on worker nodes to confirm HPA and node autoscaling behavior.

---

## 4. Dual-Loop Self-Healing Architecture

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Service as Order / Inventory Service
    participant K8s as Kubelet / HPA
    participant Prom as Prometheus
    participant AM as Alertmanager
    participant Rem as Remediation Controller
    participant K8sAPI as K8s API Server

    rect rgb(235, 248, 255)
    Note over Service, K8s: Fast Loop (Local Container Lifecycle)
    Service->>Service: Memory leak / unhandled deadlock
    K8s->>Service: GET /actuator/health/liveness (every 10s)
    Service-->>K8s: 503 DOWN (deadlocked thread detected)
    K8s->>Service: Terminate & Restart Pod Container (SIGTERM / SIGKILL)
    Note over K8s: Pod recovered in < 25s
    end

    rect rgb(255, 245, 245)
    Note over Service, K8sAPI: Deep Loop (Cluster-Wide & Algorithmic Remediation)
    Client->>Service: Surge traffic / Bad Canary Release
    Service-->>Client: 500 Internal Server Error (Spike > 2%)
    Prom->>Service: Scrape /actuator/prometheus
    Prom->>Prom: Evaluate SLO rule: sum(rate(http_server_requests_errors[1m])) > 0.02
    Prom->>AM: Fire Alert: MicroserviceHighErrorRate (severity=critical)
    AM->>Rem: POST /api/v1/remediate (Webhook JSON payload)
    Rem->>Rem: Evaluate Decision Matrix (Canary issue vs DB degradation)
    Rem->>K8sAPI: PATCH Deployment (Rollback canary or scale out replicas)
    K8sAPI-->>Service: Update Pod Specs
    Note over Rem, Service: Complete Recovery < 120s (RTO Target: < 300s)
    end
```

---

## 5. RTO Breakdown: Recovery Time Objective ($\le 5$ Minutes)

To guarantee an RTO $\le 300$ seconds under any single component failure:

| Milestone / Phase | Typical Time | Maximum SLA Budget | Description |
| :--- | :--- | :--- | :--- |
| **Fault Detection** | 10 – 30 seconds | 60 seconds | Prometheus scraping interval (10s) + Alert rule evaluation window (30s). |
| **Alert Routing & Ingestion** | 2 – 5 seconds | 10 seconds | Alertmanager deduplication, grouping, and webhook delivery to `remediation-service`. |
| **Remediation Execution** | 15 – 45 seconds | 90 seconds | `remediation-service` executes Helm rollback, pod drain, or HPA scale override. |
| **Pod Start & Initialization** | 15 – 30 seconds | 60 seconds | Java Spring Boot startup with CDS (Class Data Sharing) / GraalVM optimizations; Kubelet readiness probe passes. |
| **Traffic Warmup & Rebalancing** | 10 – 20 seconds | 40 seconds | ALB Ingress health check passes and resumes full traffic routing. |
| **Total Cumulative Time** | **~80 seconds** | **260 seconds (< 5 min)** | **Fully complies with 5-minute RTO.** |

---

## 6. Network Security & Data Flow

1. **Zero-Trust Network Policies**: Kubernetes `NetworkPolicy` isolates namespaces. `resilienceops-apps` can only communicate with required database ports and observability endpoints.
2. **AWS IAM Roles for Service Accounts (IRSA)**: Fine-grained least-privilege AWS IAM roles assigned to Kubernetes pods without baking access keys into containers.
3. **mTLS Ready**: Microservice-to-microservice traffic can be encrypted in transit via lightweight service mesh (Linkerd / Cilium SPIRE).
