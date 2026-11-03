# The whole online store on AWS:
#
#   Internet -> ALB (one front door, routes by path)
#                 /products, /inventory/mode/*, /metrics -> products service (product-api + inventory sidecar)
#                 /shopping-carts                         -> cart service (DynamoDB or MySQL)
#                 /orders                                 -> order receiver -> SNS -> SQS -> order processor
#                                                                                  (or SNS -> Lambda)
# Every service runs on ECS Fargate in private subnets, in one shared cluster.

locals {
  name         = var.service_name
  use_dynamodb = var.cart_backend == "dynamodb"
  use_lambda   = var.order_consumer == "lambda"
  platform     = var.cpu_architecture == "ARM64" ? "linux/arm64" : "linux/amd64"

  # One container image per service that runs on ECS: image key -> source folder
  images = merge(
    {
      "product-api"       = "${path.root}/../services/product-api"
      "inventory-service" = "${path.root}/../services/inventory-service"
      "shopping-cart"     = "${path.root}/../services/${local.use_dynamodb ? "shopping-cart-dynamo" : "shopping-cart"}"
      "order-receiver"    = "${path.root}/../services/order-receiver"
    },
    local.use_lambda ? {} : { "order-processor" = "${path.root}/../services/order-processor" }
  )

  # Hash of each service's sources: a code change gives a new image tag, which rolls out new tasks
  source_hash = {
    for key, dir in local.images : key => sha1(join("", [
      for f in sort(setunion(fileset(dir, "pom.xml"), fileset(dir, "Dockerfile"), fileset(dir, "src/main/**"))) :
      filesha1("${dir}/${f}")
    ]))
  }

  lambda_jar_path = "${path.root}/../services/lambda-processor/target/lambda-processor.jar"
}

data "aws_iam_role" "lab_role" {
  name = var.lab_role_name
}

module "network" {
  source         = "./modules/network"
  service_name   = local.name
  container_port = var.container_port
}

module "logging" {
  source            = "./modules/logging"
  service_name      = local.name
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_cluster" "store" {
  name = "${local.name}-cluster"
}

# ── Container images: one ECR repository each, built from the service folder and pushed ──

module "ecr" {
  for_each        = local.images
  source          = "./modules/ecr"
  repository_name = "${local.name}-${each.key}"
}

resource "docker_image" "service" {
  for_each = local.images

  name = "${module.ecr[each.key].repository_url}:${substr(local.source_hash[each.key], 0, 12)}"
  build {
    context  = each.value
    platform = local.platform
  }
  triggers = {
    source_hash = local.source_hash[each.key]
  }
}

resource "docker_registry_image" "service" {
  for_each = local.images

  name          = docker_image.service[each.key].name
  keep_remotely = true
}

# ── Front door ──

module "alb" {
  source            = "./modules/alb"
  service_name      = local.name
  vpc_id            = module.network.vpc_id
  subnet_ids        = module.network.public_subnet_ids
  security_group_id = module.network.alb_security_group_id

  routes = {
    products = {
      port     = var.container_port
      priority = 10
      paths    = ["/products", "/products/*", "/inventory/mode/*", "/metrics"]
    }
    carts = {
      port     = var.container_port
      priority = 20
      paths    = ["/shopping-carts", "/shopping-carts/*"]
    }
    orders = {
      port     = var.container_port
      priority = 30
      paths    = ["/orders/*"]
    }
  }
}

# ── Data stores ──

module "dynamodb" {
  count        = local.use_dynamodb ? 1 : 0
  source       = "./modules/dynamodb"
  service_name = local.name
}

module "rds" {
  count                 = local.use_dynamodb ? 0 : 1
  source                = "./modules/rds"
  service_name          = local.name
  vpc_id                = module.network.vpc_id
  private_subnet_ids    = module.network.private_subnet_ids
  ecs_security_group_id = module.network.ecs_security_group_id
  db_password           = var.db_password
}

module "messaging" {
  source          = "./modules/messaging"
  service_name    = local.name
  consumer        = var.order_consumer
  lambda_role_arn = data.aws_iam_role.lab_role.arn
  lambda_jar_path = local.use_lambda ? local.lambda_jar_path : ""
}

# ── Services ──

