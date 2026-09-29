output "application_url" {
  value = "https://${var.public_hostname}"
}

output "ecr_repositories" {
  value = { for name, repository in aws_ecr_repository.services : name => repository.repository_url }
}

output "core_database_endpoint" {
  value     = aws_db_instance.core.endpoint
  sensitive = true
}

output "notification_database_endpoint" {
  value     = aws_db_instance.notification.endpoint
  sensitive = true
}

output "redis_primary_endpoint" {
  value     = aws_elasticache_replication_group.main.primary_endpoint_address
  sensitive = true
}

output "kafka_bootstrap_brokers" {
  value     = aws_msk_cluster.main.bootstrap_brokers
  sensitive = true
}

output "runtime_secret_arn" {
  value = aws_secretsmanager_secret.runtime.arn
}
