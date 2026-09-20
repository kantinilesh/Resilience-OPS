# Architecture Decision Log (ADRs)

This document records the foundational architectural decisions, evaluation criteria, and trade-offs made during the design of the **ResilienceOps** platform.

---

## ADR-001: Monorepo vs. Multi-Repo Architecture

### Status: ACCEPTED

### Context
ResilienceOps consists of multiple Spring Boot microservices, infrastructure modules (Terraform), packaging definitions (Helm charts), CI/CD scripts (Jenkins), and chaos manifests. We needed to choose between:
1. **Multi-Repo**: Dedicated Git repository for each service, chart, and terraform module.
2. **Unified Monorepo**: Single repository containing all services, infra, deployment, and testing assets.

### Decision
Adopt a **Unified Monorepo**.

### Trade-Off Analysis

| Criteria | Multi-Repo | Monorepo (Chosen) |
| :--- | :--- | :--- |
| **Cross-cutting consistency** | Low. Prone to version drift between microservices and their corresponding Helm charts. | **High**. Microservice code changes and Helm chart parameter updates are committed and tested atomically. |
| **CI/CD Pipeline Complexity** | High. Requires coordinating multi-repo triggers, webhooks, and complex dependency graphs. | **Controlled**. Jenkins uses path-based change detection to trigger only affected modules. |
| **Developer Onboarding** | High friction. New developers must clone and configure 6+ distinct repositories. | **Low friction**. Single clone grants access to full stack with a unified local development environment. |
| **Repository Size & Scaling** | Minimal per repository. | Can grow large over years; mitigated via sparse checkouts and Git LFS for binary assets. |

---

## ADR-002: Observability-Driven Automated Remediation: Alertmanager Webhook vs. Custom K8s Operator

### Status: ACCEPTED

### Context
To achieve a sub-5-minute RTO, the platform requires an automated remediation engine capable of responding to degraded application states (e.g., memory leaks, high error rates, connection pool exhaustion) that standard K8s liveness probes cannot resolve.

### Decision
Implement a **Spring Boot `remediation-service` triggered via Prometheus Alertmanager Webhooks**, rather than writing a low-level Go-based Kubernetes Custom Operator.

### Trade-Off Analysis

| Dimension | Go-based K8s Operator | Spring Boot Alertmanager Webhook (Chosen) |
| :--- | :--- | :--- |
| **Signal Source** | Primarily K8s cluster events and CRD states; difficult to introspect JVM metrics directly. | **Direct Prometheus Alerts**. Rich metric-based evaluation (error rate over time, JVM heap saturation, p99 latency). |
| **Team Velocity** | Requires Go language expertise and deep mastery of `controller-runtime`. | **Matches team stack**. The core team is skilled in Java/Spring Boot, enabling rapid extension and maintenance. |
| **Integration with Ecosystem** | Complex custom notification logic required. | Spring Boot easily integrates with Slack, PagerDuty, Jira, and Kafka via enterprise-grade libraries. |
| **Execution Latency** | Sub-second reconciliation. | ~2–5 seconds webhook delivery latency (well within the 5-minute RTO budget). |

---

## ADR-003: Architectural Strategy to Guarantee 99.9% Availability and $\le 5$-Minute RTO

### Status: ACCEPTED

### Context
99.9% availability allows a maximum of **~43.8 minutes of unplanned downtime per month**. Furthermore, any incident must achieve complete recovery within **5 minutes (300 seconds)** without waiting for on-call engineer intervention.

### Decision
Implement a **Three-Tier Redundancy & Self-Healing Architecture**:
1. **Infrastructure Tier**: EKS Managed Node Groups distributed across 3 Availability Zones with automated node health checks, Karpenter node provisioning, and pod anti-affinity.
2. **Application Tier**: Spring Boot microservices backed by Resilience4j (Circuit Breakers, Bulkheads, Rate Limiters, and Fallbacks) combined with Aurora PostgreSQL Multi-AZ failover.
3. **Control Loop Tier**: Kubelet fast-restart loop (<30s) + Prometheus/Alertmanager deep-remediation loop (<2.5m).

### Trade-Off Analysis
- **Cost vs. Reliability**: Running multi-AZ Aurora clusters and multi-zone EKS node groups increases infrastructure cost by ~35% compared to single-AZ setups. This trade-off is accepted as single-AZ outages would instantly breach the 99.9% SLA.
- **Fail-Open vs. Fail-Closed**: In order processing, the system defaults to a degraded asynchronous "Tentative Order Mode" rather than hard-failing when inventory is unreachable.

---

## ADR-004: Kubernetes Packaging: Helm Umbrella Chart vs. Kustomize

### Status: ACCEPTED

### Context
We evaluated options for managing Kubernetes manifests across multiple environments (staging, production) and microservices.

### Decision
Standardize on **Helm 3 with an Umbrella Chart (`resilienceops-platform`)**.

### Trade-Off Analysis

| Criteria | Kustomize | Helm 3 Umbrella Chart (Chosen) |
| :--- | :--- | :--- |
| **Templating & Logic** | Purely declarative overlays; no conditionals or loops. | Powerful Go templating allows dynamic parameterization (e.g. conditional ingress rules, dynamic replica counts). |
| **Release Versioning & Rollback** | Relies on Git commit history; rolling back requires GitOps revert or raw kubectl edits. | **Native Helm Release History**. Provides instant atomic rollbacks (`helm rollback <release> <revision>`) crucial for our RTO target. |
| **Dependency Management** | Requires manual stitching of external manifests. | `Chart.yaml` cleanly declares subchart dependencies (`order-service`, `inventory-service`, `remediation-service`). |

---

## ADR-005: Chaos Engineering Framework: Chaos Mesh vs. LitmusChaos

### Status: ACCEPTED

### Context
To validate our self-healing loops and 5-minute RTO continuously, the platform requires automated chaos testing integrated into the Jenkins CI/CD pipeline and scheduled in staging/pre-prod.

### Decision
Select **Chaos Mesh**.

### Trade-Off Analysis

| Dimension | LitmusChaos | Chaos Mesh (Chosen) |
| :--- | :--- | :--- |
| **Ease of Integration** | Feature-rich but heavier control plane footprint and complex workflow engine. | **Lightweight CRD-driven architecture**. Manifests are plain Kubernetes YAML files easily managed in Git and Helm. |
| **Fault Injection Variety** | Extensive cloud and application experiments. | **Superior network & kernel-level simulation** (fine-grained latency, packet drop, clock skew, I/O delays via eBPF). |
| **CI/CD Automation** | Requires Litmus Portal / GraphQL API integration. | Direct integration via `kubectl apply -f chaos/experiments/` within Jenkins pipeline stages. |
