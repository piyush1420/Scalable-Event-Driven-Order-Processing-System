output "store_url" {
  description = "Base URL of the store (the load balancer)"
  value       = "http://${module.alb.alb_dns_name}"
}

output "alb_dns_name" {
  value = module.alb.alb_dns_name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.store.name
}

output "sns_topic_arn" {
  value = module.messaging.sns_topic_arn
}

output "sqs_queue_url" {
  value = module.messaging.sqs_queue_url
}

output "lambda_function_name" {
  value = module.messaging.lambda_function_name
}

output "dynamodb_table_name" {
  value = try(module.dynamodb[0].table_name, null)
}

output "rds_endpoint" {
  value = try(module.rds[0].endpoint, null)
}
