# Deployment Guide

The **application** (UI, API, database) and the **network infrastructure** (VNFs, lab endpoints, ONOS,
Mininet, OSM) are deployed separately. The application only needs network reachability and credentials.

## Requirements

| Deployment | Minimum | Notes |
|---|---|---|
| Application + Docker NFV lab | 4 vCPU, 6 GB RAM, 15 GB disk, Docker 24+ with Compose v2 | Docker Desktop (Windows/macOS) or Ubuntu 22.04/24.04 |
| + ONOS (`sdn` profile) | +2 GB RAM | `onosproject/onos:2.7-latest` |
| Mininet / OVS SDN lab | Ubuntu host or VM with root, openvswitch kernel module | not possible inside Docker Desktop |
| OSM | separate VM: 4 vCPU, 8 GB RAM, 40 GB disk + a VIM | see OSM documentation |

First build of `nexus/fw-vnf` compiles libyang/libnetconf2/sysrepo/netopeer2: ~5–10 minutes.

## 1. Configure

```bash
cp .env.example .env
```

Generate secrets (Linux/macOS/Git Bash):

```bash
openssl rand -base64 48   # NEXUS_JWT_SECRET, NEXUS_AGENT_TOKEN
openssl rand -base64 32   # NEXUS_CREDENTIAL_KEY (must decode to exactly 32 bytes)
```

Set `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `NEXUS_ADMIN_PASSWORD` (≥ 10 chars, upper/lower/digit/symbol) and
optionally `NEXUS_DEMO_USERS_PASSWORD` (creates `operator` and `analyst`).

## 2. Build and start (application + NFV lab)

```bash
docker compose --profile build build fw-vnf-image     # firewall VNF image
docker compose --profile lab up -d --build            # mysql, docker-proxy, backend, frontend, client1/2, server
docker compose ps                                     # backend becomes "healthy" after Flyway migrations
```

| URL | |
|---|---|
| <http://localhost:3000> | management console |
| <http://localhost:8080/swagger-ui.html> | OpenAPI UI (bound to 127.0.0.1) |
| <http://localhost:8080/actuator/health> | health |

Without `--profile lab` only the application runs; VNF deployment then fails with a clear message that the lab
networks do not exist.

## 3. Optional: ONOS next to the application

```bash
# .env: NEXUS_ONOS_MODE=real, ONOS_URL=http://onos:8181/onos/v1, ONOS_PASSWORD=rocks
docker compose --profile lab --profile sdn up -d
```

Switches appear once an OpenFlow network (Mininet/OVS on a Linux host) points to `tcp:<docker-host>:6653`.

## 4. Ubuntu host deployment (production-like)

```bash
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git
git clone <repo> nexus-nfv && cd nexus-nfv
cp .env.example .env && nano .env
sudo docker compose --profile build build fw-vnf-image
sudo docker compose up -d --build                     # add --profile lab for the demo lab
```

Put a TLS-terminating reverse proxy (nginx, Traefik, cloud LB) in front of port 3000 and keep 8080 private.
For the Mininet/ONOS lab on the same host: `sudo lab/sdn/setup-ubuntu-sdn-lab.sh`.

## Free public demo – Cloudflare Quick Tunnel (Windows)

No account, domain, VPS or payment. The tunnel publishes the local nginx reverse proxy (`http://localhost:3000`),
which serves the website, the console and `/api` on one hostname.

```
https://<random>.trycloudflare.com → Cloudflare edge → cloudflared (this PC) → nginx :3000 → UI · /api → Spring Boot → MySQL / VNFs
```

| Action | Command |
|---|---|
| Start (and print the verified URL) | double-click `start-nexus-public.bat` |
| Stop the tunnel only | `stop-nexus-public.bat` |
| Status / current URL | `powershell -File scripts
exus-public.ps1 status` |
| Stop the application too (data kept) | `docker compose --profile lab stop` |

`scripts/nexus-public.ps1` starts Docker Desktop if needed, runs `docker compose --profile lab up -d`, waits until
MySQL, the backend and nginx are healthy, verifies the origin, downloads the official `cloudflared` from
`github.com/cloudflare/cloudflared` **only if it is missing** and rejects it unless its Authenticode signature is
valid and issued to Cloudflare, starts `cloudflared tunnel --url http://localhost:3000`, reads the generated
`*.trycloudflare.com` URL from cloudflared's log, checks it end to end and only then prints it. The PID is stored
in `%LOCALAPPDATA%
exus-nfv	unnel.pid` so the stop script terminates exactly that process.

What makes one hostname work behind the tunnel: nginx forwards the edge's scheme/host/port (`X-Forwarded-*`), so
the backend recognises the browser's `https://…trycloudflare.com` origin as same-origin, and visitors arriving
over plain HTTP receive a 308 redirect to HTTPS. Other origins are still rejected by CORS.

Before sharing a link: run `python lab/security_audit.py` (and with `NEXUS_BASE=<public URL>`); share a
least-privilege account (e.g. a `SECURITY_ANALYST`), never the admin password. Quick Tunnel limits: the URL
changes on every restart, the PC must stay on, and Cloudflare documents quick tunnels as intended for testing
and demonstrations.

