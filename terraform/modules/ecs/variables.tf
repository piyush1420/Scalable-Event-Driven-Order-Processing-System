variable "service_name" {
  type = string
}

variable "cluster_id" {
  type        = string
  description = "ID of the shared ECS cluster"
}

variable "cluster_name" {
  type        = string
  description = "Name of the shared ECS cluster (used by auto-scaling)"
}

variable "image" {
  type = string
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_ids" {
  type = list(string)
}

variable "execution_role_arn" {
  type = string
}

variable "task_role_arn" {
  type = string
}

variable "log_group_name" {
  type = string
}

variable "ecs_count" {
  type    = number
  default = 1
}

variable "region" {
  type = string
}

# Java services get 0.5 vCPU / 1 GB by default (the Go versions ran on 0.25 vCPU / 512 MB)
variable "cpu" {
  type    = string
  default = "512"
}

variable "memory" {
  type    = string
  default = "1024"
}

variable "cpu_architecture" {
  type    = string
  default = "X86_64"
}

variable "target_group_arn" {
  type    = string
  default = ""
}

variable "assign_public_ip" {
  type    = bool
  default = false
}

variable "environment" {
  type = list(object({
    name  = string
    value = string
  }))
  default = []
}

# Extra containers in the same task; they reach each other on localhost
variable "sidecars" {
  type = list(object({
    name   = string
    image  = string
    port   = number
    memory = number
    environment = list(object({
      name  = string
      value = string
    }))
  }))
  default = []
}

# null = fixed task count; otherwise scale between min and max on average CPU
variable "autoscaling" {
  type = object({
    min_capacity = number
    max_capacity = number
    cpu_target   = number
  })
  default = null
}
