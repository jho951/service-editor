locals {
  resource_prefix = "${var.environment}-${var.service_name}"
  runtime_name    = var.service_runtime_name == "" ? var.service_name : var.service_runtime_name

  common_tags = merge(var.tags, {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
    Service     = var.service_name
    Role        = var.service_role
  })

  vpc_id             = var.create_vpc ? aws_vpc.main[0].id : var.existing_vpc_id
  public_subnet_ids  = var.create_vpc ? aws_subnet.public[*].id : var.existing_public_subnet_ids
  private_subnet_ids = var.create_vpc ? aws_subnet.private[*].id : var.existing_private_subnet_ids
  vpc_cidr           = var.create_vpc ? var.vpc_cidr : var.existing_vpc_cidr

  alb_internal = var.alb_internal == null ? var.service_name != "gateway-service" : var.alb_internal
  alb_subnet_ids = local.alb_internal ? local.private_subnet_ids : local.public_subnet_ids
  effective_app_ingress_cidrs = length(var.app_ingress_cidrs) > 0 ? var.app_ingress_cidrs : (
    local.alb_internal ? [local.vpc_cidr] : ["0.0.0.0/0"]
  )
  effective_test_listener_ingress_cidrs = length(var.test_listener_ingress_cidrs) > 0 ? var.test_listener_ingress_cidrs : local.effective_app_ingress_cidrs

  ecr_repository_name = var.ecr_repository_name == "" ? local.resource_prefix : var.ecr_repository_name
  app_image           = var.container_image != "" ? var.container_image : "${aws_ecr_repository.service[0].repository_url}:${var.image_tag}"

  base_app_env = {
    SERVICE_NAME           = var.service_name
    SPRING_PROFILES_ACTIVE = var.environment
    SERVER_PORT            = tostring(var.app_port)
  }

  mysql_env = var.enable_mysql ? {
    (var.mysql_url_env_name)      = "jdbc:mysql://${aws_db_instance.mysql[0].address}:${aws_db_instance.mysql[0].port}/${var.mysql_database_name}${var.mysql_jdbc_query}"
    (var.mysql_username_env_name) = var.mysql_username
  } : {}

  mysql_secret_env = var.enable_mysql ? {
    (var.mysql_password_env_name) = var.mysql_password
  } : {}

  app_env        = merge(local.base_app_env, local.mysql_env, var.app_env)
  app_secret_env  = merge(local.mysql_secret_env, var.app_secret_env)
  app_secret_keys = nonsensitive(sort(keys(local.app_secret_env)))

  container_base = {
    name      = local.runtime_name
    image     = local.app_image
    essential = true

    portMappings = [
      {
        containerPort = var.app_port
        hostPort      = var.app_port
        protocol      = "tcp"
      }
    ]

    environment = [
      for key in sort(keys(local.app_env)) : {
        name  = key
        value = local.app_env[key]
      }
    ]

    secrets = [
      for key in local.app_secret_keys : {
        name      = key
        valueFrom = "${aws_secretsmanager_secret.app_env.arn}:${key}::"
      }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.service.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = local.runtime_name
      }
    }
  }

  container_definition = merge(
    local.container_base,
    length(var.container_command) > 0 ? { command = var.container_command } : {}
  )
}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  count = var.create_vpc ? 1 : 0

  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-vpc"
  })
}

resource "aws_internet_gateway" "main" {
  count = var.create_vpc ? 1 : 0

  vpc_id = aws_vpc.main[0].id

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-igw"
  })
}

resource "aws_subnet" "public" {
  count = var.create_vpc ? length(var.public_subnet_cidrs) : 0

  vpc_id                  = aws_vpc.main[0].id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-public-${count.index + 1}"
    Tier = "public"
  })
}

resource "aws_subnet" "private" {
  count = var.create_vpc ? length(var.private_subnet_cidrs) : 0

  vpc_id            = aws_vpc.main[0].id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-private-${count.index + 1}"
    Tier = "private"
  })
}

