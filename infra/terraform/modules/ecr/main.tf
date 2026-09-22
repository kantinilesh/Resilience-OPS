# Reusable ECR Repositories Module for Microservices
terraform {
  required_version = ">= 1.8.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
  }
}

resource "aws_ecr_repository" "repos" {
  for_each             = toset(var.repository_names)
  name                 = "resilienceops/${each.value}"
  image_tag_mutability = var.image_tag_mutability

  image_scanning_configuration {
    scan_on_push = var.scan_on_push
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name        = "resilienceops-${each.value}"
    Environment = var.environment
    Service     = each.value
    ManagedBy   = "Terraform"
  }
}

resource "aws_ecr_lifecycle_policy" "lifecycle" {
  for_each   = aws_ecr_repository.repos
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images older than ${var.untagged_image_expiration_days} days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.untagged_image_expiration_days
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Retain at most ${var.max_tagged_images_to_retain} tagged images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["v", "build-"]
          countType     = "imageCountMoreThan"
          countNumber   = var.max_tagged_images_to_retain
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
