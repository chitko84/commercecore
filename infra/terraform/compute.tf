resource "aws_ecr_repository" "services" {
  for_each             = toset(["core", "notification", "gateway", "frontend"])
  name                 = "${local.name}-${each.key}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_cloudwatch_log_group" "apps" {
  name              = "/ecs/${local.name}"
  retention_in_days = 30
}

resource "aws_ecs_cluster" "main" {
  name = local.name
}

resource "aws_iam_role" "execution" {
  name = "${local.name}-ecs-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "runtime_secrets" {
  name = "runtime-secrets"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = aws_secretsmanager_secret.runtime.arn
    }]
  })
}

resource "aws_iam_role" "task" {
  name = "${local.name}-ecs-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_service_discovery_private_dns_namespace" "main" {
  name = "${local.name}.internal"
  vpc  = aws_vpc.main.id
}

resource "aws_service_discovery_service" "services" {
  for_each = toset(["core", "notification", "gateway"])
  name     = each.key

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id
    dns_records {
      ttl  = 10
      type = "A"
    }
    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_lb" "main" {
  name                       = substr(local.name, 0, 32)
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = values(aws_subnet.public)[*].id
  drop_invalid_header_fields = true
}

resource "aws_lb_target_group" "frontend" {
  name        = substr("${local.name}-web", 0, 32)
  port        = 80
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path = "/healthz"
  }
}

resource "aws_lb_target_group" "gateway" {
  name        = substr("${local.name}-api", 0, 32)
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path = "/actuator/health/readiness"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}

resource "aws_route53_record" "application" {
  zone_id = var.route53_zone_id
  name    = var.public_hostname
  type    = "A"

  alias {
    name                   = aws_lb.main.dns_name
    zone_id                = aws_lb.main.zone_id
    evaluate_target_health = true
  }
}

locals {
  service_ports = {
    core         = 8080
    notification = 8081
    gateway      = 8080
    frontend     = 80
  }
  service_health_paths = {
    core         = "/actuator/health/readiness"
    notification = "/actuator/health/readiness"
    gateway      = "/actuator/health/liveness"
    frontend     = "/healthz"
  }
  service_environment = {
    core = [
      { name = "APP_MAIN", value = "com.commercecore.CommerceCoreApplication" },
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "DB_HOST", value = aws_db_instance.core.address },
      { name = "DB_PORT", value = "5432" },
      { name = "DB_NAME", value = "commercecore" },
      { name = "DB_USERNAME", value = "commercecore" },
      { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.main.bootstrap_brokers },
      { name = "REDIS_HOST", value = aws_elasticache_replication_group.main.primary_endpoint_address },
      { name = "REDIS_PORT", value = "6379" },
      { name = "SPRING_DATA_REDIS_SSL_ENABLED", value = "true" },
      { name = "EVENTS_ENABLED", value = "true" },
      { name = "ASYNC_NOTIFICATIONS", value = "true" },
      { name = "CATALOG_CACHE_ENABLED", value = "true" },
      { name = "SMTP_HOST", value = var.smtp_host },
      { name = "SMTP_PORT", value = tostring(var.smtp_port) },
      { name = "SMTP_USERNAME", value = var.smtp_username },
      { name = "EMAIL_FROM", value = var.email_from },
      { name = "FRONTEND_BASE_URL", value = var.frontend_base_url },
      { name = "CORS_ALLOWED_ORIGINS", value = var.frontend_base_url }
    ]
    notification = [
      { name = "APP_MAIN", value = "com.commerceplatform.notification.NotificationServiceApplication" },
      { name = "NOTIFICATION_DB_URL", value = "jdbc:postgresql://${aws_db_instance.notification.address}:5432/notifications" },
      { name = "NOTIFICATION_DB_USERNAME", value = "notification" },
      { name = "KAFKA_BOOTSTRAP_SERVERS", value = aws_msk_cluster.main.bootstrap_brokers }
    ]
    gateway = [
      { name = "APP_MAIN", value = "com.commerceplatform.gateway.ApiGatewayApplication" },
      { name = "GATEWAY_CORE_URL", value = "http://core.${local.name}.internal:8080" },
      { name = "GATEWAY_NOTIFICATION_URL", value = "http://notification.${local.name}.internal:8081" }
    ]
    frontend = []
  }
  service_secrets = {
    core = [
      { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.runtime.arn}:core_db_password::" },
      { name = "JWT_SIGNING_SECRET", valueFrom = "${aws_secretsmanager_secret.runtime.arn}:jwt_signing_secret::" },
      { name = "SMTP_PASSWORD", valueFrom = "${aws_secretsmanager_secret.runtime.arn}:smtp_password::" }
    ]
    notification = [
      { name = "NOTIFICATION_DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.runtime.arn}:notification_db_password::" },
      { name = "JWT_SIGNING_SECRET", valueFrom = "${aws_secretsmanager_secret.runtime.arn}:jwt_signing_secret::" }
    ]
    gateway  = []
    frontend = []
  }
}

resource "aws_ecs_task_definition" "services" {
  for_each                 = local.service_ports
  family                   = "${local.name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([{
    name         = each.key
    image        = var.container_images[each.key]
    essential    = true
    environment  = local.service_environment[each.key]
    secrets      = local.service_secrets[each.key]
    portMappings = [{ containerPort = each.value, protocol = "tcp" }]
    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://127.0.0.1:${each.value}${local.service_health_paths[each.key]} | grep -q UP"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.apps.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = each.key
      }
    }
  }])
}

resource "aws_ecs_service" "services" {
  for_each        = local.service_ports
  name            = "${local.name}-${each.key}"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.services[each.key].arn
  desired_count   = 2
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = values(aws_subnet.private)[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  dynamic "service_registries" {
    for_each = contains(["core", "notification", "gateway"], each.key) ? [1] : []
    content {
      registry_arn = aws_service_discovery_service.services[each.key].arn
    }
  }

  dynamic "load_balancer" {
    for_each = each.key == "frontend" ? [aws_lb_target_group.frontend.arn] : each.key == "gateway" ? [aws_lb_target_group.gateway.arn] : []
    content {
      target_group_arn = load_balancer.value
      container_name   = each.key
      container_port   = each.value
    }
  }

  depends_on = [aws_lb_listener.http, aws_lb_listener.https]
}