resource "aws_route_table" "public" {
  count = var.create_vpc ? 1 : 0

  vpc_id = aws_vpc.main[0].id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main[0].id
  }

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-public-rt"
  })
}

resource "aws_route_table_association" "public" {
  count = var.create_vpc ? length(aws_subnet.public) : 0

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public[0].id
}

resource "aws_eip" "nat" {
  count = var.create_vpc ? 1 : 0

  domain = "vpc"

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-nat-eip"
  })
}

resource "aws_nat_gateway" "main" {
  count = var.create_vpc ? 1 : 0

  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-nat"
  })

  depends_on = [aws_internet_gateway.main]
}

resource "aws_route_table" "private" {
  count = var.create_vpc ? 1 : 0

  vpc_id = aws_vpc.main[0].id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[0].id
  }

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-private-rt"
  })
}

resource "aws_route_table_association" "private" {
  count = var.create_vpc ? length(aws_subnet.private) : 0

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[0].id
}

resource "aws_security_group" "alb" {
  name        = "${local.resource_prefix}-alb-sg"
  description = "Ingress for ${var.service_name} ALB"
  vpc_id      = local.vpc_id

  dynamic "ingress" {
    for_each = length(var.alb_ingress_source_security_group_ids) == 0 ? [1] : []
    content {
      description = "Production listener"
      from_port   = var.alb_listener_port
      to_port     = var.alb_listener_port
      protocol    = "tcp"
      cidr_blocks = local.effective_app_ingress_cidrs
    }
  }

  dynamic "ingress" {
    for_each = length(var.alb_ingress_source_security_group_ids) == 0 ? [1] : []
    content {
      description = "CodeDeploy test listener"
      from_port   = var.alb_test_listener_port
      to_port     = var.alb_test_listener_port
      protocol    = "tcp"
      cidr_blocks = local.effective_test_listener_ingress_cidrs
    }
  }

  dynamic "ingress" {
    for_each = var.alb_ingress_source_security_group_ids
    content {
      description     = "Listener access from shared caller security groups"
      from_port       = var.alb_listener_port
      to_port         = var.alb_listener_port
      protocol        = "tcp"
      security_groups = [ingress.value]
    }
  }

  dynamic "ingress" {
    for_each = var.alb_ingress_source_security_group_ids
    content {
      description     = "Test listener access from shared caller security groups"
      from_port       = var.alb_test_listener_port
      to_port         = var.alb_test_listener_port
      protocol        = "tcp"
      security_groups = [ingress.value]
    }
  }

  egress {
    description = "Outbound to ECS tasks"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [local.vpc_cidr]
  }

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-alb-sg"
  })
}

resource "aws_security_group" "ecs" {
  name        = "${local.resource_prefix}-ecs-sg"
  description = "Ingress from ALB to ${var.service_name} tasks"
  vpc_id      = local.vpc_id

  ingress {
    description     = "Service traffic from ALB"
    from_port       = var.app_port
    to_port         = var.app_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Outbound dependencies"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-ecs-sg"
  })
}

resource "aws_security_group" "mysql" {
  count = var.enable_mysql ? 1 : 0

  name        = "${local.resource_prefix}-mysql-sg"
  description = "Allow MySQL only from ${var.service_name} ECS tasks"
  vpc_id      = local.vpc_id

  ingress {
    description     = "MySQL from ECS tasks"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-mysql-sg"
  })
}

resource "aws_db_subnet_group" "mysql" {
  count = var.enable_mysql ? 1 : 0

  name       = "${local.resource_prefix}-db-subnets"
  subnet_ids = local.private_subnet_ids

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-db-subnets"
  })
}

