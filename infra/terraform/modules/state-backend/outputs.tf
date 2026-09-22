output "s3_bucket_id" {
  description = "The ID/Name of the S3 bucket for Terraform remote state"
  value       = aws_s3_bucket.state.id
}

output "s3_bucket_arn" {
  description = "The ARN of the S3 bucket for Terraform remote state"
  value       = aws_s3_bucket.state.arn
}

output "dynamodb_table_name" {
  description = "The name of the DynamoDB table for state locking"
  value       = aws_dynamodb_table.locks.name
}

output "dynamodb_table_arn" {
  description = "The ARN of the DynamoDB table for state locking"
  value       = aws_dynamodb_table.locks.arn
}
