#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"${ROOT_DIR}/scripts/setup-traefik-certs.sh"

docker compose -f "${ROOT_DIR}/docker-compose.proxy.yml" up -d --build

echo "Traefik proxy is running. Add to /etc/hosts:"
echo "127.0.0.1 cp.localhost wl-importer.localhost wl-exporter.localhost"
