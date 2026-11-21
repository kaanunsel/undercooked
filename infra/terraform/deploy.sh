#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="project-72f15835-9e20-4691-877"
ZONE="europe-west3-a"
VM_NAME="undercooked-server"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="${SCRIPT_DIR}/../../server"

echo "1) Building & testing plain-Java Undercooked server JAR..."
(cd "${SERVER_DIR}" && make test && make jar)

echo "2) Uploading undercooked-server.jar to ${VM_NAME} (${PROJECT_ID})..."
gcloud compute scp \
  "${SERVER_DIR}/dist/undercooked-server.jar" \
  "${VM_NAME}:/opt/undercooked/undercooked-server.jar" \
  --zone="${ZONE}" \
  --project="${PROJECT_ID}"

echo "3) Restarting undercooked.service..."
gcloud compute ssh "${VM_NAME}" \
  --zone="${ZONE}" \
  --project="${PROJECT_ID}" \
  --command="sudo systemctl restart undercooked.service && sudo systemctl status undercooked.service --no-pager"

echo "Deploy complete!"
