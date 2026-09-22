variable "bucket_name" {
  description = "Name of the S3 bucket to store Terraform remote state"
  type        = string
  default     = "resilienceops-tf-state"
}

variable "dynamodb_table_name" {
  description = "Name of the DynamoDB table for distributed state locking"
  type        = string
  default     = "resilienceops-tf-locks"
}

variable "environment" {
  description = "Deployment environment name (dev, staging, prod)"
  type        = string
}
