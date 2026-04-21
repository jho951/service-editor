variable "aws_region" {
  description = "AWS region used for documents-service infrastructure."
  type        = string
  default     = "ap-northeast-2"
}

variable "service_name" {
  description = "Service name used in shared deployment tags."
  type        = string
  default     = "documents-service"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "dev"
}
