# VPC Module for Multi-AZ ResilienceOps
terraform {
  required_version = ">= 1.8.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
  }
}

variable "vpc_cidr" {
  description = "Base CIDR block for VPC"
  type        = String
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of 3 availability zones for high availability"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "resilienceops-vpc"
    Environment = "prod"
    ManagedBy   = "Terraform"
  }
}

resource "aws_subnet" "private" {
  count             = length(var.availability_zones)
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index)
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name                              = "resilienceops-private-${var.availability_zones[count.index]}"
    "kubernetes.io/role/internal-elb" = "1"
    "karpenter.sh/discovery"          = "resilienceops-eks"
  }
}

resource "aws_subnet" "public" {
  count                   = length(var.availability_zones)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index + 8)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                     = "resilienceops-public-${var.availability_zones[count.index]}"
    "kubernetes.io/role/elb" = "1"
  }
}
