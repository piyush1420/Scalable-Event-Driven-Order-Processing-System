# One ECS Fargate service in the shared cluster: task definition, service, optional sidecar
# containers (they share localhost with the main container) and optional CPU auto-scaling.

locals {
  sidecar_memory = sum(concat([0], [for s in var.sidecars : s.memory]))

  main_container = {
    name      = "${var.service_name}-container"
    image     = var.image
    essential = true
    # with sidecars, the main container gets whatever memory they leave
    memory       = length(var.sidecars) > 0 ? tonumber(var.memory) - local.sidecar_memory : null
    portMappings = [{ containerPort = var.container_port }]
    environment  = var.environment
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = var.log_group_name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }

  sidecar_containers = [for s in var.sidecars : {
    name         = s.name
    image        = s.image
    essential    = true
    memory       = s.memory
    portMappings = [{ containerPort = s.port }]
    environment  = s.environment
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = var.log_group_name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }]
}

# Task Definition
resource "aws_ecs_task_definition" "this" {
  family                   = "${var.service_name}-task"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory

  execution_role_arn = var.execution_role_arn
  task_role_arn      = var.task_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode(concat([local.main_container], local.sidecar_containers))
}

# ECS Service, with optional ALB
resource "aws_ecs_service" "this" {
  name            = var.service_name
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.ecs_count
  launch_type     = "FARGATE"

  # Java services need a little time to start before the ALB health checks count
  health_check_grace_period_seconds = var.target_group_arn != "" ? 90 : null

  dynamic "load_balancer" {
    for_each = var.target_group_arn != "" ? [1] : []
    content {
      target_group_arn = var.target_group_arn
      container_name   = "${var.service_name}-container"
      container_port   = var.container_port
    }
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = var.assign_public_ip
  }
}

# Auto Scaling (HW5): keep average CPU near the target by adding or removing tasks
resource "aws_appautoscaling_target" "ecs" {
  count = var.autoscaling == null ? 0 : 1

  max_capacity       = var.autoscaling.max_capacity
  min_capacity       = var.autoscaling.min_capacity
  resource_id        = "service/${var.cluster_name}/${aws_ecs_service.this.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  count = var.autoscaling == null ? 0 : 1

  name               = "${var.service_name}-cpu-scaling"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.ecs[0].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs[0].scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs[0].service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = var.autoscaling.cpu_target
    scale_in_cooldown  = 60
    scale_out_cooldown = 60

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
