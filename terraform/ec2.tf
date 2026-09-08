resource "aws_instance" "moneylog_server" {
  ami           = "ami-00b5b2470beafd65f"
  instance_type = "t3.micro"

  subnet_id                   = "subnet-05de067d3a72e43dc"
  vpc_security_group_ids      = [aws_security_group.moneylog_sg.id]
  associate_public_ip_address = true

  key_name             = "moneylog-key"
  iam_instance_profile = aws_iam_instance_profile.moneylog_ec2_ssm_role.name

  ebs_optimized = true

  monitoring = false

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    iops                  = 3000
    throughput            = 125
    encrypted             = false
    delete_on_termination = true
  }

  metadata_options {
    http_tokens                  = "required"
    http_put_response_hop_limit  = 2
    http_endpoint                = "enabled"
  }

  tags = {
    Name = "moneylog-server"
  }
}
