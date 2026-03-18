#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost}"
USERNAME="${USERNAME:-admin}"
PASSWORD="${PASSWORD:-123456}"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

curl_code() {
  local url="$1"
  shift || true
  curl -sS -o /tmp/gateway_check_body.$$ -w '%{http_code}' "$url" "$@"
}

login_once() {
  local code
  code=$(curl_code "${BASE_URL}/api/platform/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}")
  cat /tmp/gateway_check_body.$$ > /tmp/gateway_login_body.$$
  echo "$code"
}

assert_not_502() {
  local name="$1"
  local code="$2"
  if [[ "$code" == "502" ]]; then
    log "[FAIL] ${name} returned 502"
    sed -n '1,80p' /tmp/gateway_check_body.$$ || true
    exit 1
  fi
}

extract_token() {
  sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' /tmp/gateway_login_body.$$ | head -n1
}

run_login_loop() {
  local rounds="$1"
  local phase="$2"
  local i code
  log "${phase}: running ${rounds} login checks"
  for ((i=1; i<=rounds; i++)); do
    code=$(login_once)
    if [[ "$code" != "200" ]]; then
      log "[FAIL] ${phase} login #${i} returned ${code}"
      sed -n '1,120p' /tmp/gateway_login_body.$$ || true
      exit 1
    fi
    printf '.'
  done
  printf '\n'
  log "${phase}: all ${rounds} login checks passed"
}

wait_admin_healthy() {
  local max_try=60
  local i code
  log "waiting for admin-service health"
  for ((i=1; i<=max_try; i++)); do
    code=$(curl -sS -o /tmp/gateway_health_body.$$ -w '%{http_code}' http://localhost:8081/actuator/health || true)
    if [[ "$code" == "200" ]]; then
      log "admin-service is healthy"
      return
    fi
    sleep 2
  done
  log "[FAIL] admin-service health check timeout"
  sed -n '1,80p' /tmp/gateway_health_body.$$ || true
  exit 1
}

smoke_check() {
  local token="$1"
  local code

  log "running smoke checks (expect not 502)"

  code=$(curl_code "${BASE_URL}/api/platform/auth/me" -H "Authorization: Bearer ${token}")
  assert_not_502 "GET /api/platform/auth/me" "$code"
  log "GET /api/platform/auth/me => ${code}"

  code=$(curl_code "${BASE_URL}/api/platform/auth/permissions" -H "Authorization: Bearer ${token}")
  assert_not_502 "GET /api/platform/auth/permissions" "$code"
  log "GET /api/platform/auth/permissions => ${code}"

  code=$(curl_code "${BASE_URL}/api/tenant/auth/me" -H "Authorization: Bearer ${token}")
  assert_not_502 "GET /api/tenant/auth/me" "$code"
  log "GET /api/tenant/auth/me => ${code}"

  code=$(curl_code "${BASE_URL}/api/user/auth/me" -H "Authorization: Bearer ${token}")
  assert_not_502 "GET /api/user/auth/me" "$code"
  log "GET /api/user/auth/me => ${code}"
}

main() {
  if [[ "${SKIP_INITIAL_BUILD:-false}" != "true" ]]; then
    log "rebuilding services: nginx-gateway admin-service user-service log-service"
    docker compose up -d --build nginx-gateway admin-service user-service log-service
  else
    log "skip initial build (SKIP_INITIAL_BUILD=true)"
  fi

  run_login_loop 20 "phase-1"

  log "rebuilding admin-service only (nginx should stay up)"
  docker compose up -d --build admin-service
  wait_admin_healthy

  run_login_loop 20 "phase-2"

  local token
  token=$(extract_token)
  if [[ -z "$token" ]]; then
    log "[FAIL] failed to parse token from login response"
    sed -n '1,120p' /tmp/gateway_login_body.$$ || true
    exit 1
  fi

  smoke_check "$token"

  log "SUCCESS: gateway stability checks completed"
}

main "$@"
