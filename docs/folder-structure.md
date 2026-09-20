# Monorepo vs. Multi-Repo Recommendation & Directory Structure

## 1. Recommendation: Unified Monorepo

For **ResilienceOps**, we strongly recommend a **Unified Monorepo** architecture.

### Why Monorepo is Optimal for ResilienceOps

| Criteria | Monorepo Evaluation | Multi-Repo Evaluation |
| :--- | :--- | :--- |
| **Atomic Cross-Service Changes** | **Superior**: Schema changes in `inventory-service` and caller updates in `order-service` can be validated, built, and tested in a single PR. | **Poor**: Requires coordinated multi-PR releases, version pinning nightmares, and high risk of staging desynchronization. |
| **Co-located Infrastructure & Code** | **Superior**: Helm charts, Terraform configs, and Chaos Mesh test manifests live alongside microservices code. Every PR validates IaC changes. | **Fragmented**: Drift between Helm templates in Repo A and Spring Boot configs in Repo B. |
| **Unified CI/CD Quality Gates** | **Superior**: A single Jenkins pipeline runs end-to-end integration, security scanning, and chaos verification before merging to `main`. | **Complex**: Requires webhooks across multiple repos, artifact coordination, and matrix testing. |
| **Self-Healing Loop Integration** | **Superior**: The `remediation-service` needs direct awareness of application deployment specs and Helm release values. | **Decoupled**: Version mismatch can cause remediation rollbacks to invalid Helm revisions. |

---

## 2. Comprehensive Directory Structure

