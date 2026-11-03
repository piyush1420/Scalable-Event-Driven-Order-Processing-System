# Order events. The receiver publishes every order to one SNS topic; exactly ONE consumer path is
# created, so an order is never processed twice:
#   consumer = "ecs"    -> SNS -> SQS queue -> ECS worker (durable: messages wait up to 4 days)
#   consumer = "lambda" -> SNS -> Lambda function (HW7 Part III: serverless, no queue)

locals {
  use_queue  = var.consumer == "ecs"
  use_lambda = var.consumer == "lambda"
}

resource "aws_sns_topic" "orders" {
  name = "${var.service_name}-order-processing-events"
}

# ─── SNS -> SQS -> ECS worker ───

resource "aws_sqs_queue" "orders" {
  count = local.use_queue ? 1 : 0

  name                       = "${var.service_name}-order-processing-queue"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600
  receive_wait_time_seconds  = 20
}

resource "aws_sqs_queue_policy" "orders" {
  count = local.use_queue ? 1 : 0

  queue_url = aws_sqs_queue.orders[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = "*"
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.orders[0].arn
      Condition = {
        ArnEquals = { "aws:SourceArn" = aws_sns_topic.orders.arn }
      }
    }]
  })
}

resource "aws_sns_topic_subscription" "sqs" {
  count = local.use_queue ? 1 : 0

  topic_arn = aws_sns_topic.orders.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.orders[0].arn
}

# ─── SNS -> Lambda ───

resource "aws_lambda_function" "order_processor" {
  count = local.use_lambda ? 1 : 0

  function_name    = "${var.service_name}-order-processor"
  role             = var.lambda_role_arn
  handler          = "com.onlinestore.orders.lambda.OrderEventHandler::handleRequest"
  runtime          = "java21"
  memory_size      = 512
  timeout          = 10
  filename         = var.lambda_jar_path
  source_code_hash = fileexists(var.lambda_jar_path) ? filebase64sha256(var.lambda_jar_path) : null

  lifecycle {
    precondition {
      condition     = fileexists(var.lambda_jar_path)
      error_message = "Lambda jar not found. Build it first: ./mvnw -pl services/lambda-processor package"
    }
  }
}

resource "aws_sns_topic_subscription" "lambda" {
  count = local.use_lambda ? 1 : 0

  topic_arn = aws_sns_topic.orders.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.order_processor[0].arn
}

resource "aws_lambda_permission" "sns" {
  count = local.use_lambda ? 1 : 0

  statement_id  = "AllowSNSInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.order_processor[0].function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.orders.arn
}
