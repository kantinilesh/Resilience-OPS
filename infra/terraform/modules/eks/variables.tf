variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "resilienceops-eks"
}

variable "cluster_version" {
  description = "Kubernetes version for the EKS cluster"
  type        = string
  default     = "1.30"
}

variable "environment" {
  description = "Deployment environment name (dev, staging, prod)"
  type        = string
}

variable "vpc_id" {
  description = "The VPC ID where EKS will be provisioned"
  type        = string
}

variable "subnet_ids" {
  description = "List of private subnet IDs for EKS worker nodes"
  type        = list(string)
}

variable "node_instance_types" {
  description = "EC2 instance types for the managed node group"
  type        = list(string)
  default     = ["m6i.large"]
}

variable "node_capacity_type" {
  description = "Capacity type for worker nodes (ON_DEMAND or SPOT)"
  type        = string
  default     = "ON_DEMAND"
}

variable "desired_size" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 3
}

variable "min_size" {
  description = "Minimum number of worker nodes for autoscaling"
  type        = number
  default     = 2
}

variable "max_size" {
  description = "Maximum number of worker nodes for autoscaling"
  type        = number
  default     = 6
}
