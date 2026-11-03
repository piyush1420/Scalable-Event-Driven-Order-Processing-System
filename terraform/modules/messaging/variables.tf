variable "service_name" {
  type = string
}

variable "consumer" {
  type        = string
  description = "Who processes orders: \"ecs\" (SQS queue + ECS worker) or \"lambda\""

  validation {
    condition     = contains(["ecs", "lambda"], var.consumer)
    error_message = "consumer must be \"ecs\" or \"lambda\"."
  }
}

variable "lambda_role_arn" {
  type    = string
  default = ""
}

variable "lambda_jar_path" {
  type        = string
  description = "Packaged Lambda jar (build it with ./mvnw -pl services/lambda-processor package)"
  default     = ""
}
