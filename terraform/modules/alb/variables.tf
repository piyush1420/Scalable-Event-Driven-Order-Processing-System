variable "service_name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

# key = short route name (used in resource names), e.g.
# { products = { port = 8080, priority = 10, paths = ["/products", "/products/*"] } }
variable "routes" {
  type = map(object({
    port     = number
    priority = number
    paths    = list(string)
  }))
}