```text
resilienceops/
├── .github/                                  # Repository workflows & issue templates
│   ├── CODEOWNERS                           # Ownership rules per module
│   └── pull_request_template.md             # PR checklist including chaos verification
│
├── docs/                                     # Architecture & Engineering Documentation
│   ├── architecture.md                       # Comprehensive architecture specification
│   ├── folder-structure.md                   # Monorepo structure & rationale (this document)
│   ├── services-spec.md                      # Core microservices specifications
│   ├── decision-log.md                       # Architecture Decision Records (ADRs)
│   └── runbooks/                             # SRE operational runbooks
│       ├── emergency-rollback.md             # Manual rollback instructions
│       └── chaos-test-execution.md           # Chaos scenario verification guide
│
├── services/                                 # Java Spring Boot Microservices
│   ├── pom.xml (or settings.gradle)         # Root build file for multi-module build
│   │
│   ├── order-service/                        # Core Order Coordinator Service
│   │   ├── Dockerfile                        # Multi-stage Docker build with JRE 21 runtime
│   │   ├── pom.xml                           # Service dependencies (Spring Web, Resilience4j)
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/resilienceops/order/
│   │       │   │   ├── OrderApplication.java
│   │       │   │   ├── controller/           # REST endpoints
│   │       │   │   ├── service/              # Business logic & Circuit Breaker orchestrator
│   │       │   │   ├── client/               # Feign / WebClient calls to inventory-service
│   │       │   │   ├── config/               # Resilience4j, Redis, Security config
│   │       │   │   └── model/                # Entities, DTOs, Domain events
│   │       │   └── resources/
│   │       │       ├── application.yml       # Base configs (Actuator, HikariCP, Probes)
│   │       │       └── application-prod.yml  # Production overrides
│   │       └── test/                         # Unit and MockWebServer integration tests
│   │
│   ├── inventory-service/                    # High-Concurrency Inventory Ledger
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/resilienceops/inventory/
│   │       │   │   ├── InventoryApplication.java
│   │       │   │   ├── controller/
│   │       │   │   ├── service/              # Optimistic locking & Redis caching
│   │       │   │   ├── repository/           # Spring Data JPA repositories
│   │       │   │   └── config/
│   │       │   └── resources/
│   │       │       ├── application.yml
│   │       │       └── db/migration/         # Flyway/Liquibase schema migrations
│   │       └── test/
│   │
│   └── remediation-service/                  # Autonomous Self-Healing Agent
│       ├── Dockerfile
│       ├── pom.xml                           # Includes Fabric8 Kubernetes Client & Micrometer
│       └── src/
│           ├── main/
│           │   ├── java/com/resilienceops/remediation/
│           │   │   ├── RemediationApplication.java
│           │   │   ├── controller/           # Webhook listener (/api/v1/webhook/alertmanager)
│           │   │   ├── engine/               # Decision engine (correlator, rules evaluator)
│           │   │   ├── executor/             # K8s actions (pod drain, restart, helm rollback)
│           │   │   └── notification/         # Slack/PagerDuty incident emitter
│           │   └── resources/
│           │       └── application.yml
│           └── test/
│
├── infra/                                    # Infrastructure as Code (Terraform)
│   └── terraform/
│       ├── environments/
│       │   ├── staging/                      # Staging environment root
│       │   │   ├── main.tf                   # Environment module invocations
│       │   │   ├── variables.tf
│       │   │   ├── outputs.tf
│       │   │   └── terraform.tfvars
│       │   └── prod/                         # Multi-AZ Production environment
│       │       ├── main.tf
│       │       ├── variables.tf
│       │       ├── outputs.tf
│       │       └── terraform.tfvars
│       └── modules/                          # Reusable Terraform Modules
│           ├── vpc/                          # 3-AZ VPC, subnets, NAT Gateways, Route Tables
│           ├── eks/                          # EKS cluster, managed node groups, OIDC, IRSA
│           ├── rds/                          # Multi-AZ Aurora PostgreSQL cluster
│           ├── redis/                        # ElastiCache Redis replication group
│           └── monitoring/                   # Helm release for kube-prometheus-stack
│
├── deploy/                                   # Kubernetes Packaging (Helm)
│   └── helm/
│       ├── charts/
│       │   ├── order-service/                # Chart: Deployments, Services, HPA, ServiceMonitor
│       │   │   ├── Chart.yaml
│       │   │   ├── values.yaml
│       │   │   ├── values-staging.yaml
│       │   │   ├── values-prod.yaml
│       │   │   └── templates/
│       │   │       ├── deployment.yaml
│       │   │       ├── service.yaml
│       │   │       ├── hpa.yaml
│       │   │       └── servicemonitor.yaml
│       │   ├── inventory-service/            # Chart for inventory-service
│       │   └── remediation-service/          # Chart with RBAC ClusterRole for K8s API access
│       └── resilienceops-platform/           # Umbrella Chart coordinating whole stack
│           ├── Chart.yaml                    # Dependencies: order, inventory, remediation
│           ├── values.yaml
│           └── templates/
│               ├── ingress.yaml              # AWS ALB Ingress routing rules
│               └── network-policies.yaml     # Zero-trust Pod network policies
│
├── ci-cd/                                    # Continuous Integration & Delivery
│   └── jenkins/
│       ├── Jenkinsfile                       # Root Declarative Multibranch Pipeline
│       ├── vars/                             # Shared Library Groovy scripts
│       │   ├── buildMicroservice.groovy      # Standardized Maven build & test wrapper
│       │   ├── runSecurityScan.groovy        # SonarQube & Trivy execution
│       │   ├── deployHelm.groovy             # Helm deploy wrapper
│       │   └── executeChaosGate.groovy       # Chaos Mesh automated test gate
│       └── config/
│           └── sonar-project.properties      # SonarQube quality thresholds
│
├── chaos/                                    # Chaos Engineering Layer (Chaos Mesh)
│   ├── experiments/
│   │   ├── pod-kill-order-service.yaml       # PodChaos: kills random order-service pod
│   │   ├── pod-kill-inventory-service.yaml   # PodChaos: kills inventory pod to test failover
│   │   ├── network-delay-inter-service.yaml  # NetworkChaos: 300ms latency on order->inventory
│   │   ├── network-partition.yaml            # NetworkChaos: complete drop between services
│   │   └── cpu-stress-nodes.yaml             # StressChaos: verify HPA triggering under CPU spike
│   └── schedules/
│       └── weekly-chaos-schedule.yaml        # Cron-like Chaos Mesh Schedule manifest
│
├── Makefile                                  # Developer automation (build, test, local k8s)
├── .gitignore                                # Git ignore patterns for Java, Terraform, Helm
└── README.md                                 # Project entry point & orientation
```

---

## 3. Monorepo Tooling & Governance

To maintain speed and avoid building all components on every commit, the following tooling practices are adopted:

1. **Path-Based CI Triggering**:
   - Jenkins uses the Git change log to detect which directories changed. If only `services/order-service/**` changed, only `order-service` is tested and packaged into a container.
2. **Standardized Base Images & JVM Configuration**:
   - All services share a consistent multi-stage base Dockerfile using `eclipse-temurin:21-jre-alpine` with CDS optimizations.
3. **Strict Boundary Enforcement**:
   - Microservices cannot share Java in-memory domain models or direct database connections. All inter-service communication is via REST APIs or asynchronous event buses.
