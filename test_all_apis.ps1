$ErrorActionPreference = "Stop"

# Current authenticated API smoke entrypoint for Windows PowerShell.
#
# Optional environment variables:
#   API_BASE      Backend base URL, default http://127.0.0.1:8080
#   API_USER      Login username, default admin
#   API_PASSWORD  Login password, default 123456
#   API_TIMEOUT   Per-request timeout in seconds, default 8

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $env:API_BASE) {
    $env:API_BASE = "http://127.0.0.1:8080"
}

Write-Host "API smoke target: $env:API_BASE"
python (Join-Path $root "scripts/api_smoke.py")