resource "aws_db_instance" "mysql" {
  count = var.enable_mysql ? 1 : 0

  identifier = "${local.resource_prefix}-mysql"

  engine                = "mysql"
  engine_version        = var.mysql_engine_version
  instance_class        = var.mysql_instance_class
  allocated_storage     = var.mysql_allocated_storage
  max_allocated_storage = var.mysql_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.mysql_database_name
  username = var.mysql_username
  password = var.mysql_password
  port     = 3306

  db_subnet_group_name   = aws_db_subnet_group.mysql[0].name
  vpc_security_group_ids = [aws_security_group.mysql[0].id]
  publicly_accessible    = false
  multi_az               = var.mysql_multi_az

  backup_retention_period = var.mysql_backup_retention_days
  deletion_protection     = var.mysql_deletion_protection
  skip_final_snapshot     = var.mysql_skip_final_snapshot
  final_snapshot_identifier = (
    var.mysql_skip_final_snapshot ? null : "${local.resource_prefix}-mysql-final"
  )

  auto_minor_version_upgrade = true
  apply_immediately          = var.mysql_apply_immediately

  tags = merge(local.common_tags, {
    Name = "${local.resource_prefix}-mysql"
  })
}

resource "aws_ecr_repository" "service" {
  count = var.create_ecr_repository ? 1 : 0

  name                 = local.ecr_repository_name
  image_tag_mutability = var.ecr_image_tag_mutability

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = local.common_tags
}

resource "aws_ecr_lifecycle_policy" "service" {
  count = var.create_ecr_repository ? 1 : 0

  repository = aws_ecr_repository.service[0].name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep the most recent ${var.service_name} images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_keep_image_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_secretsmanager_secret" "app_env" {
  name                    = "${local.resource_prefix}/app-env"
  recovery_window_in_days = var.secret_recovery_window_days

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "app_env" {
  secret_id     = aws_secretsmanager_secret.app_env.id
  secret_string = jsonencode(local.app_secret_env)
}

resource "aws_cloudwatch_log_group" "service" {
  name              = "/ecs/${local.resource_prefix}"
  retention_in_days = var.log_retention_days

  tags = local.common_tags
}

resource "aws_lb" "service" {
  name               = "${local.resource_prefix}-alb"
  load_balancer_type = "application"
  internal           = local.alb_internal
  security_groups    = [aws_security_group.alb.id]
  subnets            = local.alb_subnet_ids

  tags = local.common_tags
}

resource "aws_route53_record" "private_service" {
  count = var.private_dns_zone_id != "" && var.private_dns_name != "" ? 1 : 0

  zone_id = var.private_dns_zone_id
  name    = var.private_dns_name
  type    = "A"

  alias {
    name                   = aws_lb.service.dns_name
    zone_id                = aws_lb.service.zone_id
    evaluate_target_health = true
  }
}

resource "aws_lb_target_group" "blue" {
  name        = "${local.resource_prefix}-blue"
  port        = var.app_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = local.vpc_id

  deregistration_delay = var.target_deregistration_delay

  health_check {
    enabled             = true
    path                = var.health_check_path
    matcher             = var.health_check_matcher
    interval            = var.health_check_interval
    timeout             = var.health_check_timeout
    healthy_threshold   = var.healthy_threshold
    unhealthy_threshold = var.unhealthy_threshold
  }

  tags = merge(local.common_tags, {
    Color = "blue"
  })
}

resource "aws_lb_target_group" "green" {
  name        = "${local.resource_prefix}-green"
  port        = var.app_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = local.vpc_id

  deregistration_delay = var.target_deregistration_delay

  health_check {
    enabled             = true
    path                = var.health_check_path
    matcher             = var.health_check_matcher
    interval            = var.health_check_interval
    timeout             = var.health_check_timeout
    healthy_threshold   = var.healthy_threshold
    unhealthy_threshold = var.unhealthy_threshold
  }

  tags = merge(local.common_tags, {
    Color = "green"
  })
}

resource "aws_lb_listener" "prod" {
  load_balancer_arn = aws_lb.service.arn
  port              = var.alb_listener_port
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.blue.arn
  }

  tags = local.common_tags
}

