# DB Subnet Group: places RDS in the private subnets
resource "aws_db_subnet_group" "this" {
  name       = "${var.service_name}-db-subnet-group"
  subnet_ids = var.private_subnet_ids
  tags       = { Name = "${var.service_name}-db-subnet-group" }
}

# Security Group: only ECS tasks can reach MySQL on 3306
resource "aws_security_group" "rds" {
  name   = "${var.service_name}-rds-sg"
  vpc_id = var.vpc_id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [var.ecs_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.service_name}-rds-sg" }
}

# RDS MySQL Instance (8.4 LTS; standard support for 8.0 on RDS ended in July 2026)
resource "aws_db_instance" "this" {
  identifier             = "${var.service_name}-mysql"
  engine                 = "mysql"
  engine_version         = "8.4"
  instance_class         = "db.t3.micro"
  allocated_storage      = 20
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  skip_final_snapshot    = true
  deletion_protection    = false
  publicly_accessible    = false

  tags = { Name = "${var.service_name}-mysql" }

  lifecycle {
    precondition {
      condition     = var.db_password != null && length(coalesce(var.db_password, "")) >= 8
      error_message = "Set db_password (at least 8 characters) in terraform.tfvars when cart_backend = \"mysql\"."
    }
  }
}
