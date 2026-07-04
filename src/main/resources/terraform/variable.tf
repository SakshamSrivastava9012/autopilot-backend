variable "region" {}

variable "access_key" {}

variable "secret_key" {}

variable "session_token" {}

variable "deployment_id" {}

variable "instance_type" {}

variable "app_port" {}

variable "ami_id" {}

variable "rds_security_group_id" {
  type    = string
  default = ""
}

variable "rds_port" {
  type    = number
  default = 3306
}