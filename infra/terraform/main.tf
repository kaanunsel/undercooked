terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.40"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# Static external IP so classmates' APK / iOS builds keep the same server IP across VM restarts
resource "google_compute_address" "undercooked_ip" {
  name   = "undercooked-static-ip"
  region = var.region
}

# Allow raw TCP traffic on port 6789 to our Java ServerSocket
resource "google_compute_firewall" "allow_game_tcp" {
  name    = "undercooked-allow-tcp-${var.server_port}"
  network = "default"

  allow {
    protocol = "tcp"
    ports    = [tostring(var.server_port)]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["undercooked-server"]
}

# Single Compute Engine VM running Ubuntu 24.04 + OpenJDK 21 + systemd service (no auto-scaling)
resource "google_compute_instance" "undercooked_vm" {
  name         = "undercooked-server"
  machine_type = var.machine_type
  zone         = var.zone
  tags         = ["undercooked-server"]

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2404-lts-amd64"
      size  = 15
      type  = "pd-balanced"
    }
  }

  network_interface {
    network = "default"
    access_config {
      nat_ip = google_compute_address.undercooked_ip.address
    }
  }

  metadata_startup_script = <<-EOT
    #!/bin/bash
    set -euo pipefail

    apt-get update -y
    apt-get install -y openjdk-21-jre-headless

    mkdir -p /opt/undercooked
    chmod 777 /opt/undercooked

    cat << 'SERVICE' > /etc/systemd/system/undercooked.service
    [Unit]
    Description=Undercooked CMPE 436 Plain Java TCP Game Server
    After=network.target

    [Service]
    Type=simple
    WorkingDirectory=/opt/undercooked
    ExecStart=/usr/bin/java -Xms128m -Xmx512m -jar /opt/undercooked/undercooked-server.jar ${var.server_port} --log-concurrency
    Restart=on-failure
    RestartSec=3
    StandardOutput=journal
    StandardError=journal

    [Install]
    WantedBy=multi-user.target
    SERVICE

    systemctl daemon-reload
    systemctl enable undercooked.service
    if [ -f /opt/undercooked/undercooked-server.jar ]; then
      systemctl restart undercooked.service
    fi
  EOT
}
