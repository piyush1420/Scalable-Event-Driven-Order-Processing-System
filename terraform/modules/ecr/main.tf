# Create (or ensure) an ECR repo exists
resource "aws_ecr_repository" "this" {
  name = var.repository_name
  # Lets `terraform destroy` remove the repository even when it still holds images
  force_delete = true
}
