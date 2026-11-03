output "sns_topic_arn" {
  value = aws_sns_topic.orders.arn
}

# null when the Lambda consumer is used
output "sqs_queue_url" {
  value = one(aws_sqs_queue.orders[*].id)
}

output "lambda_function_name" {
  value = one(aws_lambda_function.order_processor[*].function_name)
}
