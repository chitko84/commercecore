variable "aws_region" {
  type    = string
  default = "ap-southeast-1"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "project_name" {
  type    = string
  default = "commercecore"
}

variable "vpc_cidr" {
  type    = string
  default = "10.42.0.0/16"
}

variable "container_images" {
  description = "Immutable ECR image URIs, preferably tagged with a commit SHA."
  type = object({
    core         = string
    notification = string
    gateway      = string
    frontend     = string
  })
  default = {
    core         = "replace-me/commercecore-core:1.0.0"
    notification = "replace-me/commercecore-notification:1.0.0"
    gateway      = "replace-me/commercecore-gateway:1.0.0"
    frontend     = "replace-me/commercecore-frontend:1.0.0"
  }
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "smtp_host" {
  description = "Production SMTP server hostname."
  type        = string
}

variable "smtp_port" {
  description = "Production SMTP submission port."
  type        = number
  default     = 587
}

variable "smtp_username" {
  description = "Production SMTP username; the password remains in Secrets Manager."
  type        = string
}

variable "email_from" {
  description = "Verified From address used for account email."
  type        = string
}

variable "frontend_base_url" {
  description = "Public HTTPS origin used in email links and CORS."
  type        = string
}

variable "public_hostname" {
  description = "Public DNS hostname for the CommerceCore application."
  type        = string
}

variable "route53_zone_id" {
  description = "Route 53 hosted-zone ID that owns public_hostname."
  type        = string
}

variable "certificate_arn" {
  description = "Validated ACM certificate ARN for public_hostname."
  type        = string
}
