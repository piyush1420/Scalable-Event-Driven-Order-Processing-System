variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "service_name" {
  type        = string
  description = "Prefix for every AWS resource name"
  default     = "online-store"
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "log_retention_days" {
  type    = number
  default = 7
}

variable "cart_backend" {
  type        = string
  description = "Which shopping cart to deploy: \"dynamodb\" (HW8 winner, no server) or \"mysql\" (RDS)"
  default     = "dynamodb"

  validation {
    condition     = contains(["dynamodb", "mysql"], var.cart_backend)
    error_message = "cart_backend must be \"dynamodb\" or \"mysql\"."
  }
}

variable "order_consumer" {
  type        = string
  description = "Who processes async orders: \"ecs\" (SNS -> SQS -> worker) or \"lambda\" (SNS -> Lambda)"
  default     = "ecs"

  validation {
    condition     = contains(["ecs", "lambda"], var.order_consumer)
    error_message = "order_consumer must be \"ecs\" or \"lambda\"."
  }
}

variable "num_workers" {
  type        = string
  description = "Worker threads in the order processor (the HW7 experiment tried 1, 5, 20, 100)"
  default     = "1"
}

variable "db_password" {
  type        = string
  description = "RDS master password; only needed when cart_backend = \"mysql\". Put it in terraform.tfvars."
  sensitive   = true
  default     = null
}

variable "cpu_architecture" {
  type        = string
  description = "X86_64, or ARM64 (Graviton: cheaper, and builds faster on Apple Silicon Macs)"
  default     = "X86_64"

  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "cpu_architecture must be \"X86_64\" or \"ARM64\"."
  }
}

variable "lab_role_name" {
  type        = string
  description = "Existing IAM role used by ECS tasks and Lambda (AWS Academy Learner Lab provides LabRole)"
  default     = "LabRole"
}

variable "product_api_scaling" {
  type = object({
    min_capacity = number
    max_capacity = number
    cpu_target   = number
  })
  description = "Auto-scaling for the product API (HW5 settings)"
  default = {
    min_capacity = 2
    max_capacity = 6
    cpu_target   = 70
  }
}
