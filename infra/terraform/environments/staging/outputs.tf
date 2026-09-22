output "vpc_id" {
  value = module.vpc.vpc_id
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "prometheus_irsa_role_arn" {
  value = module.eks.prometheus_irsa_role_arn
}

output "state_bucket_name" {
  value = module.state_backend.s3_bucket_id
}