locals {
  common = {
    cluster_id         = aws_ecs_cluster.store.id
    cluster_name       = aws_ecs_cluster.store.name
    subnet_ids         = module.network.private_subnet_ids
    security_group_ids = [module.network.ecs_security_group_id]
    role_arn           = data.aws_iam_role.lab_role.arn
  }

  cart_environment = local.use_dynamodb ? [
    { name = "DYNAMODB_TABLE", value = try(module.dynamodb[0].table_name, "") },
    { name = "AWS_REGION", value = var.aws_region },
    ] : [
    { name = "DB_URL", value = "jdbc:mysql://${try(module.rds[0].endpoint, "")}/${try(module.rds[0].db_name, "")}" },
    { name = "DB_USER", value = try(module.rds[0].username, "") },
    { name = "DB_PASSWORD", value = coalesce(var.db_password, "unset") },
  ]
}

# Product API with the inventory service as a sidecar in the same task (reached on localhost:8081)
module "products" {
  source             = "./modules/ecs"
  service_name       = "${local.name}-products"
  cluster_id         = local.common.cluster_id
  cluster_name       = local.common.cluster_name
  image              = docker_registry_image.service["product-api"].name
  container_port     = var.container_port
  subnet_ids         = local.common.subnet_ids
  security_group_ids = local.common.security_group_ids
  execution_role_arn = local.common.role_arn
  task_role_arn      = local.common.role_arn
  log_group_name     = module.logging.log_group_name
  region             = var.aws_region
  cpu_architecture   = var.cpu_architecture
  target_group_arn   = module.alb.target_group_arns["products"]
  ecs_count          = var.product_api_scaling.min_capacity
  autoscaling        = var.product_api_scaling
  cpu                = "1024"
  memory             = "2048"

  environment = [
    { name = "INVENTORY_URL", value = "http://localhost:8081" }
  ]
  sidecars = [{
    name        = "inventory-service"
    image       = docker_registry_image.service["inventory-service"].name
    port        = 8081
    memory      = 512
    environment = []
  }]
}

module "cart" {
  source             = "./modules/ecs"
  service_name       = "${local.name}-cart"
  cluster_id         = local.common.cluster_id
  cluster_name       = local.common.cluster_name
  image              = docker_registry_image.service["shopping-cart"].name
  container_port     = var.container_port
  subnet_ids         = local.common.subnet_ids
  security_group_ids = local.common.security_group_ids
  execution_role_arn = local.common.role_arn
  task_role_arn      = local.common.role_arn
  log_group_name     = module.logging.log_group_name
  region             = var.aws_region
  cpu_architecture   = var.cpu_architecture
  target_group_arn   = module.alb.target_group_arns["carts"]
  environment        = local.cart_environment
}

module "order_receiver" {
  source             = "./modules/ecs"
  service_name       = "${local.name}-order-receiver"
  cluster_id         = local.common.cluster_id
  cluster_name       = local.common.cluster_name
  image              = docker_registry_image.service["order-receiver"].name
  container_port     = var.container_port
  subnet_ids         = local.common.subnet_ids
  security_group_ids = local.common.security_group_ids
  execution_role_arn = local.common.role_arn
  task_role_arn      = local.common.role_arn
  log_group_name     = module.logging.log_group_name
  region             = var.aws_region
  cpu_architecture   = var.cpu_architecture
  target_group_arn   = module.alb.target_group_arns["orders"]

  environment = [
    { name = "SNS_TOPIC_ARN", value = module.messaging.sns_topic_arn },
    { name = "AWS_REGION", value = var.aws_region },
  ]
}

# Order processor: no load balancer, it pulls work from SQS (only in "ecs" consumer mode)
module "order_processor" {
  count              = local.use_lambda ? 0 : 1
  source             = "./modules/ecs"
  service_name       = "${local.name}-order-processor"
  cluster_id         = local.common.cluster_id
  cluster_name       = local.common.cluster_name
  image              = docker_registry_image.service["order-processor"].name
  container_port     = var.container_port
  subnet_ids         = local.common.subnet_ids
  security_group_ids = local.common.security_group_ids
  execution_role_arn = local.common.role_arn
  task_role_arn      = local.common.role_arn
  log_group_name     = module.logging.log_group_name
  region             = var.aws_region
  cpu_architecture   = var.cpu_architecture

  environment = [
    { name = "SQS_QUEUE_URL", value = coalesce(module.messaging.sqs_queue_url, "unset") },
    { name = "AWS_REGION", value = var.aws_region },
    { name = "NUM_WORKERS", value = var.num_workers },
  ]
}
