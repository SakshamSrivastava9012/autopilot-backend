provider "aws" {
  region     = var.region
  access_key = var.access_key
  secret_key = var.secret_key
  token      = var.session_token
}

resource "aws_security_group" "autopilot_sg" {
  name        = "autopilot-${var.deployment_id}"
  description = "Autopilot deployment SG"

  # 🔥 ONLY NGINX PUBLIC
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # 🔧 Optional SSH (remove later in prod)
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_iam_role" "ssm_role" {
  name = "autopilot-ssm-role-${var.deployment_id}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ssm_policy" {
  role       = aws_iam_role.ssm_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "ecr_policy" {
  role       = aws_iam_role.ssm_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "s3_policy" {
  role       = aws_iam_role.ssm_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

resource "aws_iam_instance_profile" "ssm_profile" {
  name = "autopilot-ssm-profile-${var.deployment_id}"
  role = aws_iam_role.ssm_role.name
}

data "aws_security_group" "default" {
  name = "default"
}

resource "aws_instance" "autopilot_instance" {
  ami           = var.ami_id
  instance_type = var.instance_type

  associate_public_ip_address = true
  iam_instance_profile        = aws_iam_instance_profile.ssm_profile.name

  vpc_security_group_ids = [
    aws_security_group.autopilot_sg.id,
    data.aws_security_group.default.id
  ]

  depends_on = [
    aws_iam_role_policy_attachment.ssm_policy,
    aws_iam_role_policy_attachment.ecr_policy,
    aws_iam_role_policy_attachment.s3_policy,
    aws_iam_instance_profile.ssm_profile
  ]

  user_data = <<-EOF
#!/bin/bash

exec > /var/log/user-data.log 2>&1
set +e

echo "=== Starting bootstrap ==="

apt-get update -y
apt-get install -y docker.io awscli snapd curl nginx

# Docker setup
# Docker setup (FINAL STABLE)
apt-get update -y
apt-get install -y docker.io

systemctl daemon-reexec
systemctl daemon-reload

systemctl enable docker

for i in $(seq 1 10); do
  systemctl start docker
  sleep 3
  systemctl is-active --quiet docker && break
done

systemctl is-active docker || exit 1

# wait for docker daemon ready
for i in $(seq 1 15); do
  docker info && break
  sleep 2
done

usermod -aG docker ubuntu

# NGINX setup
systemctl enable nginx
systemctl start nginx

# Snap + SSM
systemctl enable snapd
systemctl start snapd

for i in $(seq 1 10); do
  if [ -S /run/snapd.socket ]; then
    break
  fi
  sleep 3
done

systemctl enable amazon-ssm-agent || true
systemctl start amazon-ssm-agent || true
systemctl enable snap.amazon-ssm-agent.amazon-ssm-agent
systemctl start snap.amazon-ssm-agent.amazon-ssm-agent

sleep 5

REGION="${var.region}"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text 2>/dev/null)

if [ -n "$ACCOUNT_ID" ]; then
  aws ecr get-login-password --region $REGION \
    | docker login --username AWS --password-stdin \
      $ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com
fi

echo "=== Bootstrap complete ==="
EOF

  tags = {
    Name = "autopilot-${var.deployment_id}"
  }
}

