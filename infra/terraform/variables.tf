variable "project_id" {
  description = "GCP Project ID for Undercooked"
  type        = string
  default     = "project-72f15835-9e20-4691-877"
}

variable "region" {
  description = "GCP region (Frankfurt - low latency to Istanbul)"
  type        = string
  default     = "europe-west3"
}

variable "zone" {
  description = "GCP zone for the single game server VM"
  type        = string
  default     = "europe-west3-a"
}

variable "machine_type" {
  description = "Compute Engine machine type (2 vCPU for genuine multi-core JVM concurrency)"
  type        = string
  default     = "e2-small"
}

variable "server_port" {
  description = "Raw TCP port for Undercooked Java ServerSocket"
  type        = number
  default     = 6789
}
