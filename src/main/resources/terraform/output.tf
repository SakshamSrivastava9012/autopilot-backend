output "instance_id" {
  value = aws_instance.autopilot_instance.id
}

output "public_ip" {
  value = aws_instance.autopilot_instance.public_ip
}
