terraform {
  required_version = ">= 1.8.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Environment = var.environment
      Platform    = "ResilienceOps"
      ManagedBy   = "Terraform"
    }
  }
}

locals {
  cluster_name = "resilienceops-${var.environment}-eks"
}

# 1. State Backend Resources
module "state_backend" {
  source      = "../../modules/state-backend"
  environment = var.environment
}

# 2. VPC Module (Cost-optimized: single NAT Gateway across 2 AZs)
module "vpc" {
  source             = "../../modules/vpc"
  environment        = var.environment
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
  cluster_name       = local.cluster_name
  single_nat_gateway = true
}

# 3. EKS Cluster Module
module "eks" {
  source              = "../../modules/eks"
  cluster_name        = local.cluster_name
  environment         = var.environment
  vpc_id              = module.vpc.vpc_id
  subnet_ids          = module.vpc.private_subnet_ids
  node_instance_types = var.node_instance_types
  desired_size        = var.desired_size
  min_size            = var.min_size
  max_size            = var.max_size
}

# 4. ECR Repositories
module "ecr" {
  source      = "../../modules/ecr"
  environment = var.environment
}
