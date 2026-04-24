output "service_name" {
  description = "Logical service name."
  value       = var.service_name
}

output "ecr_repository_url" {
  description = "Push service images to this ECR repository URL when create_ecr_repository is true."
  value       = var.create_ecr_repository ? aws_ecr_repository.service[0].repository_url : null
}

output "alb_dns_name" {
  description = "ALB DNS name."
  value       = aws_lb.service.dns_name
}

output "alb_zone_id" {
  description = "ALB hosted zone ID."
  value       = aws_lb.service.zone_id
}

output "vpc_id" {
  description = "VPC ID used by this stack."
  value       = local.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet IDs associated with the stack."
  value       = local.public_subnet_ids
}

output "private_subnet_ids" {
  description = "Private subnet IDs associated with the stack."
  value       = local.private_subnet_ids
}

output "alb_security_group_id" {
  description = "ALB security group ID."
  value       = aws_security_group.alb.id
}

output "ecs_security_group_id" {
  description = "ECS task security group ID."
  value       = aws_security_group.ecs.id
}

output "service_url" {
  description = "Production listener URL."
  value       = var.private_dns_name != "" ? "http://${trimsuffix(var.private_dns_name, ".")}:${var.alb_listener_port}" : "http://${aws_lb.service.dns_name}:${var.alb_listener_port}"
}

output "test_url" {
  description = "CodeDeploy test listener URL for the green target group."
  value       = "http://${aws_lb.service.dns_name}:${var.alb_test_listener_port}"
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.service.name
}

output "ecs_service_name" {
  description = "ECS service name."
  value       = aws_ecs_service.service.name
}

output "task_definition_arn" {
  description = "Current Terraform-rendered task definition ARN."
  value       = aws_ecs_task_definition.service.arn
}

output "codedeploy_app_name" {
  description = "CodeDeploy ECS application name."
  value       = aws_codedeploy_app.service.name
}

output "codedeploy_deployment_group_name" {
  description = "CodeDeploy ECS deployment group name."
  value       = aws_codedeploy_deployment_group.service.deployment_group_name
}

output "blue_target_group_name" {
  description = "Blue target group name."
  value       = aws_lb_target_group.blue.name
}

output "green_target_group_name" {
  description = "Green target group name."
  value       = aws_lb_target_group.green.name
}

output "mysql_endpoint" {
  description = "Private MySQL endpoint when enable_mysql is true."
  value       = var.enable_mysql ? aws_db_instance.mysql[0].endpoint : null
}

output "app_secret_arn" {
  description = "Secrets Manager secret containing service secret environment variables."
  value       = aws_secretsmanager_secret.app_env.arn
}

output "private_dns_fqdn" {
  description = "Private DNS name when Route53 private DNS alias is configured."
  value       = var.private_dns_name != "" ? trimsuffix(var.private_dns_name, ".") : null
}