## Public HTTPS deployment (Ubuntu VPS – full functionality)

The firewall VNFs need privileged Linux networking (`NET_ADMIN`, nftables, Docker), so the public deployment
runs on a VPS, not on serverless/PaaS hosts (Vercel, Railway, Netlify cannot run the firewall lab).

```
Internet ──443──▶ Caddy (Let's Encrypt) ──▶ nginx (UI + /api proxy) ──▶ Spring Boot ──▶ MySQL
                                                                         ├─▶ docker-socket-proxy ─▶ Docker
                                                                         └─▶ firewall VNFs (mgmt network)
```

1. Create an Ubuntu 22.04/24.04 VPS (≥ 2 vCPU, 4 GB RAM, 20 GB disk). Open **TCP 22, 80, 443** in the
   provider's firewall / security group.
2. Copy the project to the server (git clone, or `scp` an archive – never copy your local `.env`).
3. Run:

   ```bash
   sudo ./deploy/deploy-ubuntu.sh                              # https://<server-ip>.sslip.io
   sudo PUBLIC_HOST=nfv.example.com ./deploy/deploy-ubuntu.sh  # with your own domain (A record → server IP)
   ```

The script installs Docker, enables `ufw` (22/80/443 only), generates every secret on the server, builds the
VNF and application images, starts `docker-compose.yml` + `deploy/docker-compose.prod.yml`, waits for the
certificate, smoke-tests the public URL and finally runs the **53-check end-to-end suite through the public
HTTPS API**. It prints the URL only after those checks. Re-running it updates the deployment.

What the production override changes: only Caddy publishes ports (80/443, HTTP→HTTPS redirect, HSTS); the
UI/API ports are unpublished; CORS is restricted to `https://PUBLIC_HOST`; container logs rotate
(20 MB × 5). Public health check: `https://PUBLIC_HOST/actuator/health` (status only).

Rehearsed on this development machine with `PUBLIC_HOST=localhost` (Caddy internal CA): all routes, security
headers and the redirect verified, and the E2E suite passed 53/53 through `https://localhost/api/v1`.

**Updating:** pull the new code on the server and re-run `sudo ./deploy/deploy-ubuntu.sh`.
**Logs:** `docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f backend caddy`.
**Backup:** see below; also back up `.env` (it holds the credential-encryption key).

### Split hosting (optional)

The UI can be served by a static host and pointed at the VPS API: build with `VITE_API_BASE_URL=https://<api-host>`
and add the UI origin to `NEXUS_CORS_ORIGINS`. Alternatively run the `frontend` image anywhere with
`BACKEND_UPSTREAM=https://<api-host>` (nginx proxies `/api`, so the browser stays same-origin).

## Local development

```bash
docker compose up -d mysql                            # or a local MySQL 8 with database nexus_nfv
cd backend
export DB_URL='jdbc:mysql://localhost:3306/nexus_nfv?allowPublicKeyRetrieval=true&useSSL=false' DB_PASSWORD=… \
       NEXUS_ADMIN_PASSWORD='…' NEXUS_AGENT_TOKEN=… NEXUS_CREDENTIAL_KEY=… NEXUS_ONOS_MODE=demo
mvn spring-boot:run
cd ../frontend && npm ci && npm run dev               # http://localhost:5173 (proxies /api to :8080)
```

Note: the MySQL service is on an internal network in compose; for local development publish it temporarily
(`ports: ["127.0.0.1:3306:3306"]`). A backend running on the host cannot reach VNF agents on the internal
`nexus-mgmt` network – run the backend in compose to operate the lab.

## Configuration reference

All settings are environment variables (see `.env.example` and `backend/src/main/resources/application.yml`).

| Variable | Default | Purpose |
|---|---|---|
| `NEXUS_ORCHESTRATOR_MODE` | `docker` | `docker` / `osm` / `none` |
| `NEXUS_VNF_IMAGE` | `nexus/fw-vnf:1.0.0` | lab VNF image |
| `NEXUS_VNF_NETCONF_ENABLED` | `true` | start Netopeer2 in lab VNFs and auto-register them |
| `NEXUS_ONOS_MODE` | `real` (app) / `demo` (.env.example) | ONOS adapter |
| `NEXUS_POLL_INTERVAL_MS` | `10000` | telemetry/monitoring interval |
| `NEXUS_JWT_TTL_MINUTES` | `60` | token lifetime |
| `NEXUS_CORS_ORIGINS` | `http://localhost:3000,http://localhost:5173` | allowed browser origins |

## Health checks & logging

* MySQL: `mysqladmin ping`; backend: `/actuator/health`; frontend: nginx root; VNF: `nft list table inet nexus_fw`.
* Logs: `docker compose logs -f backend`; VNF: `docker logs nexus-vnf-<name>`, ulogd at
  `/var/log/ulog/ulogd.log`, Netopeer2 at `/var/log/netopeer2.log` inside the VNF.

## Backup

`docker compose exec mysql mysqldump -u root -p nexus_nfv > backup.sql` – the database holds all desired state;
VNFs can be re-deployed and re-synchronised from it.
