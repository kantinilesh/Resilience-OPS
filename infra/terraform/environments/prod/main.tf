# Production Environment Root Configuration
terraform {
  required_version = ">= 1.8.0"
  backend "s3" {
    bucket         = "resilienceops-tf-state-prod"
    key            = "resilienceops/prod/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "resilienceops-tf-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Environment = "prod"
      Application = "ResilienceOps"
      CostCenter  = "CorePlatform"
    }
  }
}

module "vpc" {
  source             = "../../modules/vpc"
  vpc_cidr           = "10.100.0.0/16"
  availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

module "eks" {
  source       = "../../modules/eks"
  cluster_name = "resilienceops-prod-eks"
  subnet_ids   = module.vpc.private_subnet_ids
}
