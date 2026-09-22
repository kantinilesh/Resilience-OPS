output "vpc_id" {
  description = "The ID of the production VPC"
  value       = module.vpc.vpc_id
}

output "eks_cluster_name" {
  description = "The name of the production EKS cluster"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "The Kubernetes API server endpoint"
  value       = module.eks.cluster_endpoint
}

output "ecr_repository_urls" {
  description = "Map of microservices to ECR repository URLs"
  value       = module.ecr.repository_urls
}

output "prometheus_irsa_role_arn" {
  description = "IAM Role ARN for Prometheus IRSA service account"
  value       = module.eks.prometheus_irsa_role_arn
}

output "state_bucket_name" {
  description = "S3 bucket storing Terraform remote state"
  value       = module.state_backend.s3_bucket_id
}

output "dynamodb_lock_table_name" {
  description = "DynamoDB table managing state locking"
  value       = module.state_backend.dynamodb_table_name
}
