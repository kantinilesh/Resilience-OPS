# Infrastructure as Code (Terraform)

This directory contains production-grade, modular Terraform configurations to provision the cloud infrastructure for **ResilienceOps** on AWS.

---

## 🏛️ Module Architecture

```text
infra/terraform/
├── environments/
│   ├── dev/            # Single-NAT, 2-AZ cost-optimized environment (t3.medium)
│   ├── staging/        # Multi-AZ testbed with dedicated NAT per AZ (m6i.large)
│   └── prod/           # 3-AZ high-availability production stack with S3/DynamoDB remote state
└── modules/
    ├── vpc/            # Multi-AZ VPC, public/private subnets, IGW, NAT Gateways, EKS tags
    ├── eks/            # EKS v1.30, autoscaling managed node groups (2-6 nodes), OIDC, IRSA
    ├── ecr/            # Container registries (order, inventory, remediation) with scan-on-push
    └── state-backend/  # S3 bucket (versioned, encrypted) + DynamoDB state locking table
```

---

## 🚀 Quickstart: Provisioning an Environment

### 1. Prerequisites
- AWS CLI configured with appropriate administrator or IAM provisioning permissions (`aws sts get-caller-identity`).
- Terraform `v1.8+`.

### 2. Deploying to `dev`
```bash
cd infra/terraform/environments/dev
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars -auto-approve
```

### 3. Deploying to `staging`
```bash
cd infra/terraform/environments/staging
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

### 4. Deploying to `prod`
For initial setup with a remote backend:
1. Provision the state backend first or run with a local backend to create the S3 bucket and DynamoDB table.
2. Initialize and migrate state:
   ```bash
   cd infra/terraform/environments/prod
   terraform init -migrate-state
   terraform plan -var-file=terraform.tfvars
   terraform apply -var-file=terraform.tfvars
   ```

---

## 🔐 IAM Roles for Service Accounts (IRSA)

The EKS module automatically creates an **OpenID Connect (OIDC)** identity provider and binds an IAM role for Prometheus:

- **Role ARN**: Output as `prometheus_irsa_role_arn`.
- **Target Kubernetes ServiceAccount**: `system:serviceaccount:monitoring:prometheus`
- **Permissions**:
  - `cloudwatch:PutMetricData`
  - `cloudwatch:GetMetricData`
  - `logs:PutLogEvents`
  - `ec2:DescribeInstances` / `ec2:DescribeTags`

In Kubernetes, annotate the Prometheus service account:
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: prometheus
  namespace: monitoring
  annotations:
    eks.amazonaws.com/role-arn: "<PROMETHEUS_IRSA_ROLE_ARN>"
```

---

## 📦 Amazon ECR Repositories

The `ecr` module provisions three repositories:
- `resilienceops/order-service`
- `resilienceops/inventory-service`
- `resilienceops/remediation-service`

**Security & Retention Rules**:
- Continuous vulnerability scanning on image push (`scan_on_push = true`).
- Server-side encryption using AWS-managed KMS (`AES256`).
- Lifecycle pruning: Untagged images are purged after **14 days**; at most **30 tagged releases** are retained per microservice.
