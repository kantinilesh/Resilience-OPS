variable "vpc_cidr" {
  description = "Base CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of availability zones for multi-AZ high availability"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "environment" {
  description = "Deployment environment name (dev, staging, prod)"
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name to tag subnets for ALB Controller and Karpenter discovery"
  type        = string
}

variable "single_nat_gateway" {
  description = "If true, provision a single NAT Gateway shared across all private subnets (useful for dev cost savings)"
  type        = bool
  default     = false
}
