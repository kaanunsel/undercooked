output "server_ip" {
  description = "Static external IP of the Undercooked TCP server"
  value       = google_compute_address.undercooked_ip.address
}

output "server_port" {
  description = "TCP port of the Undercooked Java ServerSocket"
  value       = var.server_port
}

output "ssh_command" {
  description = "Command to SSH into the Undercooked VM"
  value       = "gcloud compute ssh undercooked-server --zone=${var.zone} --project=${var.project_id}"
}

output "logs_command" {
  description = "Command to stream live server & concurrency logs"
  value       = "gcloud compute ssh undercooked-server --zone=${var.zone} --project=${var.project_id} --command='sudo journalctl -u undercooked.service -f'"
}
