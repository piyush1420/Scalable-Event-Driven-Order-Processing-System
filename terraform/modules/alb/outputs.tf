output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

# route name -> target group ARN
output "target_group_arns" {
  value = { for name, tg in aws_lb_target_group.this : name => tg.arn }
}
