# ResilienceOps: Self-Healing Cloud Platform

[![Availability](https://img.shields.io/badge/Target_SLA-99.9%25-green.svg)](docs/architecture.md)
[![RTO](https://img.shields.io/badge/Target_RTO-%E2%89%A4_5_Minutes-blue.svg)](docs/architecture.md)
[![Kubernetes](https://img.shields.io/badge/Orchestration-AWS_EKS-orange.svg)](docs/architecture.md)
[![IaC](https://img.shields.io/badge/IaC-Terraform-blueviolet.svg)](infra/terraform/)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-Jenkins-red.svg)](ci-cd/jenkins/)
[![Observability](https://img.shields.io/badge/Observability-Prometheus_%2B_Grafana-orange.svg)](docs/architecture.md)
[![Chaos](https://img.shields.io/badge/Chaos_Testing-Chaos_Mesh-yellow.svg)](chaos/)

ResilienceOps is an enterprise-grade cloud-native reference platform engineered for high availability and autonomous self-healing. Built on **AWS EKS**, **Java Spring Boot microservices**, **Terraform**, **Jenkins**, **Helm**, **Prometheus & Grafana**, and **Chaos Engineering**, it guarantees **99.9% availability** (~43.8 minutes allowable downtime/month) and an automated **Recovery Time Objective (RTO) under 5 minutes**.

---

## 📑 Core Documentation

1. **[Architecture Specification](docs/architecture.md)**
   - High-level system architecture diagrams (Mermaid)
   - EKS multi-AZ cluster topology & network security
   - Dual-loop self-healing mechanism (Kube-native fast loop + Prometheus-driven deep loop)
   - 99.9% availability & $\le 5$-minute RTO mathematical analysis
2. **[Repository Structure Recommendation](docs/folder-structure.md)**
   - Monorepo structure, rationale, and tooling
   - Organization across services, infrastructure, deployment, and chaos manifests
3. **[Core Microservices Specification](docs/services-spec.md)**
   - `order-service`: Core transaction processing with Circuit Breakers & Outbox pattern
   - `inventory-service`: High-concurrency stateful inventory management with Bulkheads & Rate Limiters
   - `remediation-service`: Intelligent self-healing agent receiving Alertmanager webhooks and driving automated remediation
4. **[Architecture Decision Records (ADRs)](docs/decision-log.md)**
   - Trade-off analysis covering Monorepo vs Multi-repo, Alert-driven remediation vs Operator, RTO optimization, Helm umbrella vs Kustomize, and Chaos Mesh vs LitmusChaos

---

## 🏛️ System Architecture At A Glance

```mermaid
graph TB
    subgraph Users ["Client Layer"]
        Client[External Traffic / API Clients]
        Admin[Platform Engineers / SREs]
    end

    subgraph AWS_EKS ["AWS EKS Cluster (Multi-AZ Multi-NodeGroup)"]
        ALB[AWS Application Load Balancer]
        Ingress[AWS Load Balancer Controller / Ingress]
        
        subgraph App_Namespace ["Namespace: resilienceops-apps"]
            OrderSvc["order-service (Spring Boot)"]
            InvSvc["inventory-service (Spring Boot)"]
            RemediationSvc["remediation-service (Self-Healing Agent)"]
            
            OrderSvc -->|Resilience4j Circuit Breaker| InvSvc
        end

        subgraph Monitoring_Namespace ["Namespace: monitoring"]
            Prometheus[Prometheus Operator]
            Alertmanager[Alertmanager]
            Grafana[Grafana Dashboards]
            
            Prometheus -->|Scrape /actuator/prometheus| App_Namespace
            Prometheus -->|SLO Alert Triggers| Alertmanager
            Alertmanager -->|Webhook Event| RemediationSvc
        end

        subgraph Chaos_Namespace ["Namespace: chaos-testing"]
            ChaosMesh[Chaos Mesh Controller]
            ChaosMesh -.->|Simulate Pod Kill / Net Delay| App_Namespace
        end

        KubeAPI[K8s API Server]
        RemediationSvc -->|Auto Drain / Rollback / Scale| KubeAPI
    end

    subgraph CICD ["CI/CD Pipeline"]
        Jenkins[Jenkins CI/CD]
        SonarQube[SonarQube Quality Gate]
        Trivy[Trivy Vulnerability Scanner]
        Git[GitHub: Resilience-OPS]

        Git -->|Webhook| Jenkins
        Jenkins --> SonarQube
        Jenkins --> Trivy
        Jenkins -->|Helm Deploy| AWS_EKS
        Jenkins -->|Trigger Chaos Gate| ChaosMesh
    end

    Client --> ALB --> Ingress --> OrderSvc
    Admin --> Grafana
    Admin --> Jenkins
```

---

## 🔄 Self-Healing Loops & RTO Targets

| Failure Scenario | Detection Time | Remediation Action | Recovery Time (RTO) |
| :--- | :--- | :--- | :--- |
| **Pod Crash / OOMKilled** | 5 – 10 seconds | Kubelet auto-restarts pod via Liveness probe | **< 30 seconds** |
| **Node Failure / AZ Outage** | 30 – 45 seconds | EKS Auto Scaling Group + Karpenter spins replacement node; Pods rescheduled across healthy AZs via PodAntiAffinity | **< 3 minutes** |
| **Cascading Downstream Latency** | Immediate (< 1s) | Resilience4j Circuit Breaker opens; fallback to cached responses / degraded mode | **< 1 second** |
| **Faulty Release / Bad Canary** | 60 – 90 seconds | Prometheus detects error rate > 1%; Alertmanager triggers `remediation-service` automated Helm rollback | **< 2.5 minutes** |
| **Memory Leak / Deadlock** | 30 – 60 seconds | Spring Boot liveness failure or Alertmanager webhook $\rightarrow$ automated rolling restart | **< 2 minutes** |

---

## 🚀 Directory Structure Overview

```text
.
├── .github/                 # GitHub workflows & templates
├── docs/                    # Architecture, ADRs, and technical specs
│   ├── architecture.md      # Detailed system architecture & diagrams
│   ├── folder-structure.md  # Monorepo layout and best practices
│   ├── services-spec.md     # 3 Core microservices specifications
│   └── decision-log.md      # Architecture Decision Records (ADRs)
├── services/                # Java Spring Boot Microservices
│   ├── order-service/       # Order orchestration & transaction boundary
│   ├── inventory-service/   # Stock reservation & optimistic concurrency
│   └── remediation-service/ # Self-healing webhook consumer & k8s controller
├── infra/                   # Infrastructure as Code
│   └── terraform/           # Terraform modules (VPC, EKS, RDS, Monitoring)
├── deploy/                  # Deployment packages
│   └── helm/                # Helm charts for services & umbrella platform
├── ci-cd/                   # Continuous Integration & Delivery
│   └── jenkins/             # Jenkinsfile, shared libraries, and test pipelines
└── chaos/                   # Chaos engineering manifests (Chaos Mesh)
```

---

## 🛠️ Tech Stack & Tooling

- **Core Application**: Java 21, Spring Boot 3.x, Spring Cloud, Resilience4j, Micrometer
- **Containers & Orchestration**: Docker, Kubernetes 1.30+, AWS EKS, AWS ALB Ingress Controller
- **Infrastructure as Code**: Terraform 1.8+, AWS Provider, Helm Provider
- **Packaging & Delivery**: Helm 3.x
- **CI/CD Automation**: Jenkins Declarative Pipelines, Docker Pipeline, Trivy, SonarQube
- **Observability**: Prometheus Operator (kube-prometheus-stack), Alertmanager, Grafana, Micrometer Prometheus registry
- **Chaos Engineering**: Chaos Mesh (PodChaos, NetworkChaos, StressChaos)

---

## 👥 Contributors & Repository Maintenance

- Repository: [https://github.com/kantinilesh/Resilience-OPS](https://github.com/kantinilesh/Resilience-OPS)
- Architecture Lead & Author: Nilesh Kanti
