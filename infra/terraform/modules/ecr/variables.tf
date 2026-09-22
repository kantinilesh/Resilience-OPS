variable "repository_names" {
  description = "List of ECR repository names to create"
  type        = list(string)
  default     = ["order-service", "inventory-service", "remediation-service"]
}

variable "environment" {
  description = "Deployment environment name (dev, staging, prod)"
  type        = string
}

variable "image_tag_mutability" {
  description = "Tag mutability setting (MUTABLE or IMMUTABLE)"
  type        = string
  default     = "MUTABLE"
}

variable "scan_on_push" {
  description = "Indicates whether images are scanned after being pushed to the repository"
  type        = bool
  default     = true
}

variable "untagged_image_expiration_days" {
  description = "Days after which untagged images will be purged by lifecycle policy"
  type        = number
  default     = 14
}

variable "max_tagged_images_to_retain" {
  description = "Maximum number of tagged images to retain per repository"
  type        = number
  default     = 30
}
