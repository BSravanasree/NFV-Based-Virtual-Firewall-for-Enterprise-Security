#!/usr/bin/env bash
# =============================================================================
# NEXUS NFV – one-shot production deployment on Ubuntu 22.04 / 24.04 (full stack incl. firewall lab)
#
#   sudo ./deploy/deploy-ubuntu.sh                    # HTTPS on <public-ip>.sslip.io (no domain needed)
#   sudo PUBLIC_HOST=nfv.example.com ./deploy/deploy-ubuntu.sh          # own domain (A record -> server IP)
#
# Idempotent: re-running updates the deployment and keeps the database and existing secrets.
# Requirements: 2+ vCPU, 4+ GB RAM, 20 GB disk, ports 80/443 reachable (cloud security group!).
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run as root (sudo)"
. /etc/os-release
[[ "${ID:-}" == "ubuntu" ]] || echo "WARNING: tested on Ubuntu; detected ${PRETTY_NAME:-unknown}"

# ---------------------------------------------------------------- 1. packages
log "Installing Docker Engine and tools"
if ! command -v docker >/dev/null || ! docker compose version >/dev/null 2>&1; then
  apt-get update -y
  apt-get install -y ca-certificates curl gnupg python3 ufw openssl
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
apt-get install -y python3 ufw openssl >/dev/null
systemctl enable --now docker

# ---------------------------------------------------------------- 2. host firewall
log "Configuring host firewall (SSH, HTTP, HTTPS only)"
ufw allow OpenSSH >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw --force enable >/dev/null
ufw status | sed 's/^/   /'
# Note: Docker publishes only Caddy (80/443) in the production override; MySQL, API, NETCONF and the
# Docker socket proxy are on internal networks and never bound to a public interface.

# ---------------------------------------------------------------- 3. configuration & secrets
log "Preparing .env (secrets are generated on this server and never leave it)"
if [[ -z "${PUBLIC_HOST:-}" ]]; then
  IP=$(curl -fsS4 https://api.ipify.org || curl -fsS4 https://ifconfig.me)
  [[ -n "$IP" ]] || die "could not detect the public IPv4 address; set PUBLIC_HOST"
  PUBLIC_HOST="${IP}.sslip.io"
fi
[[ -f .env ]] || cp .env.example .env
setv() { # set KEY=VALUE in .env (only if empty unless forced)
  local k=$1 v=$2 force=${3:-}
  if grep -qE "^${k}=" .env; then
    local cur; cur=$(grep -E "^${k}=" .env | head -1 | cut -d= -f2-)
    if [[ -z "$cur" || "$cur" == change-me* || -n "$force" ]]; then
      python3 - "$k" "$v" <<'PY'
import re, sys
k, v = sys.argv[1], sys.argv[2]
s = open('.env', encoding='utf-8').read()
s = re.sub(rf'^{re.escape(k)}=.*$', lambda m: f'{k}={v}', s, flags=re.M)
open('.env', 'w', encoding='utf-8').write(s)
PY
    fi
  else
    echo "${k}=${v}" >> .env
  fi
}
rand() { openssl rand -base64 "$1" | tr -d '\n/+=' | cut -c1-"$2"; }
setv DB_PASSWORD "$(rand 32 32)"
setv MYSQL_ROOT_PASSWORD "$(rand 32 32)"
setv NEXUS_JWT_SECRET "$(rand 64 64)"
setv NEXUS_CREDENTIAL_KEY "$(openssl rand -base64 32)"
setv NEXUS_AGENT_TOKEN "$(rand 48 40)"
setv NEXUS_ADMIN_PASSWORD "Nx#$(rand 24 18)7a"
setv NEXUS_DEMO_USERS_PASSWORD "Nx#$(rand 24 18)7a"
setv PUBLIC_HOST "$PUBLIC_HOST" force
setv NEXUS_CORS_ORIGINS "https://${PUBLIC_HOST}" force
chmod 600 .env
echo "   public host: https://${PUBLIC_HOST}"

COMPOSE=(docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml --profile lab)

# ---------------------------------------------------------------- 4. build & start
log "Building the firewall VNF image (first run compiles Netopeer2: ~5-10 min)"
docker compose --profile build build fw-vnf-image
log "Building and starting the application stack"
"${COMPOSE[@]}" up -d --build

log "Waiting for the backend to become healthy"
for _ in $(seq 1 60); do
  s=$(docker inspect -f '{{.State.Health.Status}}' "$("${COMPOSE[@]}" ps -q backend)" 2>/dev/null || echo starting)
  [[ "$s" == healthy ]] && break
  sleep 5
done
[[ "$s" == healthy ]] || { "${COMPOSE[@]}" logs --tail 80 backend; die "backend did not become healthy"; }

log "Waiting for the HTTPS certificate (Let's Encrypt via Caddy)"
for _ in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "https://${PUBLIC_HOST}/healthz" || true)
  [[ "$code" == 200 ]] && break
  sleep 5
done
[[ "$code" == 200 ]] || { "${COMPOSE[@]}" logs --tail 50 caddy; die "https://${PUBLIC_HOST} not reachable – are ports 80/443 open in the cloud firewall?"; }

# ---------------------------------------------------------------- 5. verification through the public URL
log "Smoke test via https://${PUBLIC_HOST}"
for p in / /docs /login /overview /api/v1/auth/me /actuator/health; do
  printf '   %-22s %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' "https://${PUBLIC_HOST}${p}")"
done
curl -sI "https://${PUBLIC_HOST}/" | grep -iE '^(strict-transport|content-security|x-frame)' | sed 's/^/   /'

log "Running the end-to-end suite against the public HTTPS API (real firewall traffic)"
set +e
NEXUS_API="https://${PUBLIC_HOST}/api/v1" python3 lab/e2e_test.py
E2E=$?
set -e

cat <<EOF

=====================================================================
 NEXUS NFV is deployed
   Website / console : https://${PUBLIC_HOST}
   API health        : https://${PUBLIC_HOST}/actuator/health
   API docs          : https://${PUBLIC_HOST}/swagger-ui.html
   Admin login       : admin / (NEXUS_ADMIN_PASSWORD in ${ROOT}/.env)
   Demo accounts     : operator, analyst / (NEXUS_DEMO_USERS_PASSWORD in .env)
   End-to-end checks : $( [[ $E2E -eq 0 ]] && echo "ALL PASSED" || echo "${E2E} FAILED – see output above" )
 Keep .env private. Re-run this script to update the deployment.
=====================================================================
EOF
exit $E2E
