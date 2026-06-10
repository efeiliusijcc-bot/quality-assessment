#!/usr/bin/env bash
set -euo pipefail

# Current authenticated API smoke entrypoint.
#
# Optional environment variables:
#   API_BASE      Backend base URL, default http://127.0.0.1:8080
#   API_USER      Login username, default admin
#   API_PASSWORD  Login password, default 123456
#   API_TIMEOUT   Per-request timeout in seconds, default 8

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE="${API_BASE:-http://127.0.0.1:8080}"

echo "API smoke target: ${API_BASE}"
python "${ROOT_DIR}/scripts/api_smoke.py"