resource "aws_lb_listener" "test" {
  load_balancer_arn = aws_lb.service.arn
  port              = var.alb_test_listener_port
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.green.arn
  }

  tags = local.common_tags
}

resource "aws_iam_role" "task_execution" {
  name = "${local.resource_prefix}-task-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "task_execution_secret" {
  name = "${local.resource_prefix}-task-secret-read"
  role = aws_iam_role.task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = aws_secretsmanager_secret.app_env.arn
      }
    ]
  })
}

resource "aws_iam_role" "task" {
  name = "${local.resource_prefix}-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = local.common_tags
}

resource "aws_ecs_cluster" "service" {
  name = "${local.resource_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }

  tags = local.common_tags
}

resource "aws_ecs_task_definition" "service" {
  family                   = local.resource_prefix
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(var.task_cpu)
  memory                   = tostring(var.task_memory)
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn
  container_definitions    = jsonencode([local.container_definition])

  tags = local.common_tags
}

resource "aws_ecs_service" "service" {
  name            = local.resource_prefix
  cluster         = aws_ecs_cluster.service.id
  task_definition = aws_ecs_task_definition.service.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  deployment_controller {
    type = "CODE_DEPLOY"
  }

  network_configuration {
    subnets          = local.private_subnet_ids
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.blue.arn
    container_name   = local.runtime_name
    container_port   = var.app_port
  }

  health_check_grace_period_seconds = var.health_check_grace_period_seconds
  enable_execute_command            = var.enable_execute_command

  lifecycle {
    ignore_changes = [
      task_definition,
      load_balancer,
      desired_count
    ]
  }

  depends_on = [
    aws_lb_listener.prod,
    aws_lb_listener.test,
    aws_iam_role_policy_attachment.task_execution
  ]

  tags = local.common_tags
}

resource "aws_iam_role" "codedeploy" {
  name = "${local.resource_prefix}-codedeploy-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "codedeploy.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "codedeploy_ecs" {
  role       = aws_iam_role.codedeploy.name
  policy_arn = "arn:aws:iam::aws:policy/AWSCodeDeployRoleForECS"
}

resource "aws_codedeploy_app" "service" {
  compute_platform = "ECS"
  name             = "${local.resource_prefix}-codedeploy"

  tags = local.common_tags
}

resource "aws_codedeploy_deployment_group" "service" {
  app_name               = aws_codedeploy_app.service.name
  deployment_group_name  = "${local.resource_prefix}-blue-green"
  service_role_arn       = aws_iam_role.codedeploy.arn
  deployment_config_name = var.codedeploy_deployment_config_name

  auto_rollback_configuration {
    enabled = true
    events  = ["DEPLOYMENT_FAILURE"]
  }

  blue_green_deployment_config {
    deployment_ready_option {
      action_on_timeout    = "CONTINUE_DEPLOYMENT"
      wait_time_in_minutes = var.deployment_ready_wait_minutes
    }

    terminate_blue_instances_on_deployment_success {
      action                           = "TERMINATE"
      termination_wait_time_in_minutes = var.blue_termination_wait_minutes
    }
  }

  deployment_style {
    deployment_option = "WITH_TRAFFIC_CONTROL"
    deployment_type   = "BLUE_GREEN"
  }

  ecs_service {
    cluster_name = aws_ecs_cluster.service.name
    service_name = aws_ecs_service.service.name
  }

  load_balancer_info {
    target_group_pair_info {
      prod_traffic_route {
        listener_arns = [aws_lb_listener.prod.arn]
      }

      test_traffic_route {
        listener_arns = [aws_lb_listener.test.arn]
      }

      target_group {
        name = aws_lb_target_group.blue.name
      }

      target_group {
        name = aws_lb_target_group.green.name
      }
    }
  }

  depends_on = [
    aws_iam_role_policy_attachment.codedeploy_ecs
  ]

  tags = local.common_tags
}
